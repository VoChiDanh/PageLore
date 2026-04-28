package net.danh.pagelore.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
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
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemPacketListener extends PacketListenerAbstract implements PacketListener {

    private static final Pattern CHECK_PATTERN = Pattern.compile("\\{check:(.+?)(>=|<=|>|<|==|!=)(.+?)\\}");

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot setSlot = new WrapperPlayServerSetSlot(event);
            ItemStack peItem = setSlot.getItem();

            ItemStack modified = processItem(event.getPlayer(), peItem);
            if (modified != null) {
                setSlot.setItem(modified);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems windowItems = new WrapperPlayServerWindowItems(event);
            List<ItemStack> items = windowItems.getItems();

            for (int i = 0; i < items.size(); i++) {
                ItemStack modified = processItem(event.getPlayer(), items.get(i));
                if (modified != null) {
                    items.set(i, modified);
                }
            }
            windowItems.setItems(items);
        }
    }

    private ItemStack processItem(Object playerObj, ItemStack peItem) {
        if (peItem == null || peItem.getAmount() <= 0 || peItem.getType() == ItemTypes.AIR) return null;

        org.bukkit.inventory.ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem);
        if (bukkitItem == null) return null;
        bukkitItem = bukkitItem.clone();

        if (!bukkitItem.hasItemMeta() || !bukkitItem.getItemMeta().hasLore()) return peItem;

        ItemMeta meta = bukkitItem.getItemMeta();

        List<String> rawLore = new ArrayList<>();
        if (ServerVersion.isPaper() && ServerVersion.isAtLeast(1, 16, 5)) {
            List<Component> components = meta.lore();
            if (components != null) {
                for (Component c : components) {
                    rawLore.add(MiniMessage.miniMessage().serialize(c));
                }
            }
        } else {
            fetchLegacyLore(meta, rawLore);
        }

        String separator = PageLore.getInstance().getSettings().getString("settings.page-separator", "<page>");

        boolean hasPage = false;
        for (String s : rawLore) {
            if (s.contains(separator)) {
                hasPage = true;
                break;
            }
        }

        NamespacedKey key = new NamespacedKey(PageLore.getInstance(), "current_page");
        int currentPage = meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);

        List<String> pageLore = new ArrayList<>();
        int pageIndex = 0;

        for (String line : rawLore) {
            if (line.contains(separator)) {
                pageIndex++;
                continue;
            }
            if (!hasPage || pageIndex == currentPage) {
                pageLore.add(line);
            }
        }

        String metSymbol = PageLore.getInstance().getSettings().getString("requirements.met-symbol", "<green>✔");
        String unmetSymbol = PageLore.getInstance().getSettings().getString("requirements.unmet-symbol", "<dark_gray>✘");
        boolean isDebug = PageLore.getInstance().getSettings().getBoolean("settings.debug", false);

        List<String> processedStrings = new ArrayList<>();
        Player player = playerObj instanceof Player ? (Player) playerObj : null;
        boolean hasPapi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && player != null;

        for (String line : pageLore) {
            if (line.contains("{papi:")) {
                line = line.replaceAll("\\{papi:([^{}]+)\\}", "%$1%");
            }

            if (hasPapi) {
                line = PlaceholderAPI.setPlaceholders(player, line);
            }

            Matcher matcher = CHECK_PATTERN.matcher(line);
            StringBuilder sb = new StringBuilder();

            while (matcher.find()) {
                String rawVal1 = matcher.group(1);
                String operator = matcher.group(2);
                String rawVal2 = matcher.group(3);

                String val1Str = stripAllColors(rawVal1);
                String val2Str = stripAllColors(rawVal2);

                boolean conditionMet = isConditionMet(val1Str, val2Str, operator);

                if (isDebug) {
                    PageLore.getInstance().getLogger().info("[DEBUG] Raw: " + matcher.group(0));
                    PageLore.getInstance().getLogger().info("[DEBUG] Cleaned -> [" + val1Str + "] " + operator + " [" + val2Str + "] == " + conditionMet);
                }

                matcher.appendReplacement(sb, conditionMet ? metSymbol : unmetSymbol);
            }
            matcher.appendTail(sb);
            processedStrings.add(sb.toString());
        }

        if (ServerVersion.isPaper() && ServerVersion.isAtLeast(1, 16, 5)) {
            List<Component> finalLore = new ArrayList<>();
            for (String str : processedStrings) {
                Component parsed = ColorUtils.parse(str)
                        .decoration(TextDecoration.ITALIC, false)
                        .colorIfAbsent(NamedTextColor.WHITE);
                finalLore.add(parsed);
            }
            meta.lore(finalLore);
        } else {
            List<String> finalLore = new ArrayList<>();
            for (String str : processedStrings) {
                Component parsed = ColorUtils.parse(str)
                        .decoration(TextDecoration.ITALIC, false)
                        .colorIfAbsent(NamedTextColor.WHITE);
                finalLore.add(LegacyComponentSerializer.legacySection().serialize(parsed));
            }
            applyLegacyLore(meta, finalLore);
        }

        bukkitItem.setItemMeta(meta);

        ItemStack modifiedPeItem = SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
        return modifiedPeItem != null ? modifiedPeItem : peItem;
    }

    private String stripAllColors(String input) {
        if (input == null) return "";
        String strippedMiniMessage = MiniMessage.miniMessage().stripTags(input);
        Component legacyParsed = LegacyComponentSerializer.legacySection().deserialize(strippedMiniMessage);
        return PlainTextComponentSerializer.plainText().serialize(legacyParsed).trim();
    }

    private boolean isConditionMet(String val1Str, String val2Str, String operator) {
        try {
            double val1 = Double.parseDouble(val1Str);
            double val2 = Double.parseDouble(val2Str);

            return switch (operator) {
                case ">=" -> val1 >= val2;
                case "<=" -> val1 <= val2;
                case ">" -> val1 > val2;
                case "<" -> val1 < val2;
                case "==" -> val1 == val2;
                case "!=" -> val1 != val2;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return switch (operator) {
                case "==" -> val1Str.equalsIgnoreCase(val2Str);
                case "!=" -> !val1Str.equalsIgnoreCase(val2Str);
                default -> false;
            };
        }
    }

    @SuppressWarnings("deprecation")
    private void fetchLegacyLore(ItemMeta meta, List<String> rawLore) {
        if (meta.getLore() != null) {
            rawLore.addAll(meta.getLore());
        }
    }

    @SuppressWarnings("deprecation")
    private void applyLegacyLore(ItemMeta meta, List<String> finalLore) {
        meta.setLore(finalLore);
    }
}