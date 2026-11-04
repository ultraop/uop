package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
import com.ultraop.uop.team.TeamService;
import com.ultraop.uop.target.TargetResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-player inventory operations, backups, history, and deterministic Team swaps. */
public final class InventoryService {
    private final JavaPlugin plugin;
    private final UopStore store;
    private final TeamService teams;
    private final Set<String> recording = new HashSet<>();
    private final Map<String, String> lastFingerprints = new HashMap<>();
    private final Map<String, List<Map<String, Object>>> historyCache = new HashMap<>();
    private final Set<String> migratedHistoryPendingCleanup = new HashSet<>();
    private final ConcurrentMap<String, String> pendingHistoryWrites = new ConcurrentHashMap<>();
    private final AtomicBoolean historyWriteScheduled = new AtomicBoolean();
    private final ExecutorService historyWriter = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "UOP-History-Writer");
        thread.setDaemon(true);
        return thread;
    });
    private boolean persistencePending;

    public InventoryService(JavaPlugin plugin, UopStore store, TeamService teams) {
        this.plugin = plugin;
        this.store = store;
        this.teams = teams;
        for (String name : store.data().getStringList("inventory-recording.active")) {
            if (name != null && name.matches("[A-Za-z0-9_]{1,16}")) {
                recording.add(name.toLowerCase(Locale.ROOT));
            }
        }
    }

    private Path historyFile(String playerName) {
        if (playerName == null || !playerName.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("Invalid player name for inventory history.");
        }
        return plugin.getDataFolder().toPath().resolve("history")
                .resolve(playerName.toLowerCase(Locale.ROOT) + ".yml");
    }

    /**
     * History is cached in memory and stored separately from uop.yml. Legacy
     * entries are migrated lazily the first time that player's history is used.
     */
    private List<Map<String, Object>> history(String playerName) {
        String cacheKey = playerName.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> cached = historyCache.get(cacheKey);
        if (cached != null) return cached;

        List<Map<String, Object>> loaded = new ArrayList<>();
        Path file = historyFile(playerName);
        String legacyPath = playerPath(playerName) + ".history";
        if (Files.exists(file)) {
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file.toFile());
            } catch (IOException | InvalidConfigurationException ex) {
                throw new IllegalStateException("Could not read inventory history for " + playerName
                        + "; the existing history file was left untouched.", ex);
            }
            for (Map<?, ?> row : yaml.getMapList("history")) {
                Map<String, Object> copy = new LinkedHashMap<>();
                row.forEach((key, value) -> copy.put(String.valueOf(key), value));
                loaded.add(copy);
            }
            if (store.data().contains(legacyPath)) {
                store.data().set(legacyPath, null);
                saveStoreSafely("legacy inventory history cleanup");
            }
        } else {
            for (Map<?, ?> row : store.data().getMapList(legacyPath)) {
                Map<String, Object> copy = new LinkedHashMap<>();
                row.forEach((key, value) -> copy.put(String.valueOf(key), value));
                loaded.add(copy);
            }
            if (!loaded.isEmpty()) {
                // Keep the legacy copy until the separate file has been written
                // successfully. A failed write must never erase the only copy.
                migratedHistoryPendingCleanup.add(cacheKey);
                queueHistoryWrite(playerName, loaded);
            }
        }
        historyCache.put(cacheKey, loaded);
        return loaded;
    }

    private void queueHistoryWrite(String playerName, List<Map<String, Object>> history) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("history", history);
        pendingHistoryWrites.put(playerName.toLowerCase(Locale.ROOT), yaml.saveToString());
        scheduleHistoryWrite();
    }

    private void scheduleHistoryWrite() {
        if (pendingHistoryWrites.isEmpty() || !historyWriteScheduled.compareAndSet(false, true)) return;
        try {
            historyWriter.execute(() -> {
                boolean failed = false;
                try {
                    // Drain updates that arrive while a previous file version is being
                    // written. This guarantees the newest snapshot is flushed on disable.
                    while (!pendingHistoryWrites.isEmpty()) {
                        Map<String, String> batch = new HashMap<>(pendingHistoryWrites);
                        for (Map.Entry<String, String> entry : batch.entrySet()) {
                            Path target = historyFile(entry.getKey());
                            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
                            Files.createDirectories(target.getParent());
                            Files.writeString(temporary, entry.getValue(), StandardCharsets.UTF_8,
                                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                                    StandardOpenOption.WRITE);
                            try {
                                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                                        StandardCopyOption.REPLACE_EXISTING);
                            } catch (AtomicMoveNotSupportedException ignored) {
                                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                            pendingHistoryWrites.remove(entry.getKey(), entry.getValue());
                        }
                    }
                } catch (IOException | RuntimeException ex) {
                    failed = true;
                    plugin.getLogger().severe("Could not persist inventory history asynchronously; "
                            + "the pending history remains queued for retry: " + ex.getMessage());
                } finally {
                    historyWriteScheduled.set(false);
                    if (!failed && !pendingHistoryWrites.isEmpty() && !historyWriter.isShutdown()) {
                        scheduleHistoryWrite();
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            historyWriteScheduled.set(false);
            plugin.getLogger().severe("Inventory history writer is no longer accepting writes: " + ex.getMessage());
        }
    }

    /** Flush queued history files before backup, reset, or plugin shutdown. */
    public void flushHistoryWrites() {
        if (!pendingHistoryWrites.isEmpty()) scheduleHistoryWrite();
        try {
            historyWriter.submit(() -> {}).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not flush inventory history before a destructive operation.", ex);
        }
        if (!pendingHistoryWrites.isEmpty()) {
            throw new IllegalStateException("Inventory history still has unflushed snapshots; destructive operation cancelled.");
        }
    }

    /** Flush queued history files before plugin shutdown. */
    public void shutdown() {
        try {
            flushHistoryWrites();
        } catch (IllegalStateException ex) {
            plugin.getLogger().severe(ex.getMessage());
        }
        historyWriter.shutdown();
        try {
            if (!historyWriter.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Inventory history writer did not finish within 10 seconds; "
                        + pendingHistoryWrites.size() + " file(s) may still be pending.");
                historyWriter.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            historyWriter.shutdownNow();
            plugin.getLogger().severe("Interrupted while flushing inventory history files.");
        }
        if (!pendingHistoryWrites.isEmpty()) {
            plugin.getLogger().severe("Inventory history remains unflushed for "
                    + pendingHistoryWrites.size() + " player(s).");
        }
    }

    private void cleanupMigratedHistory() {
        boolean changed = false;
        for (String playerName : new ArrayList<>(migratedHistoryPendingCleanup)) {
            if (!Files.exists(historyFile(playerName))) continue;
            store.data().set(playerPath(playerName) + ".history", null);
            migratedHistoryPendingCleanup.remove(playerName);
            changed = true;
        }
        if (changed) saveStoreSafely("legacy inventory history cleanup");
    }

    private void deleteHistoryFiles() {
        Path directory = plugin.getDataFolder().toPath().resolve("history");
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
            });
        } catch (IOException | java.io.UncheckedIOException ex) {
            throw new IllegalStateException("Could not delete inventory history files.", ex);
        }
    }

    private void persistRecordingState() {
        if (recording.isEmpty()) {
            store.data().set("inventory-recording.active", null);
        } else {
            store.data().set("inventory-recording.active", recording.stream().sorted().toList());
        }
    }

    private String playerPath(String playerName) {
        return "players." + playerName.toLowerCase(Locale.ROOT) + ".inventory";
    }

    private String backupPath(String playerName, String backupName) {
        return playerPath(playerName) + ".backups." + key(backupName);
    }

    private String key(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,48}")) {
            throw new IllegalArgumentException("Name must be 1-48 letters, numbers, '.', '_' or '-'.");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    public static int validateSlot(int slot) {
        return InventoryBounds.validateSlot(slot);
    }

    public static int validateAmount(int amount) {
        return InventoryBounds.validateAmount(amount);
    }

    public void set(Player player, int slot, Material material, int amount) {
        validateSlot(slot);
        validateAmount(amount);
        if (material == null || material.isAir() || !material.isItem() || material.isLegacy())
            throw new IllegalArgumentException("Choose a valid modern item material.");
        ItemStack item = new ItemStack(material, amount);
        if (item.getMaxStackSize() < amount) throw new IllegalArgumentException(material + " stack size cannot exceed " + item.getMaxStackSize() + ".");
        backupAutomatically(player, "before-set");
        player.getInventory().setItem(slot, item);
        player.updateInventory();
    }

    public boolean remove(Player player, int slot) {
        validateSlot(slot);
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing == null || existing.getType().isAir()) return false;
        backupAutomatically(player, "before-remove");
        player.getInventory().setItem(slot, null);
        player.updateInventory();
        return true;
    }

    public int give(Player player, Material material, int amount) {
        validateAmount(amount);
        if (material == null || material.isAir() || !material.isItem() || material.isLegacy())
            throw new IllegalArgumentException("Choose a valid modern item material.");
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(material, amount));
        player.updateInventory();
        return leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
    }

    public int take(Player player, Material material, int amount) {
        validateAmount(amount);
        if (material == null || material.isAir() || !material.isItem() || material.isLegacy())
            throw new IllegalArgumentException("Choose a valid modern item material.");
        int remaining = amount;
        int removed = 0;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot <= 40 && remaining > 0; slot++) {
            // Never consume equipped armor while executing /uop inv take.
            if (slot >= 36 && slot <= 39) continue;
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != material) continue;
            int count = Math.min(remaining, item.getAmount());
            int next = item.getAmount() - count;
            if (next <= 0) inventory.setItem(slot, null);
            else item.setAmount(next);
            remaining -= count;
            removed += count;
        }
        player.updateInventory();
        return removed;
    }

    public void clear(Player player) {
        backupAutomatically(player, "before-clear");
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);
        player.updateInventory();
    }

    public List<String> describe(Player player) {
        List<String> lines = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        lines.add("Inventory: " + player.getName() + " | Held slot: " + inventory.getHeldItemSlot());
        for (int slot = 0; slot <= 40; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            String section = slot >= 36 && slot <= 39 ? "ARMOR" : slot == 40 ? "OFFHAND" : slot <= 8 ? "HOTBAR" : "STORAGE";
            lines.add(section + " [" + slot + "] " + item.getAmount() + "x " + item.getType().name());
        }
        if (lines.size() == 1) lines.add("(empty)");
        return lines;
    }

    public void backup(Player player, String backupName) {
        String root = backupPath(player.getName(), backupName);
        store.data().set(root + ".display-name", backupName);
        store.data().set(root + ".snapshot", snapshot(player));
        store.data().set(root + ".created-at", System.currentTimeMillis());
        trimBackups(player.getName());
        store.save();
    }

    public void backupAutomatically(Player player, String reason) {
        String name = "auto-" + reason + "-" + System.currentTimeMillis();
        String root = backupPath(player.getName(), name);
        store.data().set(root + ".display-name", name);
        store.data().set(root + ".snapshot", snapshot(player));
        store.data().set(root + ".created-at", System.currentTimeMillis());
        trimBackups(player.getName());
        // Persist the safety copy before changing the live inventory.
        store.save();
    }

    private void trimBackups(String playerName) {
        String path = playerPath(playerName) + ".backups";
        var section = store.data().getConfigurationSection(path);
        if (section == null) return;
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort(java.util.Comparator.comparingLong(key ->
                store.data().getLong(path + "." + key + ".created-at", 0L)));
        while (keys.size() > 20) {
            store.data().set(path + "." + keys.remove(0), null);
        }
    }

    public boolean restore(Player player, String backupName) {
        String root = backupPath(player.getName(), backupName);
        if (!store.data().contains(root + ".snapshot")) return false;
        Object raw = store.data().get(root + ".snapshot");
        if (!(raw instanceof Map<?, ?> snapshot)) return false;
        backupAutomatically(player, "before-restore");
        restoreSnapshot(player, snapshot);
        store.save();
        return true;
    }

    public List<String> backups(String playerName) {
        var section = store.data().getConfigurationSection(playerPath(playerName) + ".backups");
        if (section == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String child : section.getKeys(false)) {
            result.add(section.getString(child + ".display-name", child));
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public boolean backupExists(String playerName, String backupName) {
        try {
            return store.data().contains(backupPath(playerName, backupName) + ".snapshot");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public void deleteBackup(String playerName, String backupName) {
        String root = backupPath(playerName, backupName);
        if (!store.data().contains(root)) throw new IllegalArgumentException("Backup not found: " + backupName);
        store.data().set(root, null);
        store.save();
    }

    public void swap(Player first, Player second) {
        if (first.equals(second)) throw new IllegalArgumentException("Choose two different players.");
        backupAutomatically(first, "before-swap");
        backupAutomatically(second, "before-swap");
        Map<String, Object> firstState = snapshot(first);
        Map<String, Object> secondState = snapshot(second);
        try {
            restoreSnapshot(first, secondState);
            restoreSnapshot(second, firstState);
        } catch (RuntimeException ex) {
            restoreSnapshot(first, firstState);
            restoreSnapshot(second, secondState);
            throw new IllegalStateException("Inventory swap failed; both inventories were rolled back.", ex);
        }
    }

    /** Team swap uses stable saved roster order; all members must be online and counts must match. */
    public int swapTeams(String firstTeam, String secondTeam) {
        teams.requireTeam(firstTeam);
        teams.requireTeam(secondTeam);
        if (firstTeam.equalsIgnoreCase(secondTeam)) throw new IllegalArgumentException("Choose two different Teams.");
        List<String> firstNames = teams.members(firstTeam);
        List<String> secondNames = teams.members(secondTeam);
        if (firstNames.isEmpty() || secondNames.isEmpty()) throw new IllegalArgumentException("Both Teams must have members.");
        if (firstNames.size() != secondNames.size()) throw new IllegalArgumentException("Team inventory swap requires equal member counts.");
        List<Player> first = onlineRoster(firstTeam, firstNames);
        List<Player> second = onlineRoster(secondTeam, secondNames);

        for (Player player : first) backupAutomatically(player, "before-team-swap");
        for (Player player : second) backupAutomatically(player, "before-team-swap");
        List<Map<String, Object>> firstSnapshots = first.stream().map(this::snapshot).toList();
        List<Map<String, Object>> secondSnapshots = second.stream().map(this::snapshot).toList();
        try {
            for (int index = 0; index < first.size(); index++) {
                restoreSnapshot(first.get(index), secondSnapshots.get(index));
                restoreSnapshot(second.get(index), firstSnapshots.get(index));
            }
        } catch (RuntimeException ex) {
            for (int index = 0; index < first.size(); index++) {
                restoreSnapshot(first.get(index), firstSnapshots.get(index));
                restoreSnapshot(second.get(index), secondSnapshots.get(index));
            }
            throw new IllegalStateException("Team swap failed; all affected inventories were rolled back.", ex);
        }
        return first.size();
    }

    private List<Player> onlineRoster(String team, List<String> roster) {
        List<Player> result = new ArrayList<>();
        for (String name : roster) {
            Player player = TargetResolver.onlinePlayer(name);
            if (player == null || !player.isOnline()) {
                throw new IllegalArgumentException("All Team members must be online for inventory swap. Offline: " + name);
            }
            result.add(player);
        }
        return result;
    }

    public int startRecord(List<Player> players) {
        int started = 0;
        List<String> failed = new ArrayList<>();
        for (Player player : players) {
            String name = player.getName();
            String key = name.toLowerCase(Locale.ROOT);
            recording.add(key);
            try {
                history(name);
                record(player, true);
                started++;
            } catch (RuntimeException ex) {
                recording.remove(key);
                lastFingerprints.remove(key);
                failed.add(name);
                plugin.getLogger().warning("Could not start inventory history for " + name
                        + "; other selected players will continue: " + ex.getMessage());
            }
        }
        persistRecordingState();
        saveStoreSafely("inventory recording start");
        if (!failed.isEmpty()) {
            plugin.getLogger().warning("Inventory history did not start for: " + String.join(", ", failed));
        }
        return started;
    }

    public void stopRecord(List<Player> players) {
        stopRecordNames(players.stream().map(Player::getName).toList());
    }

    public int stopRecordNames(List<String> playerNames) {
        int stopped = 0;
        for (String name : playerNames) {
            String key = name.toLowerCase(Locale.ROOT);
            if (recording.remove(key)) stopped++;
            lastFingerprints.remove(key);
        }
        if (stopped > 0) {
            persistRecordingState();
            scheduleHistoryWrite();
            saveStoreSafely("inventory recording stop");
        }
        return stopped;
    }

    private void saveStoreSafely(String context) {
        boolean wasPending = persistencePending;
        try {
            store.save();
            persistencePending = false;
            if (wasPending) plugin.getLogger().info("UOP data persistence recovered; pending inventory history is saved.");
        } catch (RuntimeException ex) {
            persistencePending = true;
            if (!wasPending) {
                plugin.getLogger().severe("Could not save UOP data during " + context
                        + "; the sampler will retry automatically: " + ex.getMessage());
            }
        }
    }

    public List<String> recordingNames() {
        return recording.stream().sorted().toList();
    }

    /** Clears process-local sampler state when an administrator resets UOP data. */
    public void clearRuntimeRecordingState() {
        flushHistoryWrites();
        recording.clear();
        lastFingerprints.clear();
        historyCache.clear();
        migratedHistoryPendingCleanup.clear();
        pendingHistoryWrites.clear();
        persistencePending = false;
        try {
            // The single-thread writer ensures any earlier write finishes before
            // the reset deletes the history directory.
            historyWriter.submit(this::deleteHistoryFiles).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not clear persisted inventory history safely.", ex);
        }
    }

    public void tickHistory() {
        cleanupMigratedHistory();
        // Retry failed asynchronous file writes even when the player's inventory is unchanged.
        if (!pendingHistoryWrites.isEmpty()) scheduleHistoryWrite();
        for (String name : new ArrayList<>(recording)) {
            Player player = plugin.getServer().getOnlinePlayers().stream()
                    .filter(candidate -> candidate.getName().equalsIgnoreCase(name))
                    .findFirst().orElse(null);
            if (player != null && player.isOnline()) {
                try {
                    record(player, false);
                } catch (RuntimeException ex) {
                    // A bad sample must not cancel the repeating scheduler task or stop
                    // the recording session. Keep the prior history and retry next cycle.
                    plugin.getLogger().warning("Inventory history sample failed for " + name
                            + "; recording remains active and will retry: " + ex.getMessage());
                }
            } else {
                // Keep the recording active while the player is offline. The next sample
                // after reconnect establishes a fresh baseline instead of silently stopping.
                lastFingerprints.remove(name);
            }
        }
        if (persistencePending) saveStoreSafely("inventory recording roster persistence");
    }

    private boolean record(Player player, boolean force) {
        String fingerprint = fingerprint(player);
        String playerKey = player.getName().toLowerCase(Locale.ROOT);
        if (!force && fingerprint.equals(lastFingerprints.get(playerKey))) return false;

        List<Map<String, Object>> history = history(player.getName());
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", System.currentTimeMillis());
        entry.put("snapshot", snapshot(player));
        history.add(entry);

        long windowSeconds = Math.max(0L, plugin.getConfig().getLong("history.window-seconds", 300L));
        if (windowSeconds > 0) {
            long cutoff = System.currentTimeMillis() - windowSeconds * 1000L;
            history.removeIf(row -> row.get("time") instanceof Number n && n.longValue() < cutoff);
        }
        int max = Math.max(1, plugin.getConfig().getInt("history.max-snapshots", 600));
        while (history.size() > max) history.remove(0);
        lastFingerprints.put(playerKey, fingerprint);
        queueHistoryWrite(player.getName(), history);
        return true;
    }

    public boolean back(Player player, String timeExpression) {
        long seconds = parseTime(timeExpression);
        List<Map<String, Object>> history = history(player.getName());
        if (history.isEmpty()) return false;
        long target = System.currentTimeMillis() - seconds * 1000L;
        Map<?, ?> selected = null;
        long selectedTime = Long.MIN_VALUE;
        for (Map<?, ?> entry : history) {
            Object timeValue = entry.get("time");
            if (!(timeValue instanceof Number number)) continue;
            long timestamp = number.longValue();
            if (timestamp <= target && timestamp > selectedTime) {
                selectedTime = timestamp;
                selected = entry;
            }
        }
        if (selected == null) return false;
        Object raw = selected.get("snapshot");
        if (!(raw instanceof Map<?, ?> snapshot)) return false;
        backupAutomatically(player, "before-back");
        restoreSnapshot(player, snapshot);
        store.save();
        return true;
    }

    private long parseTime(String expression) {
        if (expression == null || expression.isBlank()) throw new IllegalArgumentException("Time is required. [e.g. 30s, 5m, 1h]");
        String value = expression.toLowerCase(Locale.ROOT);
        long multiplier = value.endsWith("h") ? 3600 : value.endsWith("m") ? 60 : 1;
        String number = value.endsWith("h") || value.endsWith("m") || value.endsWith("s")
                ? value.substring(0, value.length() - 1) : value;
        try {
            long amount = Long.parseLong(number);
            if (amount < 0 || amount > 604800) throw new NumberFormatException();
            return Math.multiplyExact(amount, multiplier);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid time. Use seconds, minutes (m), or hours (h).");
        }
    }

    private String fingerprint(Player player) {
        Map<String, Object> state = snapshot(player);
        return state.toString();
    }

    private Map<String, Object> snapshot(Player player) {
        PlayerInventory inventory = player.getInventory();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("contents", cloneItems(inventory.getStorageContents()));
        state.put("armor", cloneItems(inventory.getArmorContents()));
        ItemStack offhand = inventory.getItemInOffHand();
        state.put("offhand", offhand == null ? null : offhand.clone());
        state.put("held-slot", inventory.getHeldItemSlot());
        return state;
    }

    private List<ItemStack> cloneItems(ItemStack[] source) {
        List<ItemStack> result = new ArrayList<>(source.length);
        for (ItemStack item : source) result.add(item == null ? null : item.clone());
        return result;
    }

    private void restoreSnapshot(Player player, Map<?, ?> state) {
        Object rawContents = state.get("contents");
        Object rawArmor = state.get("armor");
        if (!(rawContents instanceof List<?> contents) || !(rawArmor instanceof List<?> armor)) {
            throw new IllegalArgumentException("Inventory snapshot is incomplete.");
        }
        ItemStack[] storage = new ItemStack[36];
        ItemStack[] armorContents = new ItemStack[4];
        for (int i = 0; i < Math.min(36, contents.size()); i++) {
            storage[i] = contents.get(i) instanceof ItemStack item ? item.clone() : null;
        }
        for (int i = 0; i < Math.min(4, armor.size()); i++) {
            armorContents[i] = armor.get(i) instanceof ItemStack item ? item.clone() : null;
        }
        Object offhand = state.get("offhand");
        Object held = state.get("held-slot");
        int selectedSlot = held instanceof Number n ? Math.max(0, Math.min(8, n.intValue())) : 0;
        player.getInventory().setStorageContents(storage);
        player.getInventory().setArmorContents(armorContents);
        player.getInventory().setItemInOffHand(offhand instanceof ItemStack item ? item.clone() : null);
        player.getInventory().setHeldItemSlot(selectedSlot);
        player.updateInventory();
    }
}