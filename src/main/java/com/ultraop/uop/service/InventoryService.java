package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
import com.ultraop.uop.team.TeamService;
import com.ultraop.uop.target.TargetResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

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

    public InventoryService(JavaPlugin plugin, UopStore store, TeamService teams) {
        this.plugin = plugin;
        this.store = store;
        this.teams = teams;
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
        if (slot < 0 || slot > 40) throw new IllegalArgumentException("Inventory slot must be between 0 and 40.");
        return slot;
    }

    public static int validateAmount(int amount) {
        if (amount < 1 || amount > 99) throw new IllegalArgumentException("Amount must be between 1 and 99.");
        return amount;
    }

    public void set(Player player, int slot, Material material, int amount) {
        validateSlot(slot);
        validateAmount(amount);
        if (material == null || material.isAir()) throw new IllegalArgumentException("Choose a valid non-air item.");
        ItemStack item = new ItemStack(material, amount);
        if (item.getMaxStackSize() < amount) throw new IllegalArgumentException(material + " stack size cannot exceed " + item.getMaxStackSize() + ".");
        player.getInventory().setItem(slot, item);
        player.updateInventory();
    }

    public void remove(Player player, int slot) {
        validateSlot(slot);
        player.getInventory().setItem(slot, null);
        player.updateInventory();
    }

    public int give(Player player, Material material, int amount) {
        validateAmount(amount);
        if (material == null || material.isAir()) throw new IllegalArgumentException("Choose a valid non-air item.");
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(material, amount));
        player.updateInventory();
        return leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
    }

    public int take(Player player, Material material, int amount) {
        validateAmount(amount);
        if (material == null || material.isAir()) throw new IllegalArgumentException("Choose a valid non-air item.");
        int remaining = amount;
        int removed = 0;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot <= 40 && remaining > 0; slot++) {
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
        store.save();
    }

    public boolean restore(Player player, String backupName) {
        String root = backupPath(player.getName(), backupName);
        if (!store.data().contains(root + ".snapshot")) return false;
        Object raw = store.data().get(root + ".snapshot");
        if (!(raw instanceof Map<?, ?> snapshot)) return false;
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

    public void startRecord(List<Player> players) {
        for (Player player : players) {
            String name = player.getName();
            recording.add(name.toLowerCase(Locale.ROOT));
            String history = playerPath(name) + ".history";
            if (!store.data().contains(history)) store.data().set(history, new ArrayList<>());
            record(player, true);
        }
        store.save();
    }

    public void stopRecord(List<Player> players) {
        for (Player player : players) {
            recording.remove(player.getName().toLowerCase(Locale.ROOT));
            lastFingerprints.remove(player.getName().toLowerCase(Locale.ROOT));
        }
        store.save();
    }

    public List<String> recordingNames() {
        return recording.stream().sorted().toList();
    }

    public void tickHistory() {
        for (String name : new ArrayList<>(recording)) {
            Player player = plugin.getServer().getOnlinePlayers().stream()
                    .filter(candidate -> candidate.getName().equalsIgnoreCase(name))
                    .findFirst().orElse(null);
            if (player != null && player.isOnline()) record(player, false);
            else {
                recording.remove(name);
                lastFingerprints.remove(name);
            }
        }
    }

    private void record(Player player, boolean force) {
        String fingerprint = fingerprint(player);
        String playerKey = player.getName().toLowerCase(Locale.ROOT);
        if (!force && fingerprint.equals(lastFingerprints.get(playerKey))) return;

        String historyPath = playerPath(player.getName()) + ".history";
        List<Map<String, Object>> history = new ArrayList<>();
        for (Map<?, ?> old : store.data().getMapList(historyPath)) {
            Map<String, Object> copy = new LinkedHashMap<>();
            old.forEach((key, value) -> copy.put(String.valueOf(key), value));
            history.add(copy);
        }
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
        store.data().set(historyPath, history);
        lastFingerprints.put(playerKey, fingerprint);
        if (history.size() == 1 || history.size() % 10 == 0) store.save();
    }

    public boolean back(Player player, String timeExpression) {
        long seconds = parseTime(timeExpression);
        List<Map<?, ?>> history = store.data().getMapList(playerPath(player.getName()) + ".history");
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
        if (selected == null) selected = history.get(0);
        Object raw = selected.get("snapshot");
        if (!(raw instanceof Map<?, ?> snapshot)) return false;
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