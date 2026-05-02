package net.danh.pagelore.listeners;

import net.danh.pagelore.PageLore;
import net.danh.pagelore.utils.ColorUtils;
import net.danh.pagelore.utils.ServerVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Handles inventory interactions, page switching, and throttled cooldown validations.
 */
public class InventoryClickListener implements Listener {

    // Throttles the cooldown message spam when players hold down the interact key
    private final Map<UUID, Long> lastMsgMap = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(PageLore.getInstance(), player::updateInventory, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

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

        if (plugin.cooldownEnabled) {
            long currentTime = System.currentTimeMillis();
            long cooldownMillis = (long) (plugin.cooldownTime * 1000);
            Long lastTime = plugin.cooldowns.get(player.getUniqueId());

            if (lastTime != null) {
                long timeLeft = (lastTime + cooldownMillis) - currentTime;
                if (timeLeft > 0) {
                    Long lastMsg = lastMsgMap.get(player.getUniqueId());
                    // Only dispatch the UI message once per second to prevent network choking
                    if (lastMsg == null || currentTime - lastMsg >= 1000) {
                        sendCooldownMessage(player, plugin, timeLeft);
                        lastMsgMap.put(player.getUniqueId(), currentTime);
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

        playClickSound(player, plugin);
    }

    private void sendCooldownMessage(Player player, PageLore plugin, long timeLeft) {
        String rawMsg = plugin.getMessages().getString("cooldown-active");
        if (rawMsg == null || rawMsg.isEmpty()) return;

        String timeFormatted = String.format("%.1f", timeLeft / 1000.0);
        Component msgComp = ColorUtils.parseWithPrefix(rawMsg.replace("%time%", timeFormatted));

        Title.Times times = Title.Times.times(
                Duration.ofMillis(plugin.titleFadeIn * 50L),
                Duration.ofMillis(plugin.titleStay * 50L),
                Duration.ofMillis(plugin.titleFadeOut * 50L)
        );

        switch (plugin.cooldownMessageType) {
            case "ACTION_BAR" -> player.sendActionBar(msgComp);
            case "CHAT" -> player.sendMessage(msgComp);
            case "TITLE" -> player.showTitle(Title.title(msgComp, Component.empty(), times));
            case "SUBTITLE" -> player.showTitle(Title.title(Component.empty(), msgComp, times));
        }
    }

    private void playClickSound(Player player, PageLore plugin) {
        if (!plugin.playSound) return;
        try {
            if (ServerVersion.isAtLeast(1, 21, 3)) {
                String formattedSoundName = plugin.soundName.toLowerCase(Locale.ROOT).replace("_", ".");
                NamespacedKey soundKey = NamespacedKey.minecraft(formattedSoundName);
                Sound sound = Registry.SOUNDS.get(soundKey);
                if (sound != null) player.playSound(player.getLocation(), sound, plugin.soundVolume, plugin.soundPitch);
            } else {
                Sound sound = Sound.valueOf(plugin.soundName.toUpperCase(Locale.ROOT));
                player.playSound(player.getLocation(), sound, plugin.soundVolume, plugin.soundPitch);
            }
        } catch (Exception ignored) {
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PageLore.getInstance().cooldowns.remove(e.getPlayer().getUniqueId());
        lastMsgMap.remove(e.getPlayer().getUniqueId());
    }
}