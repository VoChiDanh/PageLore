package net.danh.pagelore.tasks;

import net.danh.pagelore.PageLore;
import net.danh.pagelore.utils.ColorUtils;
import net.danh.pagelore.utils.ServerVersion;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Periodically refreshes player inventories to keep placeholders live.
 * Optimized to fail fast and prevent memory garbage generation.
 */
public class AutoUpdateTask extends BukkitRunnable {

    @Override
    public void run() {
        String separator = PageLore.getInstance().separator;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE) continue;

            boolean needsUpdate = false;

            // 1. Check Main Hand and Off Hand first (Most common location for lore items)
            if (hasPageLoreOrPapi(player.getInventory().getItemInMainHand(), separator) ||
                    hasPageLoreOrPapi(player.getInventory().getItemInOffHand(), separator)) {
                needsUpdate = true;
            }

            // 2. Only check open inventory if hands didn't trigger an update
            if (!needsUpdate && player.getOpenInventory().getTopInventory().getSize() > 0 &&
                    player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {

                for (ItemStack item : player.getOpenInventory().getTopInventory().getContents()) {
                    if (hasPageLoreOrPapi(item, separator)) {
                        needsUpdate = true;
                        break; // Stop looping immediately once we find one item
                    }
                }
            }

            if (needsUpdate) {
                player.updateInventory();
            }
        }
    }

    /**
     * Efficiently checks if an item has lore that requires live updating.
     */
    private boolean hasPageLoreOrPapi(ItemStack item, String separator) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return false;

        if (ServerVersion.isPaper() && ServerVersion.isAtLeast(1, 16, 5)) {
            List<Component> lore = meta.lore();
            if (lore != null) {
                for (Component comp : lore) {
                    String plainText = ColorUtils.toPlainText(comp);
                    if (plainText.contains(separator) || plainText.contains("{papi:") || plainText.contains("{check:")) {
                        return true;
                    }
                }
            }
        } else {
            List<String> lore = meta.getLore();
            if (lore != null) {
                for (String line : lore) {
                    if (line.contains(separator) || line.contains("{papi:") || line.contains("{check:")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}