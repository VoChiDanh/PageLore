package net.danh.pagelore.tasks;

import net.danh.pagelore.PageLore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class AutoUpdateTask extends BukkitRunnable {

    @Override
    public void run() {
        String separator = PageLore.getInstance().getSettings().getString("settings.page-separator", "{page}");

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                continue;
            }

            boolean needsUpdate = false;
            if (player.getOpenInventory().getTopInventory().getSize() > 0 && player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {
                for (ItemStack item : player.getOpenInventory().getTopInventory().getContents()) {
                    if (hasPageLore(item, separator)) {
                        needsUpdate = true;
                        break;
                    }
                }
            }

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            if (needsUpdate || hasPageLore(mainHand, separator) || hasPageLore(offHand, separator)) {
                player.updateInventory();
            }
        }
    }

    private boolean hasPageLore(ItemStack item, String separator) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().toString().contains(separator);
    }
}