package net.danh.pagelore;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.pagelore.command.PageLoreCommand;
import net.danh.pagelore.listeners.InventoryClickListener;
import net.danh.pagelore.listeners.ItemPacketListener;
import net.danh.pagelore.utils.ConfigUtils;
import org.bukkit.plugin.java.JavaPlugin;

public class PageLore extends JavaPlugin {

    private static PageLore instance;
    private ConfigUtils settingsConfig;
    private ConfigUtils messagesConfig;

    public static PageLore getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        settingsConfig = new ConfigUtils(this, "config.yml");
        messagesConfig = new ConfigUtils(this, "messages.yml");

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(new PageLoreCommand(this).buildCommand(), "Main command"));

        getServer().getPluginManager().registerEvents(new InventoryClickListener(), this);
        PacketEvents.getAPI().getEventManager().registerListener(new ItemPacketListener(), PacketListenerPriority.NORMAL);
    }

    @Override
    public void onDisable() {
        settingsConfig.save();
        messagesConfig.save();
        instance = null;
    }

    public ConfigUtils getSettings() {
        return settingsConfig;
    }

    public ConfigUtils getMessages() {
        return messagesConfig;
    }
}