package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PermissionService {
    private final UopStore store;

    public PermissionService(UopStore store) {
        this.store = store;
    }

    private String path(String playerName) {
        return "players." + playerName.toLowerCase(Locale.ROOT) + ".permissions";
    }

    public void grant(String playerName, List<String> nodes) {
        List<String> current = new ArrayList<>(store.data().getStringList(path(playerName)));
        for (String raw : nodes) {
            String normalized = normalize(raw);
            if (normalized.equals("all")) normalized = "*";
            current.removeIf(value -> normalizeStored(value).equals("!" + normalized));
            if (!current.contains(normalized)) current.add(normalized);
        }
        store.data().set(path(playerName), current.isEmpty() ? null : current);
        store.save();
    }

    /**
     * An explicit deny is stored as !node. It is evaluated before Bukkit/OP grants,
     * so a per-player revoke can override an inherited uop.* or OP permission.
     */
    public void revoke(String playerName, List<String> nodes) {
        List<String> current = new ArrayList<>(store.data().getStringList(path(playerName)));
        for (String raw : nodes) {
            String normalized = normalize(raw);
            if (normalized.equals("all")) normalized = "*";
            String denied = "!" + normalized;
            if (!current.contains(denied)) current.add(denied);
        }
        store.data().set(path(playerName), current.isEmpty() ? null : current);
        store.save();
    }

    public List<String> list(String playerName) {
        return List.copyOf(store.data().getStringList(path(playerName)));
    }

    public boolean has(Player player, String permission) {
        if (player == null) return false;
        Boolean explicit = PermissionRuleSet.explicitDecision(list(player.getName()), permission);
        if (explicit != null) return explicit;
        return player.isOp() || player.hasPermission(permission);
    }

    public boolean check(String playerName, String permission) {
        Boolean explicit = PermissionRuleSet.explicitDecision(list(playerName), permission);
        if (explicit != null) return explicit;
        Player online = Bukkit.getPlayerExact(playerName);
        return online != null && (online.isOp() || online.hasPermission(permission));
    }

    public void reset(String playerName, String permission) {
        List<String> current = new ArrayList<>(list(playerName));
        if (permission == null || permission.equalsIgnoreCase("all")) {
            current.clear();
        } else {
            String requested = normalize(permission);
            current.removeIf(value -> {
                String node = normalizeStored(value);
                return node.equals(requested) || node.equals("!" + requested);
            });
        }
        store.data().set(path(playerName), current.isEmpty() ? null : current);
        store.save();
    }

    public void resetAll() {
        store.createBackup("permission-reset");
        var players = store.data().getConfigurationSection("players");
        if (players != null) {
            for (String player : players.getKeys(false)) store.data().set("players." + player + ".permissions", null);
        }
        store.save();
    }

    private String normalize(String node) {
        if (node == null || node.isBlank()) throw new IllegalArgumentException("Permission node is required.");
        String value = node.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_.*-]+")) throw new IllegalArgumentException("Invalid permission node: " + node);
        return value;
    }

    private String normalizeStored(String node) {
        if (node == null || node.isBlank()) return "";
        return node.trim().toLowerCase(Locale.ROOT);
    }
}
