package net.danh.pagelore.tasks;

import net.danh.pagelore.PageLore;
import net.danh.pagelore.utils.ColorUtils;
import net.danh.pagelore.utils.ServerVersion;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

public class AutoUpdateTask extends BukkitRunnable {

    @Override
    public void run() {
        String separator = PageLore.getInstance().getSettings().getString("settings.page-separator", "{page}");

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (player.getOpenInventory().getTopInventory().getSize() > 0 && player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {
                player.updateInventory();
                continue;
            }

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            if (hasPageLore(mainHand, separator) || hasPageLore(offHand, separator)) {
                player.updateInventory();
            }
        }
    }

    private boolean hasPageLore(ItemStack item, String separator) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();

        if (ServerVersion.isPaper() && ServerVersion.isAtLeast(1, 16, 5)) {
            if (!meta.hasLore() || meta.lore() == null) return false;
            for (Component comp : meta.lore()) {
                if (ColorUtils.toPlainText(comp).contains(separator)) return true;
            }
        } else {
            return checkLegacyLore(meta, separator);
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private boolean checkLegacyLore(ItemMeta meta, String separator) {
        if (!meta.hasLore() || meta.getLore() == null) return false;
        for (String line : meta.getLore()) {
            if (line.contains(separator)) return true;
        }
        return false;
    }
}