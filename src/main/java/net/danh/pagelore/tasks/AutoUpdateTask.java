package net.danh.pagelore.tasks;

import net.danh.pagelore.PageLore;
import net.danh.pagelore.utils.ColorUtils;
import net.danh.pagelore.utils.SchedulerUtils;
import net.danh.pagelore.utils.ServerVersion;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Periodically refreshes player inventories to keep placeholders live.
 * Player inventory access is moved onto the owning entity thread on Folia.
 */
public class AutoUpdateTask {

    public void run() {
        PageLore plugin = PageLore.getInstance();
        if (plugin == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            SchedulerUtils.runEntity(plugin, player, () -> refreshPlayer(player, plugin));
        }
    }

    private void refreshPlayer(Player player, PageLore plugin) {
        if (!player.isOnline() || player.getGameMode() == GameMode.CREATIVE) return;

        boolean needsUpdate = false;

        if (hasPageLoreOrPapi(player.getInventory().getItemInMainHand(), plugin) ||
                hasPageLoreOrPapi(player.getInventory().getItemInOffHand(), plugin)) {
            needsUpdate = true;
        }

        if (!needsUpdate && player.getOpenInventory().getTopInventory().getSize() > 0 &&
                player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {

            for (ItemStack item : player.getOpenInventory().getTopInventory().getContents()) {
                if (hasPageLoreOrPapi(item, plugin)) {
                    needsUpdate = true;
                    break;
                }
            }
        }

        if (needsUpdate) {
            player.updateInventory();
        }
    }

    /**
     * Efficiently checks if an item has lore that requires live updating.
     */
    private boolean hasPageLoreOrPapi(ItemStack item, PageLore plugin) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return false;

        if (ServerVersion.isPaper() && ServerVersion.isAtLeast(1, 16, 5)) {
            List<Component> lore = meta.lore();
            if (lore != null) {
                for (Component comp : lore) {
                    // Extract safe plain text string representation of the component for matching
                    String plainText = ColorUtils.toPlainText(comp);
                    if (plainText.contains(plugin.separator) || plainText.contains(plugin.papiTag) || plainText.contains(plugin.checkTag)) {
                        return true;
                    }
                }
            }
        } else {
            List<String> lore = meta.getLore();
            if (lore != null) {
                for (String line : lore) {
                    if (line.contains(plugin.separator) || line.contains(plugin.papiTag) || line.contains(plugin.checkTag)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
