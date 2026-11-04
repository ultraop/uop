package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Replays selected Team operations for offline roster members on their next login. */
public final class PendingActionService {
    private static final long MAX_PENDING_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000;
    private static final int MAX_ATTEMPTS = 10;
    private final UopStore store;
    private final JavaPlugin plugin;
    private final ArmorKitService kits;
    private final ArmorLoadoutService loadouts;
    private final CheckpointService checkpoints;
    private final EnchantService enchants;

    public PendingActionService(UopStore store, JavaPlugin plugin, ArmorKitService kits,
                                ArmorLoadoutService loadouts, CheckpointService checkpoints,
                                EnchantService enchants) {
        this.store = store;
        this.plugin = plugin;
        this.kits = kits;
        this.loadouts = loadouts;
        this.checkpoints = checkpoints;
        this.enchants = enchants;
    }

    private String path(String playerName) {
        return "players." + playerName.toLowerCase(Locale.ROOT) + ".pending-actions";
    }

    public void queueArmorSet(String playerName, String material, String mode,
                              boolean includeWeapons, List<String> weaponNames) {
        Map<String, Object> action = new HashMap<>();
        action.put("type", "armor-set");
        action.put("material", material);
        action.put("mode", mode);
        action.put("include-weapons", includeWeapons);
        action.put("weapons", new ArrayList<>(weaponNames));
        queue(playerName, action);
    }

    public void queueWeaponSet(String playerName, String mode, List<String> weaponNames) {
        Map<String, Object> action = new HashMap<>();
        action.put("type", "weapon-set");
        action.put("mode", mode);
        action.put("weapons", new ArrayList<>(weaponNames));
        queue(playerName, action);
    }

    public void queueArmorRemove(String playerName) {
        Map<String, Object> action = new HashMap<>();
        action.put("type", "armor-remove");
        queue(playerName, action);
    }

    public void queueDirectKit(String playerName, String material, int level) {
        Map<String, Object> action = new HashMap<>();
        action.put("type", "direct-kit");
        action.put("material", material);
        action.put("level", level);
        queue(playerName, action);
    }

    public void queueLoadout(String playerName, String loadoutName) {
        Map<String, Object> action = new HashMap<>();
        action.put("type", "loadout");
        action.put("name", loadoutName);
        queue(playerName, action);
    }

    public void queueEnchant(String playerName, String scope, String enchantment, int level) {
        Map<String, Object> action = new HashMap<>();
        action.put("type", "enchant");
        action.put("scope", scope);
        action.put("enchantment", enchantment);
        action.put("level", level);
        queue(playerName, action);
    }

    public void queueDisenchant(String playerName, String scope, String slot, String enchantment) {
        Map<String, Object> action = new HashMap<>();
        action.put("type", "disenchant");
        action.put("scope", scope);
        action.put("slot", slot == null ? "" : slot);
        action.put("enchantment", enchantment);
        queue(playerName, action);
    }

    public void queueCheckpointRestore(String playerName, String checkpoint) {
        Map<String, Object> action = new HashMap<>();
        action.put("type", "checkpoint-restore");
        action.put("name", checkpoint);
        queue(playerName, action);
    }

    private void queue(String playerName, Map<String, Object> action) {
        if (playerName == null || !playerName.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("Invalid pending-action player name.");
        }
        List<Map<String, Object>> actions = new ArrayList<>();
        long now = System.currentTimeMillis();
        boolean duplicate = false;
        for (Map<?, ?> old : store.data().getMapList(path(playerName))) {
            Map<String, Object> copy = copy(old);
            if (expired(copy, now)) continue;
            if (samePayload(copy, action)) duplicate = true;
            actions.add(copy);
        }
        if (!duplicate) {
            action.put("created-at", now);
            action.put("attempts", 0);
            actions.add(action);
        }
        store.data().set(path(playerName), actions);
        store.save();
    }

    private Map<String, Object> copy(Map<?, ?> raw) {
        Map<String, Object> result = new HashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private boolean expired(Map<String, Object> action, long now) {
        Object created = action.get("created-at");
        return created instanceof Number number
                && (now - number.longValue() > MAX_PENDING_AGE_MILLIS || number.longValue() > now);
    }

    private boolean samePayload(Map<String, Object> first, Map<String, Object> second) {
        Map<String, Object> left = new HashMap<>(first);
        Map<String, Object> right = new HashMap<>(second);
        left.keySet().removeIf(key -> key.equals("created-at") || key.equals("attempts"));
        right.keySet().removeIf(key -> key.equals("created-at") || key.equals("attempts"));
        return left.equals(right);
    }

    public void clearPending(String playerName) {
        store.data().set(path(playerName), null);
        store.save();
    }

    public int pendingCount(String playerName) {
        return store.data().getMapList(path(playerName)).size();
    }

    public List<String> pendingDescriptions(String playerName) {
        List<String> result = new ArrayList<>();
        int index = 1;
        for (Map<?, ?> action : store.data().getMapList(path(playerName))) {
            Object attempts = action.get("attempts");
            result.add(index++ + ". " + action.get("type") + " (attempts "
                    + (attempts == null ? 0 : attempts) + ")");
        }
        return result;
    }

    /** Runs once after the profile has been observed on PlayerJoinEvent. */
    public int applyOnJoin(Player player) {
        String actionPath = path(player.getName());
        List<Map<String, Object>> remaining = new ArrayList<>();
        List<Map<?, ?>> pending = store.data().getMapList(actionPath);
        int applied = 0;
        long now = System.currentTimeMillis();
        for (Map<?, ?> raw : pending) {
            Map<String, Object> action = copy(raw);
            if (!action.containsKey("created-at")) action.put("created-at", now);
            if (expired(action, now)) {
                plugin.getLogger().warning("Expired pending UOP action discarded for " + player.getName()
                        + ": " + action.get("type"));
                continue;
            }
            try {
                if (applyOne(player, action)) applied++;
                else retainForRetry(player, action, remaining, "operation could not be applied");
            } catch (Exception ex) {
                retainForRetry(player, action, remaining, ex.getMessage());
            }
        }
        store.data().set(actionPath, remaining.isEmpty() ? null : remaining);
        if (!pending.isEmpty()) store.save();
        return applied;
    }

    private void retainForRetry(Player player, Map<String, Object> action,
                                List<Map<String, Object>> remaining, String reason) {
        int attempts = action.get("attempts") instanceof Number number ? number.intValue() : 0;
        attempts++;
        if (attempts >= MAX_ATTEMPTS) {
            plugin.getLogger().severe("Pending UOP action discarded after " + attempts + " failed attempts for "
                    + player.getName() + " (" + action.get("type") + "): " + reason);
            return;
        }
        action.put("attempts", attempts);
        remaining.add(action);
        plugin.getLogger().warning("Pending UOP action retained for " + player.getName() + " ("
                + attempts + "/" + MAX_ATTEMPTS + "): " + reason);
    }

    private boolean applyOne(Player player, Map<String, Object> action) {
        String type = String.valueOf(action.get("type"));
        switch (type) {
            case "armor-set" -> {
                List<String> weapons = action.get("weapons") instanceof List<?> values
                        ? values.stream().map(String::valueOf).toList() : List.of();
                boolean includeWeapons = Boolean.TRUE.equals(action.get("include-weapons"));
                return kits.setArmor(player, String.valueOf(action.get("material")),
                        String.valueOf(action.get("mode")), includeWeapons, weapons);
            }
            case "weapon-set" -> {
                List<String> weapons = action.get("weapons") instanceof List<?> values
                        ? values.stream().map(String::valueOf).toList() : List.of();
                return kits.giveWeapons(player, String.valueOf(action.get("mode")), weapons);
            }
            case "armor-remove" -> {
                return kits.removeArmor(player);
            }
            case "direct-kit" -> {
                String material = String.valueOf(action.get("material"));
                int level = ((Number) action.get("level")).intValue();
                return kits.giveDirect(player, material, level);
            }
            case "loadout" -> {
                return loadouts.give(String.valueOf(action.get("name")), player);
            }
            case "enchant" -> {
                enchants.apply(List.of(player), String.valueOf(action.get("scope")),
                        String.valueOf(action.get("enchantment")), ((Number) action.get("level")).intValue());
                return true;
            }
            case "disenchant" -> {
                String slot = String.valueOf(action.get("slot"));
                enchants.remove(List.of(player), String.valueOf(action.get("scope")),
                        slot.isBlank() ? null : slot, String.valueOf(action.get("enchantment")));
                return true;
            }
            case "checkpoint-restore" -> {
                return checkpoints.restore(String.valueOf(action.get("name")), List.of(player)) > 0;
            }
            default -> {
                plugin.getLogger().warning("Unknown pending UOP action '" + type + "' for " + player.getName());
                return false;
            }
        }
    }
}