package net.danh.pagelore.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.danh.pagelore.PageLore;
import net.danh.pagelore.utils.ColorUtils;
import net.danh.pagelore.utils.ServerVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Highly optimized packet listener.
 * Uses NBT Fast-Failing to achieve zero-lag performance on high-population servers.
 */
public class ItemPacketListener extends PacketListenerAbstract implements PacketListener {

    private static final Pattern CHECK_PATTERN = Pattern.compile("\\{check:(.+?)(>=|<=|>|<|==|!=)(.+?)\\}");

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Player player = (Player) event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot setSlot = new WrapperPlayServerSetSlot(event);
            var modified = processItem(player, setSlot.getItem());
            if (modified != null) setSlot.setItem(modified);

        } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems windowItems = new WrapperPlayServerWindowItems(event);
            var items = windowItems.getItems();
            boolean changed = false;

            for (int i = 0; i < items.size(); i++) {
                var modified = processItem(player, items.get(i));
                if (modified != null) {
                    items.set(i, modified);
                    changed = true;
                }
            }
            if (changed) windowItems.setItems(items);
        }
    }

    /**
     * Processes an item with maximum efficiency.
     */
    private com.github.retrooper.packetevents.protocol.item.ItemStack processItem(Player player, com.github.retrooper.packetevents.protocol.item.ItemStack peItem) {
        // 1. Basic empty checks
        if (peItem == null || peItem.getAmount() <= 0 || peItem.getType() == ItemTypes.AIR) return null;

        // 2. ULTRA OPTIMIZATION: NBT Fast-Fail.
        // We convert the raw NBT to a string. If it doesn't contain our triggers, we abort immediately.
        // This avoids converting thousands of normal items (like dirt or stone) into Bukkit ItemStacks.
        if (peItem.getNBT() == null) return null;
        PageLore plugin = PageLore.getInstance();
        String rawNbt = peItem.getNBT().toString();

        if (!rawNbt.contains(plugin.separator) && !rawNbt.contains("{papi:") && !rawNbt.contains("{check:")) {
            return null;
        }

        // 3. Only convert to Bukkit ItemStack if we are 100% sure it needs modification
        ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem);
        if (bukkitItem == null || !bukkitItem.hasItemMeta() || !bukkitItem.getItemMeta().hasLore()) return null;

        ItemMeta meta = bukkitItem.getItemMeta();
        List<String> rawLore = new ArrayList<>();

        if (ServerVersion.isPaper() && ServerVersion.isAtLeast(1, 16, 5)) {
            List<Component> components = meta.lore();
            if (components != null) components.forEach(c -> rawLore.add(MiniMessage.miniMessage().serialize(c)));
        } else {
            if (meta.getLore() != null) rawLore.addAll(meta.getLore());
        }

        boolean hasPage = rawLore.stream().anyMatch(s -> s.contains(plugin.separator));
        NamespacedKey key = new NamespacedKey(plugin, "current_page");
        int currentPage = meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);

        List<String> pageLore = new ArrayList<>();
        int pageIndex = 0;

        for (String line : rawLore) {
            if (line.contains(plugin.separator)) {
                pageIndex++;
                continue;
            }
            if (!hasPage || pageIndex == currentPage) pageLore.add(line);
        }

        List<Component> finalLore = new ArrayList<>(pageLore.size());

        for (String line : pageLore) {
            // Process PAPI
            String processedLine = line.contains("{papi:") ? line.replaceAll("\\{papi:([^{}]+)\\}", "%$1%") : line;
            if (plugin.hasPapi) {
                try {
                    processedLine = PlaceholderAPI.setPlaceholders(player, processedLine);
                } catch (Exception ignored) {
                    // Fail-safe to prevent rare PAPI async crashes
                }
            }

            // Process Requirements
            Matcher matcher = CHECK_PATTERN.matcher(processedLine);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                boolean met = isConditionMet(stripColors(matcher.group(1)), stripColors(matcher.group(3)), matcher.group(2));
                matcher.appendReplacement(sb, met ? plugin.metSymbol : plugin.unmetSymbol);
            }
            matcher.appendTail(sb);

            finalLore.add(ColorUtils.parse(sb.toString()).decoration(TextDecoration.ITALIC, false).colorIfAbsent(NamedTextColor.WHITE));
        }

        if (ServerVersion.isPaper() && ServerVersion.isAtLeast(1, 16, 5)) {
            meta.lore(finalLore);
        } else {
            List<String> legacy = finalLore.stream().map(c -> LegacyComponentSerializer.legacySection().serialize(c)).toList();
            meta.setLore(legacy);
        }

        bukkitItem.setItemMeta(meta);
        return SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
    }

    private String stripColors(String input) {
        if (input == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacySection().deserialize(MiniMessage.miniMessage().stripTags(input))).trim();
    }

    private boolean isConditionMet(String v1, String v2, String op) {
        try {
            double d1 = Double.parseDouble(v1), d2 = Double.parseDouble(v2);
            return switch (op) {
                case ">=" -> d1 >= d2;
                case "<=" -> d1 <= d2;
                case ">" -> d1 > d2;
                case "<" -> d1 < d2;
                case "==" -> d1 == d2;
                case "!=" -> d1 != d2;
                default -> false;
            };
        } catch (Exception e) {
            return op.equals("==") ? v1.equalsIgnoreCase(v2) : op.equals("!=") && !v1.equalsIgnoreCase(v2);
        }
    }
}