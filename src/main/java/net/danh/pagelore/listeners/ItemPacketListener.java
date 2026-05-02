package net.danh.pagelore.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Highly optimized packet listener.
 * Employs Guava caching to completely eliminate packet storm lag during UI spamming.
 */
public class ItemPacketListener extends PacketListenerAbstract implements PacketListener {

    private static final Pattern CHECK_PATTERN = Pattern.compile("\\{check:(.+?)(>=|<=|>|<|==|!=)(.+?)\\}");

    // Memoization cache: Stores processed items for 1 second to prevent PAPI/NBT lag spikes during 'F' key spam.
    private final Cache<Integer, Optional<com.github.retrooper.packetevents.protocol.item.ItemStack>> itemCache =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(1, TimeUnit.SECONDS)
                    .maximumSize(5000)
                    .build();

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
     * Processes an item with maximum efficiency utilizing memory caching.
     */
    private com.github.retrooper.packetevents.protocol.item.ItemStack processItem(Player player, com.github.retrooper.packetevents.protocol.item.ItemStack peItem) {
        if (peItem == null || peItem.getAmount() <= 0 || peItem.getType() == ItemTypes.AIR) return null;

        // Generate a unique fingerprint for this specific item state and player
        int nbtHash = peItem.getNBT() != null ? peItem.getNBT().hashCode() : 0;
        int cacheKey = Objects.hash(player.getUniqueId(), peItem.getType(), peItem.getAmount(), nbtHash);

        // Instantly return the cached result if the player just spammed a packet (0.0001ms execution)
        Optional<com.github.retrooper.packetevents.protocol.item.ItemStack> cachedResult = itemCache.getIfPresent(cacheKey);
        if (cachedResult != null) {
            return cachedResult.orElse(null); // Returns null if the item required no changes, or the modified item
        }

        if (peItem.getNBT() == null) {
            itemCache.put(cacheKey, Optional.empty());
            return null;
        }

        PageLore plugin = PageLore.getInstance();
        String rawNbt = peItem.getNBT().toString();

        if (!rawNbt.contains(plugin.separator) && !rawNbt.contains("{papi:") && !rawNbt.contains("{check:")) {
            itemCache.put(cacheKey, Optional.empty());
            return null;
        }

        // Heavy conversions only run ONCE per second per unique item state
        ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem);
        if (bukkitItem == null || !bukkitItem.hasItemMeta() || !bukkitItem.getItemMeta().hasLore()) {
            itemCache.put(cacheKey, Optional.empty());
            return null;
        }

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
            String processedLine = line.contains("{papi:") ? line.replaceAll("\\{papi:([^{}]+)\\}", "%$1%") : line;
            if (plugin.hasPapi) {
                try {
                    processedLine = PlaceholderAPI.setPlaceholders(player, processedLine);
                } catch (Exception ignored) {
                }
            }

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
        var result = SpigotConversionUtil.fromBukkitItemStack(bukkitItem);

        // Cache the fully processed result
        itemCache.put(cacheKey, Optional.of(result));
        return result;
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