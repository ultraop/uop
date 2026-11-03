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
        List<Map<String, Object>> actions = new ArrayList<>();
        for (Map<?, ?> old : store.data().getMapList(path(playerName))) {
            Map<String, Object> copy = new HashMap<>();
            old.forEach((k, v) -> copy.put(String.valueOf(k), v));
            actions.add(copy);
        }
        actions.add(action);
        store.data().set(path(playerName), actions);
        store.save();
    }

    public int pendingCount(String playerName) {
        return store.data().getMapList(path(playerName)).size();
    }

    /** Runs once after the profile has been observed on PlayerJoinEvent. */
    public int applyOnJoin(Player player) {
        String actionPath = path(player.getName());
        List<Map<String, Object>> remaining = new ArrayList<>();
        int applied = 0;
        for (Map<?, ?> raw : store.data().getMapList(actionPath)) {
            Map<String, Object> action = new HashMap<>();
            raw.forEach((k, v) -> action.put(String.valueOf(k), v));
            try {
                if (applyOne(player, action)) applied++;
                else remaining.add(action);
            } catch (Exception ex) {
                remaining.add(action);
                plugin.getLogger().warning("Pending UOP action retained for " + player.getName()
                        + ": " + ex.getMessage());
            }
        }
        store.data().set(actionPath, remaining.isEmpty() ? null : remaining);
        if (applied > 0 || !remaining.isEmpty()) store.save();
        return applied;
    }

    private boolean applyOne(Player player, Map<String, Object> action) {
        String type = String.valueOf(action.get("type"));
        switch (type) {
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