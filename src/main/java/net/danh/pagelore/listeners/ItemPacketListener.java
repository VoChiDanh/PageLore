package net.danh.pagelore.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
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
 * Highly optimized packet listener utilizing Bukkit Meta Fast-Failing.
 * Implements a lightweight hash generation algorithm to completely eliminate GUI spam lag.
 */
public class ItemPacketListener extends PacketListenerAbstract implements PacketListener {

    private final Cache<Integer, Optional<ItemStack>> itemCache =
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
            var peItem = setSlot.getItem();

            if (peItem == null) return;
            ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem);

            if (bukkitItem == null || !bukkitItem.hasItemMeta() || !bukkitItem.getItemMeta().hasLore()) return;

            int cacheKey = generateFastHash(player, bukkitItem);
            Optional<ItemStack> cachedOpt = itemCache.getIfPresent(cacheKey);

            if (cachedOpt != null) {
                if (cachedOpt.isPresent()) {
                    setSlot.setItem(SpigotConversionUtil.fromBukkitItemStack(cachedOpt.get()));
                }
                return;
            }

            ItemStack modified = processBukkitItem(player, bukkitItem);
            if (modified != null) {
                itemCache.put(cacheKey, Optional.of(modified));
                setSlot.setItem(SpigotConversionUtil.fromBukkitItemStack(modified));
            } else {
                itemCache.put(cacheKey, Optional.empty());
            }

        } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems windowItems = new WrapperPlayServerWindowItems(event);
            var items = windowItems.getItems();
            boolean changed = false;

            for (int i = 0; i < items.size(); i++) {
                var peItem = items.get(i);
                if (peItem == null) continue;

                ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem);

                if (bukkitItem == null || !bukkitItem.hasItemMeta() || !bukkitItem.getItemMeta().hasLore()) continue;

                int cacheKey = generateFastHash(player, bukkitItem);
                Optional<ItemStack> cachedOpt = itemCache.getIfPresent(cacheKey);

                if (cachedOpt != null) {
                    if (cachedOpt.isPresent()) {
                        items.set(i, SpigotConversionUtil.fromBukkitItemStack(cachedOpt.get()));
                        changed = true;
                    }
                    continue;
                }

                ItemStack modified = processBukkitItem(player, bukkitItem);
                if (modified != null) {
                    itemCache.put(cacheKey, Optional.of(modified));
                    items.set(i, SpigotConversionUtil.fromBukkitItemStack(modified));
                    changed = true;
                } else {
                    itemCache.put(cacheKey, Optional.empty());
                }
            }
            if (changed) windowItems.setItems(items);
        }
    }

    /**
     * Generates a lightweight hash key for the cache based strictly on essential values.
     * Bypasses the default Bukkit ItemStack.hashCode() which serializes extreme NBT data
     * and causes severe lag spikes during GUI spam clicks.
     *
     * @param player The player interacting with the GUI.
     * @param item   The item being evaluated.
     * @return Fast integer hash code.
     */
    private int generateFastHash(Player player, ItemStack item) {
        int hash = Objects.hash(player.getUniqueId(), item.getType(), item.getAmount());
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            hash = 31 * hash + item.getItemMeta().getLore().hashCode();
        }
        return hash;
    }

    /**
     * Safely processes the Bukkit item, evaluating placeholders and pagination logic based on config tags.
     */
    private ItemStack processBukkitItem(Player player, ItemStack bukkitItem) {
        PageLore plugin = PageLore.getInstance();
        ItemMeta meta = bukkitItem.getItemMeta();
        List<String> rawLore = new ArrayList<>();
        boolean needsProcessing = false;

        if (ServerVersion.isPaper() && ServerVersion.isAtLeast(1, 16, 5)) {
            List<Component> components = meta.lore();
            if (components != null) {
                for (Component c : components) {
                    String plainStr = c.toString();
                    if (plainStr.contains(plugin.separator) || plainStr.contains(plugin.papiTag) || plainStr.contains(plugin.checkTag)) {
                        needsProcessing = true;
                    }
                    rawLore.add(MiniMessage.miniMessage().serialize(c));
                }
            }
        } else {
            if (meta.getLore() != null) {
                rawLore.addAll(meta.getLore());
                for (String line : rawLore) {
                    if (line.contains(plugin.separator) || line.contains(plugin.papiTag) || line.contains(plugin.checkTag)) {
                        needsProcessing = true;
                        break;
                    }
                }
            }
        }

        if (!needsProcessing) return null;

        boolean hasPage = rawLore.stream().anyMatch(s -> s.contains(plugin.separator));
        NamespacedKey key = new NamespacedKey(plugin, plugin.nbtPageKey);
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
        String papiRegexReplace = Pattern.quote(plugin.papiTag) + "([^{}]+)\\}";

        for (String line : pageLore) {
            String processedLine = line.contains(plugin.papiTag) ? line.replaceAll(papiRegexReplace, "%$1%") : line;
            if (plugin.hasPapi) {
                try {
                    processedLine = PlaceholderAPI.setPlaceholders(player, processedLine);
                } catch (Exception ignored) {
                }
            }

            Matcher matcher = plugin.checkPattern.matcher(processedLine);
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
        return bukkitItem;
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