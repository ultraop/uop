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

    public void grant(String playerName, List<String> nodes) {
        List<String> current = new ArrayList<>(store.data().getStringList(path(playerName)));
        for (String node : nodes) {
            String normalized = normalize(node);
            if (normalized.equals("all")) normalized = "*";
            if (!current.contains(normalized)) current.add(normalized);
        }
        store.data().set(path(playerName), current);
        store.save();
    }

    public void revoke(String playerName, List<String> nodes) {
        List<String> current = new ArrayList<>(store.data().getStringList(path(playerName)));
        for (String node : nodes) {
            String normalized = normalize(node);
            if (normalized.equals("all")) current.clear();
            else current.removeIf(value -> normalizeStored(value).equals(normalized));
        }
        store.data().set(path(playerName), current.isEmpty() ? null : current);
        store.save();
    }

    public List<String> list(String playerName) {
        return List.copyOf(store.data().getStringList(path(playerName)));
    }

    public boolean has(Player player, String permission) {
        if (player == null) return false;
        if (player.isOp() || player.hasPermission(permission)) return true;
        String requested = normalize(permission);
        for (String stored : list(player.getName())) {
            String node = normalizeStored(stored);
            if (node.equals("*") || node.equals(requested)) return true;
            if (node.endsWith(".*") && requested.startsWith(node.substring(0, node.length() - 1))) return true;
        }
        return false;
    }

    public boolean check(String playerName, String permission) {
        String requested = normalize(permission);
        for (String stored : list(playerName)) {
            String node = normalizeStored(stored);
            if (node.equals("*") || node.equals(requested)) return true;
            if (node.endsWith(".*") && requested.startsWith(node.substring(0, node.length() - 1))) return true;
        }
        return false;
    }

    public void reset(String playerName, String permission) {
        if (permission == null || permission.equalsIgnoreCase("all")) {
            store.data().set(path(playerName), null);
        } else {
            String requested = normalize(permission);
            List<String> current = new ArrayList<>(list(playerName));
            current.removeIf(node -> normalizeStored(node).equals(requested));
            store.data().set(path(playerName), current.isEmpty() ? null : current);
        }
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