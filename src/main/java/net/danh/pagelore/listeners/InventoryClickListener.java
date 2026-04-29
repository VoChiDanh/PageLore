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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public class InventoryClickListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        PageLore plugin = PageLore.getInstance();
        ClickType click = e.getClick();
        String clickName = click.name();

        boolean isNext = plugin.nextPageControls.contains(clickName);
        boolean isPrev = plugin.previousPageControls.contains(clickName);

        if (!isNext && !isPrev) return;

        ItemMeta meta = item.getItemMeta();
        int totalPages = 1;
        boolean hasPageTag = false;

        if (ServerVersion.isPaper() && ServerVersion.isAtLeast(1, 16, 5)) {
            if (meta.hasLore() && meta.lore() != null) {
                for (Component comp : meta.lore()) {
                    if (ColorUtils.toPlainText(comp).contains(plugin.separator)) {
                        hasPageTag = true;
                        totalPages++;
                    }
                }
            }
        } else {
            if (meta.hasLore() && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line.contains(plugin.separator)) {
                        hasPageTag = true;
                        totalPages++;
                    }
                }
            }
        }

        if (!hasPageTag) return;

        e.setCancelled(true);

        if (e.getWhoClicked() instanceof Player player) {

            if (plugin.cooldownEnabled) {
                long currentTime = System.currentTimeMillis();
                long cooldownMillis = (long) (plugin.cooldownTime * 1000);
                Long lastTime = plugin.cooldowns.get(player.getUniqueId());

                if (lastTime != null) {
                    long timeLeft = (lastTime + cooldownMillis) - currentTime;

                    if (timeLeft > 0) {
                        String msg = plugin.getMessages().getString("cooldown-active", "");
                        if (!msg.isEmpty()) {
                            msg = msg.replace("%time%", String.format(Locale.US, "%.1f", timeLeft / 1000.0));
                            player.sendMessage(ColorUtils.parseWithPrefix(msg));
                        }
                        return;
                    }
                }
                plugin.cooldowns.put(player.getUniqueId(), currentTime);
            }

            NamespacedKey key = new NamespacedKey(plugin, "current_page");
            int currentPage = meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);

            if (isNext) {
                currentPage++;
                if (currentPage >= totalPages) currentPage = 0;
            } else {
                currentPage--;
                if (currentPage < 0) currentPage = totalPages - 1;
            }

            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, currentPage);
            item.setItemMeta(meta);
            e.setCurrentItem(item);


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


    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PageLore.getInstance().cooldowns.remove(e.getPlayer().getUniqueId());
    }
}