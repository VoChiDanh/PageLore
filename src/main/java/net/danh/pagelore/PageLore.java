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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Main plugin class for PageLore.
 * Handles initialization, configuration loading, caching, and task management.
 * Dynamic hardcoded tags are stored centrally to optimize memory and CPU usage.
 */
public class PageLore extends JavaPlugin {

    private static PageLore instance;
    public final Map<UUID, Long> cooldowns = new HashMap<>();

    public boolean hasPapi;
    public String separator;
    public String metSymbol;
    public String unmetSymbol;
    public String soundName;
    public boolean isDebug;
    public boolean playSound;
    public float soundVolume;
    public float soundPitch;
    public boolean cooldownEnabled;
    public double cooldownTime;
    public String cooldownMessageType;
    public int titleFadeIn;
    public int titleStay;
    public int titleFadeOut;

    // Extracted logic tags for advanced parsing
    public String papiTag;
    public String checkTag;
    public String nbtPageKey;
    public Pattern checkPattern;

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

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            String cmdDesc = messagesConfig.getString("command-description", "PageLore main command");
            event.registrar().register(new PageLoreCommand(this).buildCommand(), cmdDesc);
        });

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

    /**
     * Loads and caches configuration values into memory.
     * Pre-compiles the regex pattern for condition checking to drastically save CPU cycles during live packet listening.
     */
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

        cooldownMessageType = settingsConfig.getString("settings.cooldown.message-type", "ACTION_BAR").toUpperCase();
        titleFadeIn = settingsConfig.getInt("settings.cooldown.title-settings.fade-in", 10);
        titleStay = settingsConfig.getInt("settings.cooldown.title-settings.stay", 40);
        titleFadeOut = settingsConfig.getInt("settings.cooldown.title-settings.fade-out", 10);

        papiTag = settingsConfig.getString("advanced.papi-tag", "{papi:");
        checkTag = settingsConfig.getString("advanced.check-tag", "{check:");
        nbtPageKey = settingsConfig.getString("advanced.nbt-page-key", "current_page");

        String escapedCheckTag = Pattern.quote(checkTag);
        checkPattern = Pattern.compile(escapedCheckTag + "(.+?)(>=|<=|>|<|==|!=)(.+?)\\}");
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