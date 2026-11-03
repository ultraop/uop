package com.ultraop.uop.storage;

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
        data = YamlConfiguration.loadConfiguration(file);
        if (!data.contains("schema-version")) data.set("schema-version", 1);
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