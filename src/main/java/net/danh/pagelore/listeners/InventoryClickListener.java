package net.danh.pagelore.listeners;

import net.danh.pagelore.PageLore;
import net.danh.pagelore.utils.ColorUtils;
import net.danh.pagelore.utils.ServerVersion;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InventoryClickListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getCurrentItem() == null) return;
        ItemStack item = e.getCurrentItem();

        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PageLore plugin = PageLore.getInstance();

        List<String> plainLore = new ArrayList<>();

        if (ServerVersion.isPaper() && ServerVersion.isAtLeast(1, 16, 5)) {
            if (meta.hasLore()) {
                List<Component> components = meta.lore();
                if (components != null) {
                    for (Component comp : components) {
                        plainLore.add(ColorUtils.toPlainText(comp));
                    }
                }
            }
        } else {
            fetchLegacyLore(meta, plainLore);
        }

        if (plainLore.isEmpty()) return;

        boolean hasPageTag = false;
        int totalPages = 1;

        for (String line : plainLore) {
            if (line.contains(plugin.separator)) {
                hasPageTag = true;
                totalPages++;
            }
        }

        if (!hasPageTag) return;

        ClickType click = e.getClick();

        if (click == ClickType.SHIFT_RIGHT || click == ClickType.SHIFT_LEFT) {
            e.setCancelled(true);

            NamespacedKey key = new NamespacedKey(plugin, "current_page");
            int currentPage = meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);

            if (click == ClickType.SHIFT_RIGHT) {
                currentPage++;
                if (currentPage >= totalPages) currentPage = 0;
            } else {
                currentPage--;
                if (currentPage < 0) currentPage = totalPages - 1;
            }

            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, currentPage);
            item.setItemMeta(meta);

            e.setCurrentItem(item);

            if (e.getWhoClicked() instanceof Player player) {
                if (plugin.playSound) {
                    try {
                        if (ServerVersion.isAtLeast(1, 21, 3)) {
                            String formattedSoundName = plugin.soundName.toLowerCase(Locale.ROOT).replace("_", ".");
                            NamespacedKey soundKey = NamespacedKey.minecraft(formattedSoundName);
                            Sound sound = Registry.SOUNDS.get(soundKey);
                            if (sound != null) {
                                player.playSound(player.getLocation(), sound, plugin.soundVolume, plugin.soundPitch);
                            }
                        } else {
                            Sound sound = Sound.valueOf(plugin.soundName.toUpperCase(Locale.ROOT));
                            player.playSound(player.getLocation(), sound, plugin.soundVolume, plugin.soundPitch);
                        }
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Invalid sound name in config.yml: " + plugin.soundName);
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void fetchLegacyLore(ItemMeta meta, List<String> plainLore) {
        if (meta.hasLore() && meta.getLore() != null) {
            plainLore.addAll(meta.getLore());
        }
    }
}