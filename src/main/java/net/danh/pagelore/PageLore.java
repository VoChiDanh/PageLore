package net.danh.pagelore;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.pagelore.command.PageLoreCommand;
import net.danh.pagelore.listeners.InventoryClickListener;
import net.danh.pagelore.listeners.ItemPacketListener;
import net.danh.pagelore.tasks.AutoUpdateTask;
import net.danh.pagelore.utils.ConfigUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PageLore extends JavaPlugin {

    private static PageLore instance;
    public final Map<UUID, Long> cooldowns = new HashMap<>();
    public boolean hasPapi;
    public String separator, metSymbol, unmetSymbol, soundName;
    public boolean isDebug, playSound;
    public float soundVolume, soundPitch;
    public boolean cooldownEnabled;
    public double cooldownTime;
    public List<String> nextPageControls = new ArrayList<>();
    public List<String> previousPageControls = new ArrayList<>();

    private ConfigUtils settingsConfig;
    private ConfigUtils messagesConfig;
    private AutoUpdateTask autoUpdateTask;

    public static PageLore getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        settingsConfig = new ConfigUtils(this, "config.yml");
        messagesConfig = new ConfigUtils(this, "messages.yml");

        loadCache();
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(new PageLoreCommand(this).buildCommand(), "Main command"));

        getServer().getPluginManager().registerEvents(new InventoryClickListener(), this);
        PacketEvents.getAPI().getEventManager().registerListener(new ItemPacketListener(), PacketListenerPriority.HIGHEST);

        startTask();
    }

    @Override
    public void onDisable() {
        stopTask();
        settingsConfig.save();
        messagesConfig.save();
        instance = null;
    }

    public void loadCache() {
        hasPapi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        separator = settingsConfig.getString("settings.page-separator", "{page}");
        metSymbol = settingsConfig.getString("requirements.met-symbol", "<green>✔");
        unmetSymbol = settingsConfig.getString("requirements.unmet-symbol", "<dark_gray>✘");
        isDebug = settingsConfig.getBoolean("settings.debug", false);

        playSound = settingsConfig.getBoolean("settings.play-sound", true);
        soundName = settingsConfig.getString("settings.sound-type", "ui.button.click");
        soundVolume = (float) settingsConfig.getDouble("settings.sound-volume", 1.0);
        soundPitch = (float) settingsConfig.getDouble("settings.sound-pitch", 1.0);

        nextPageControls = settingsConfig.getStringList("controls.next-page");
        previousPageControls = settingsConfig.getStringList("controls.previous-page");

        cooldownEnabled = settingsConfig.getBoolean("settings.cooldown.enabled", true);
        cooldownTime = settingsConfig.getDouble("settings.cooldown.time", 0.5);
    }

    public void startTask() {
        stopTask();
        int updateInterval = settingsConfig.getInt("settings.auto-update-interval", 60);
        if (updateInterval > 0) {
            autoUpdateTask = new AutoUpdateTask();
            autoUpdateTask.runTaskTimer(this, updateInterval, updateInterval);
        }
    }

    public void stopTask() {
        if (autoUpdateTask != null && !autoUpdateTask.isCancelled()) {
            autoUpdateTask.cancel();
        }
    }

    public ConfigUtils getSettings() {
        return settingsConfig;
    }

    public ConfigUtils getMessages() {
        return messagesConfig;
    }
}