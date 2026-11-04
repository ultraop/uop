package com.ultraop.uop.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Main-thread YAML store with atomic replacement and pre-reset rolling backups. */
public final class UopStore {
    private static final int MAX_BACKUPS = 10;
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
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

    /** Creates a zip backup of the complete plugin data folder before destructive actions. */
    public synchronized Path createBackup(String reason) {
        Path pluginFolder = plugin.getDataFolder().toPath();
        Path backupDirectory = pluginFolder.resolve("backups");
        String safeReason = reason == null ? "manual" : reason.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-");
        String stamp = LocalDateTime.now().format(BACKUP_TIME);
        Path backup = backupDirectory.resolve("UOP-" + stamp + "-" + safeReason + ".zip");
        try {
            Files.createDirectories(backupDirectory);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(backup))) {
                if (Files.exists(pluginFolder)) {
                    try (var paths = Files.walk(pluginFolder)) {
                        for (Path source : paths.filter(Files::isRegularFile)
                                .filter(path -> !path.startsWith(backupDirectory))
                                .sorted().toList()) {
                            String entryName = pluginFolder.relativize(source).toString()
                                    .replace(File.separatorChar, '/');
                            zip.putNextEntry(new ZipEntry(entryName));
                            Files.copy(source, zip);
                            zip.closeEntry();
                        }
                    }
                }
            }
            if (Files.size(backup) == 0L) {
                Files.deleteIfExists(backup);
                throw new IOException("Backup archive is empty.");
            }
            pruneBackups(backupDirectory);
            plugin.getLogger().info("Created UOP safety backup: " + backup.getFileName());
            return backup;
        } catch (IOException | RuntimeException ex) {
            try { Files.deleteIfExists(backup); } catch (IOException ignored) { /* preserve original failure */ }
            throw new IllegalStateException("UOP safety backup failed; destructive operation was cancelled.", ex);
        }
    }

    private void pruneBackups(Path directory) throws IOException {
        List<Path> backups;
        try (var paths = Files.list(directory)) {
            backups = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("UOP-")
                            && path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparingLong(this::lastModified).reversed())
                    .toList();
        }
        for (int index = MAX_BACKUPS; index < backups.size(); index++) {
            Files.deleteIfExists(backups.get(index));
        }
    }

    private long lastModified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return Long.MIN_VALUE; }
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
