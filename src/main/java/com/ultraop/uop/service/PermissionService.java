package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
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

    private String deniedPath(String playerName) {
        return "players." + playerName.toLowerCase(Locale.ROOT) + ".denied-permissions";
    }

    public void grant(String playerName, List<String> nodes) {
        List<String> current = new ArrayList<>(store.data().getStringList(path(playerName)));
        List<String> denied = new ArrayList<>(store.data().getStringList(deniedPath(playerName)));
        for (String raw : nodes) {
            String normalized = normalize(raw);
            if (normalized.equals("all")) normalized = "*";
            if (!current.contains(normalized)) current.add(normalized);
            denied.removeIf(value -> normalizeStored(value).equals(normalized));
        }
        store.data().set(path(playerName), current);
        store.data().set(deniedPath(playerName), denied.isEmpty() ? null : denied);
        store.save();
    }

    /** Revoke creates an explicit deny so a broader UOP wildcard grant cannot re-enable the node. */
    public void revoke(String playerName, List<String> nodes) {
        List<String> current = new ArrayList<>(store.data().getStringList(path(playerName)));
        List<String> denied = new ArrayList<>(store.data().getStringList(deniedPath(playerName)));
        for (String raw : nodes) {
            String normalized = normalize(raw);
            if (normalized.equals("all")) {
                current.clear();
                denied.clear();
            } else {
                if (!denied.contains(normalized)) denied.add(normalized);
            }
        }
        store.data().set(path(playerName), current.isEmpty() ? null : current);
        store.data().set(deniedPath(playerName), denied.isEmpty() ? null : denied);
        store.save();
    }

    public List<String> list(String playerName) {
        return List.copyOf(store.data().getStringList(path(playerName)));
    }

    public List<String> listDenied(String playerName) {
        return List.copyOf(store.data().getStringList(deniedPath(playerName)));
    }

    /** Explicit UOP denies take precedence over UOP wildcard grants and Bukkit/LuckPerms permissions. */
    public boolean denied(Player player, String permission) {
        return player != null && denied(player.getName(), permission);
    }

    public boolean denied(String playerName, String permission) {
        String requested = normalize(permission);
        for (String stored : store.data().getStringList(deniedPath(playerName))) {
            if (matches(normalizeStored(stored), requested)) return true;
        }
        return false;
    }

    public boolean has(Player player, String permission) {
        if (player == null || denied(player, permission)) return false;
        return check(player.getName(), permission);
    }

    public boolean check(String playerName, String permission) {
        String requested = normalize(permission);
        if (denied(playerName, requested)) return false;
        for (String stored : list(playerName)) {
            if (matches(normalizeStored(stored), requested)) return true;
        }
        return false;
    }

    private boolean matches(String stored, String requested) {
        return stored.equals("*") || stored.equals(requested)
                || (stored.endsWith(".*") && requested.startsWith(stored.substring(0, stored.length() - 1)));
    }

    public void reset(String playerName, String permission) {
        if (permission == null || permission.equalsIgnoreCase("all")) {
            store.data().set(path(playerName), null);
            store.data().set(deniedPath(playerName), null);
        } else {
            String requested = normalize(permission);
            List<String> current = new ArrayList<>(list(playerName));
            current.removeIf(node -> normalizeStored(node).equals(requested));
            List<String> denied = new ArrayList<>(listDenied(playerName));
            denied.removeIf(node -> normalizeStored(node).equals(requested));
            store.data().set(path(playerName), current.isEmpty() ? null : current);
            store.data().set(deniedPath(playerName), denied.isEmpty() ? null : denied);
        }
        store.save();
    }

    public void resetAll() {
        var players = store.data().getConfigurationSection("players");
        if (players != null) {
            for (String player : players.getKeys(false)) {
                store.data().set("players." + player + ".permissions", null);
                store.data().set("players." + player + ".denied-permissions", null);
            }
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
