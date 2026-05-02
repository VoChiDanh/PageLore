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

/**
 * Periodically refreshes player inventories to keep placeholders live.
 */
public class AutoUpdateTask extends BukkitRunnable {

    @Override
    public void run() {
        String separator = PageLore.getInstance().getSettings().getString("settings.page-separator", "{page}");

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (player.getGameMode() == GameMode.CREATIVE) {
                continue;
            }

            boolean needsUpdate = false;
            if (player.getOpenInventory().getTopInventory().getSize() > 0 && player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {
                for (ItemStack item : player.getOpenInventory().getTopInventory().getContents()) {
                    if (hasPageLoreOrPapi(item, separator)) {
                        needsUpdate = true;
                        break;
                    }
                }
            }

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            if (needsUpdate || hasPageLoreOrPapi(mainHand, separator) || hasPageLoreOrPapi(offHand, separator)) {
                player.updateInventory();
            }
        }
    }

    /**
     * Efficiently checks if an item has lore that requires live updating.
     *
     * @param item      The ItemStack to inspect.
     * @param separator The configured page separator.
     * @return True if the item requires an update.
     */
    private boolean hasPageLoreOrPapi(ItemStack item, String separator) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();

        if (!meta.hasLore()) return false;

        if (ServerVersion.isPaper() && ServerVersion.isAtLeast(1, 16, 5)) {
            if (meta.lore() != null) {
                for (Component comp : meta.lore()) {
                    String plainText = ColorUtils.toPlainText(comp);
                    if (plainText.contains(separator) || plainText.contains("{papi:") || plainText.contains("{check:")) {
                        return true;
                    }
                }
            }
        } else {
            if (meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line.contains(separator) || line.contains("{papi:") || line.contains("{check:")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}