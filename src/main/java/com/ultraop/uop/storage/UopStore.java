package com.ultraop.uop.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Main-thread YAML store with atomic replacement. */
public final class UopStore {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public UopStore(JavaPlugin plugin) {
        this.plugin = plugin;
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Could not create UOP data directory.");
        }
        this.file = new File(folder, "uop.yml");
        reload();
    }

    public synchronized void reload() {
        YamlConfiguration loaded = new YamlConfiguration();
        if (file.exists()) {
            if (file.length() == 0L) {
                throw new IllegalStateException("UOP data file is empty. The plugin has stopped loading to protect the original file.");
            }
            try {
                // Use the strict loader. loadConfiguration(File) may return an empty
                // configuration after a parse problem, which could later overwrite
                // the user's existing data with a blank file.
                loaded.load(file);
            } catch (IOException | InvalidConfigurationException ex) {
                plugin.getLogger().severe("UOP data file could not be parsed; original file was left untouched: "
                        + ex.getMessage());
                throw new IllegalStateException("UOP data is invalid. The plugin has stopped loading to protect it.", ex);
            }
            if (loaded.getKeys(false).isEmpty()) {
                throw new IllegalStateException("UOP data file contains no records. The plugin has stopped loading to protect the original file.");
            }
        }
        Object schemaValue = loaded.get("schema-version");
        if (schemaValue != null && !(schemaValue instanceof Number)) {
            throw new IllegalStateException("UOP data schema-version is not numeric; original data was left untouched.");
        }
        if (!loaded.contains("schema-version")) loaded.set("schema-version", 1);
        int schemaVersion = loaded.getInt("schema-version", 1);
        if (schemaVersion != 1) {
            throw new IllegalStateException("Unsupported UOP data schema version " + schemaVersion
                    + "; expected version 1. Original data was left untouched.");
        }
        data = loaded;
    }

    public synchronized YamlConfiguration data() {
        return data;
    }

    public synchronized void save() {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            data.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save UOP data: " + ex.getMessage());
            throw new IllegalStateException("UOP data could not be saved.", ex);
        }
    }
}