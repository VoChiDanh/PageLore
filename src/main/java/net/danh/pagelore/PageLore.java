package net.danh.pagelore;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.pagelore.command.PageLoreCommand;
import net.danh.pagelore.listeners.InventoryClickListener;
import net.danh.pagelore.listeners.ItemPacketListener;
import net.danh.pagelore.tasks.AutoUpdateTask;
import net.danh.pagelore.utils.ConfigUtils;
import net.danh.pagelore.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class PageLore extends JavaPlugin {

    private static PageLore instance;
    public final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

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

    public String adminPermission;
    public int cacheExpireSeconds;
    public int cacheMaxSize;
    public long messageThrottleMs;
    public long desyncFixDelayTicks;

    public String papiTag;
    public String checkTag;
    public String nbtPageKey;
    public String nbtFullLoreKey;
    public Pattern checkPattern;

    public Set<String> nextPageControls = new HashSet<>();
    public Set<String> previousPageControls = new HashSet<>();
    public Set<String> fullLoreControls = new HashSet<>();

    private ConfigUtils settingsConfig;
    private ConfigUtils messagesConfig;
    private AutoUpdateTask autoUpdateTask;
    private SchedulerUtils.TaskHandle autoUpdateTaskHandle;
    private ItemPacketListener itemPacketListener;

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

        itemPacketListener = new ItemPacketListener();
        PacketEvents.getAPI().getEventManager().registerListener(itemPacketListener, PacketListenerPriority.HIGHEST);

        startTask();
    }

    @Override
    public void onDisable() {
        stopTask();

        if (itemPacketListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(itemPacketListener);
            itemPacketListener.clearCache();
            itemPacketListener = null;
        }

        cooldowns.clear();
        settingsConfig.save();
        messagesConfig.save();
        instance = null;
    }

    public void loadCache() {
        hasPapi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        separator = settingsConfig.getString("settings.page-separator", "{page}");
        metSymbol = settingsConfig.getString("requirements.met-symbol", "<green>[OK]");
        unmetSymbol = settingsConfig.getString("requirements.unmet-symbol", "<dark_gray>[NO]");
        isDebug = settingsConfig.getBoolean("settings.debug", false);

        adminPermission = settingsConfig.getString("settings.permission", "pagelore.admin");
        cacheExpireSeconds = Math.max(1, settingsConfig.getInt("settings.cache.expire-time-seconds", 1));
        cacheMaxSize = Math.max(100, settingsConfig.getInt("settings.cache.maximum-size", 5000));
        messageThrottleMs = Math.max(0L, (long) settingsConfig.getDouble("settings.cooldown.message-throttle-ms", 1000.0));
        desyncFixDelayTicks = Math.max(1, settingsConfig.getInt("settings.desync-fix-delay-ticks", 1));

        playSound = settingsConfig.getBoolean("settings.play-sound", true);
        soundName = settingsConfig.getString("settings.sound-type", "ui.button.click");
        soundVolume = Math.max(0.0f, (float) settingsConfig.getDouble("settings.sound-volume", 1.0));
        soundPitch = Math.max(0.0f, (float) settingsConfig.getDouble("settings.sound-pitch", 1.0));

        nextPageControls = normalizeControls(settingsConfig.getStringList("controls.next-page"));
        previousPageControls = normalizeControls(settingsConfig.getStringList("controls.previous-page"));
        fullLoreControls = normalizeControls(settingsConfig.getStringList("controls.full-lore"));

        cooldownEnabled = settingsConfig.getBoolean("settings.cooldown.enabled", true);
        cooldownTime = Math.max(0.0, settingsConfig.getDouble("settings.cooldown.time", 0.5));

        cooldownMessageType = settingsConfig.getString("settings.cooldown.message-type", "ACTION_BAR").toUpperCase();
        titleFadeIn = Math.max(0, settingsConfig.getInt("settings.cooldown.title-settings.fade-in", 10));
        titleStay = Math.max(0, settingsConfig.getInt("settings.cooldown.title-settings.stay", 40));
        titleFadeOut = Math.max(0, settingsConfig.getInt("settings.cooldown.title-settings.fade-out", 10));

        papiTag = settingsConfig.getString("advanced.papi-tag", "{papi:");
        checkTag = settingsConfig.getString("advanced.check-tag", "{check:");
        nbtPageKey = settingsConfig.getString("advanced.nbt-page-key", "current_page");
        nbtFullLoreKey = settingsConfig.getString("advanced.nbt-full-lore-key", "show_full_lore");

        String escapedCheckTag = Pattern.quote(checkTag);
        checkPattern = Pattern.compile(escapedCheckTag + "(.+?)(>=|<=|>|<|==|!=)(.+?)\\}");

        if (itemPacketListener != null) {
            itemPacketListener.setupCache();
        }
    }

    public void startTask() {
        stopTask();
        int updateInterval = settingsConfig.getInt("settings.auto-update-interval", 60);
        if (updateInterval > 0) {
            updateInterval = Math.max(1, updateInterval);
            autoUpdateTask = new AutoUpdateTask();
            autoUpdateTaskHandle = SchedulerUtils.runGlobalTimer(this, autoUpdateTask::run, updateInterval, updateInterval);
        }
    }

    public void stopTask() {
        if (autoUpdateTaskHandle != null) {
            autoUpdateTaskHandle.cancel();
            autoUpdateTaskHandle = null;
        }
        autoUpdateTask = null;
    }

    private Set<String> normalizeControls(List<String> configuredControls) {
        Set<String> controls = new HashSet<>();
        for (String control : configuredControls) {
            if (control != null && !control.isBlank()) {
                controls.add(control.trim().toUpperCase());
            }
        }
        return controls;
    }

    public ConfigUtils getSettings() {
        return settingsConfig;
    }

    public ConfigUtils getMessages() {
        return messagesConfig;
    }
}
