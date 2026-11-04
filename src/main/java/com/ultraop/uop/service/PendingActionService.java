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
        long now = System.currentTimeMillis();
        long expiryDays = Math.max(1L, plugin.getConfig().getLong("pending.expiry-days", 30L));
        long expiresAt = now + expiryDays * 86_400_000L;
        action.put("created-at", now);
        action.put("expires-at", expiresAt);

        List<Map<String, Object>> actions = new ArrayList<>();
        boolean duplicate = false;
        for (Map<?, ?> old : store.data().getMapList(path(playerName))) {
            Map<String, Object> copy = new HashMap<>();
            old.forEach((k, v) -> copy.put(String.valueOf(k), v));
            Object expires = copy.get("expires-at");
            if (expires instanceof Number n && n.longValue() > 0 && n.longValue() <= now) continue;
            if (sameAction(copy, action)) duplicate = true;
            actions.add(copy);
        }
        int limit = Math.max(1, plugin.getConfig().getInt("pending.max-actions-per-player", 50));
        if (!duplicate) {
            if (actions.size() >= limit) {
                throw new IllegalArgumentException("Pending action limit reached for " + playerName
                        + " (" + limit + "). Review or clear /uop pending entries first.");
            }
            actions.add(action);
        }
        store.data().set(path(playerName), actions.isEmpty() ? null : actions);
        store.save();
    }

    private boolean sameAction(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> a = new HashMap<>(left);
        Map<String, Object> b = new HashMap<>(right);
        for (String key : List.of("created-at", "expires-at", "attempts")) {
            a.remove(key);
            b.remove(key);
        }
        return a.equals(b);
    }

    public int pendingCount(String playerName) {
        return store.data().getMapList(path(playerName)).size();
    }

    public List<String> pendingDescriptions(String playerName) {
        List<String> result = new ArrayList<>();
        int index = 1;
        for (Map<?, ?> action : store.data().getMapList(path(playerName))) {
            String type = String.valueOf(action.containsKey("type") ? action.get("type") : "unknown");
            long expires = action.get("expires-at") instanceof Number n ? n.longValue() : 0L;
            result.add("#" + index++ + " " + type + (expires > 0 ? " (expires " + expires + ")" : ""));
        }
        return result;
    }

    public int clear(String playerName) {
        int count = pendingCount(playerName);
        store.data().set(path(playerName), null);
        store.save();
        return count;
    }

    /** Runs once after the profile has been observed on PlayerJoinEvent. */
    public int applyOnJoin(Player player) {
        String actionPath = path(player.getName());
        List<Map<String, Object>> source = store.data().getMapList(actionPath).stream().map(raw -> {
            Map<String, Object> action = new HashMap<>();
            raw.forEach((k, v) -> action.put(String.valueOf(k), v));
            return action;
        }).toList();
        List<Map<String, Object>> remaining = new ArrayList<>();
        int applied = 0;
        boolean changed = false;
        long now = System.currentTimeMillis();
        long expiryDays = Math.max(1L, plugin.getConfig().getLong("pending.expiry-days", 30L));
        for (Map<String, Object> action : source) {
            Object expiry = action.get("expires-at");
            if (!(expiry instanceof Number number) || number.longValue() <= 0) {
                // Migrate pre-expiry pending actions safely: retain them once with a bounded lifetime.
                action.put("created-at", now);
                action.put("expires-at", now + expiryDays * 86_400_000L);
                changed = true;
                expiry = action.get("expires-at");
            }
            if (expiry instanceof Number number && number.longValue() > 0 && number.longValue() <= now) {
                changed = true;
                plugin.getLogger().warning("Expired pending UOP action discarded for " + player.getName()
                        + ": " + action.get("type"));
                continue;
            }
            try {
                if (applyOne(player, action)) {
                    applied++;
                    changed = true;
                } else {
                    remaining.add(action);
                }
            } catch (Exception ex) {
                remaining.add(action);
                plugin.getLogger().warning("Pending UOP action retained for " + player.getName()
                        + ": " + ex.getMessage());
            }
        }
        store.data().set(actionPath, remaining.isEmpty() ? null : remaining);
        if (changed) store.save();
        return applied;
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
                String checkpoint = String.valueOf(action.get("name"));
                if (!checkpoint.equalsIgnoreCase(CheckpointService.BACK_CHECKPOINT)) checkpoints.saveBack(player);
                return checkpoints.restore(checkpoint, List.of(player)) > 0;
            }
            default -> {
                plugin.getLogger().warning("Unknown pending UOP action '" + type + "' for " + player.getName());
                return false;
            }
        }
    }
}