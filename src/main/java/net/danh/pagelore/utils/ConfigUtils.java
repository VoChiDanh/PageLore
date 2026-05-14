package net.danh.pagelore.utils;

import net.danh.pagelore.PageLore;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Utility wrapper for simplified YAML Configuration operations.
 * Integrated with Auto-Updater to sync missing keys from plugin jar.
 */
public class ConfigUtils {
    private final PageLore plugin;
    private final String name;
    private File file;
    private FileConfiguration config;

    public ConfigUtils(PageLore plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        this.load();
    }

    public void load() {
        file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            if (plugin.getResource(name) != null) {
                plugin.saveResource(name, false);
            } else {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        autoUpdateConfig(false);
    }

    private void autoUpdateConfig(boolean removeObsolete) {
        YamlConfiguration defaultConfig;
        try (InputStream defaultStream = plugin.getResource(name)) {
            if (defaultStream == null) return;
            defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read default " + name + ": " + e.getMessage());
            return;
        }
        boolean changed = false;

        for (String key : defaultConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
                changed = true;
            }
        }
        if (removeObsolete) {
            for (String key : config.getKeys(true)) {
                if (!defaultConfig.contains(key)) {
                    config.set(key, null);
                    changed = true;
                }
            }
        }

        if (changed) {
            save();
            plugin.getLogger().info("[Auto-Updater] Automatically updated " + name + " with missing configuration keys.");
        }
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reload() {
        load();
    }

    public String getString(String path) {
        return config.getString(path, "");
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public int getInt(String path) {
        return config.getInt(path);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public double getDouble(String path) {
        return config.getDouble(path);
    }

    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    public boolean getBoolean(String path, boolean defValue) {
        return config.getBoolean(path, defValue);
    }

    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    public Component getComponent(String path) {
        String raw = config.getString(path);
        if (raw == null) return Component.empty();
        return ColorUtils.parse(raw);
    }

    public void set(String path, Object value) {
        config.set(path, value);
    }

    public void setAndSave(String path, Object value) {
        config.set(path, value);
        save();
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
