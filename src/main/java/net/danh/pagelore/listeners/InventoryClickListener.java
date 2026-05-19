package net.danh.pagelore.listeners;

import net.danh.pagelore.PageLore;
import net.danh.pagelore.utils.ColorUtils;
import net.danh.pagelore.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryClickListener implements Listener {

    private final Map<UUID, Long> lastMsgMap = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        Player player = e.getPlayer();
        PageLore plugin = PageLore.getInstance();
        if (plugin == null) return;
        SchedulerUtils.runEntityLater(plugin, player, player::updateInventory, plugin.desyncFixDelayTicks);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return;

        PageLore plugin = PageLore.getInstance();
        if (plugin == null) return;

        ClickType click = e.getClick();
        String clickName = click.name();

        boolean isNext = plugin.nextPageControls.contains(clickName);
        boolean isPrev = plugin.previousPageControls.contains(clickName);
        boolean isFullLore = plugin.fullLoreControls.contains(clickName);

        if (!isNext && !isPrev && !isFullLore) return;

        ItemMeta meta = item.getItemMeta();
        int totalPages = 1;
        boolean hasPageTag = false;

        if (meta.lore() != null) {
            for (Component comp : meta.lore()) {
                if (ColorUtils.toPlainText(comp).contains(plugin.separator)) {
                    hasPageTag = true;
                    totalPages++;
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
                    if (lastMsg == null || currentTime - lastMsg >= plugin.messageThrottleMs) {
                        sendCooldownMessage(player, plugin, timeLeft);
                        lastMsgMap.put(player.getUniqueId(), currentTime);
                    }
                    return;
                }
            }
            plugin.cooldowns.put(player.getUniqueId(), currentTime);
        }

        NamespacedKey key = new NamespacedKey(plugin, plugin.nbtPageKey);
        NamespacedKey fullLoreKey = new NamespacedKey(plugin, plugin.nbtFullLoreKey);
        int currentPage = meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));

        if (isFullLore) {
            byte currentMode = meta.getPersistentDataContainer().getOrDefault(fullLoreKey, PersistentDataType.BYTE, (byte) 0);
            byte nextMode = currentMode == 1 ? (byte) 0 : (byte) 1;
            meta.getPersistentDataContainer().set(fullLoreKey, PersistentDataType.BYTE, nextMode);
        } else if (isNext) {
            meta.getPersistentDataContainer().set(fullLoreKey, PersistentDataType.BYTE, (byte) 0);
            currentPage++;
            if (currentPage >= totalPages) currentPage = 0;
        } else {
            meta.getPersistentDataContainer().set(fullLoreKey, PersistentDataType.BYTE, (byte) 0);
            currentPage--;
            if (currentPage < 0) currentPage = totalPages - 1;
        }

        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, currentPage);
        item.setItemMeta(meta);
        e.setCurrentItem(item);

        playClickSound(player, plugin);

        SchedulerUtils.runEntityLater(plugin, player, player::updateInventory, plugin.desyncFixDelayTicks);
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
            String formattedSoundName = plugin.soundName.toLowerCase(Locale.ROOT).replace("_", ".");
            NamespacedKey soundKey = NamespacedKey.minecraft(formattedSoundName);
            Sound sound = Registry.SOUNDS.get(soundKey);
            if (sound != null) player.playSound(player.getLocation(), sound, plugin.soundVolume, plugin.soundPitch);
        } catch (Exception ignored) {
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PageLore plugin = PageLore.getInstance();
        if (plugin != null) {
            plugin.cooldowns.remove(e.getPlayer().getUniqueId());
        }
        lastMsgMap.remove(e.getPlayer().getUniqueId());
    }
}
