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
 * Intercepts outbound item packets to inject dynamically evaluated lore and pagination states.
 */
public class ItemPacketListener extends PacketListenerAbstract implements PacketListener {

    private static final Pattern CHECK_PATTERN = Pattern.compile("\\{check:(.+?)(>=|<=|>|<|==|!=)(.+?)\\}");

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Player player = (Player) event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot setSlot = new WrapperPlayServerSetSlot(event);
            var peItem = setSlot.getItem();
            var modified = processItem(player, peItem);
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
     * Processes individual items, checking for placeholders, requirements, and page separators.
     *
     * @param player The target player receiving the packet.
     * @param peItem The PacketEvents ItemStack object.
     * @return The modified PacketEvents ItemStack, or null if no modifications were needed.
     */
    private com.github.retrooper.packetevents.protocol.item.ItemStack processItem(Player player, com.github.retrooper.packetevents.protocol.item.ItemStack peItem) {
        if (peItem == null || peItem.getAmount() <= 0 || peItem.getType() == ItemTypes.AIR) return null;

        ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem);
        if (bukkitItem == null || !bukkitItem.hasItemMeta() || !bukkitItem.getItemMeta().hasLore()) return null;

        ItemMeta meta = bukkitItem.getItemMeta();
        PageLore plugin = PageLore.getInstance();

        List<String> rawLore = new ArrayList<>();
        boolean requiresProcessing = false;

        if (ServerVersion.isPaper() && ServerVersion.isAtLeast(1, 16, 5)) {
            List<Component> components = meta.lore();
            if (components != null) {
                for (Component c : components) {
                    String serialized = MiniMessage.miniMessage().serialize(c);
                    rawLore.add(serialized);
                    if (serialized.contains(plugin.separator) || serialized.contains("{papi:") || serialized.contains("{check:")) {
                        requiresProcessing = true;
                    }
                }
            }
        } else {
            if (meta.getLore() != null) {
                rawLore.addAll(meta.getLore());
                for (String line : rawLore) {
                    if (line.contains(plugin.separator) || line.contains("{papi:") || line.contains("{check:")) {
                        requiresProcessing = true;
                        break;
                    }
                }
            }
        }

        if (!requiresProcessing) return null;

        bukkitItem = bukkitItem.clone();

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

        List<String> processed = new ArrayList<>();
        for (String line : pageLore) {
            String l = line.contains("{papi:") ? line.replaceAll("\\{papi:([^{}]+)\\}", "%$1%") : line;
            if (plugin.hasPapi) l = PlaceholderAPI.setPlaceholders(player, l);

            Matcher matcher = CHECK_PATTERN.matcher(l);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                boolean met = isConditionMet(stripColors(matcher.group(1)), stripColors(matcher.group(3)), matcher.group(2));
                matcher.appendReplacement(sb, met ? plugin.metSymbol : plugin.unmetSymbol);
            }
            matcher.appendTail(sb);
            processed.add(sb.toString());
        }

        List<Component> finalLore = new ArrayList<>();
        for (String str : processed) {
            finalLore.add(ColorUtils.parse(str).decoration(TextDecoration.ITALIC, false).colorIfAbsent(NamedTextColor.WHITE));
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

    /**
     * Strips all formatting and color tags from a given string.
     */
    private String stripColors(String input) {
        if (input == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacySection().deserialize(MiniMessage.miniMessage().stripTags(input))).trim();
    }

    /**
     * Evaluates a mathematical or string condition.
     */
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