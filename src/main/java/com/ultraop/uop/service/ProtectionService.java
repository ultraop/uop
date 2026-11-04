package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Protection entries are copied into an immutable snapshot for async pre-login reads. */
public final class ProtectionService {
    private final UopStore store;
    private volatile Map<String, ProtectionEntry> entries = Map.of();

    private record ProtectionEntry(String uuid, String ip, String mode) { }

    public ProtectionService(UopStore store) {
        this.store = store;
        refreshCache();
    }

    private String base(String playerName) {
        return "protection.entries." + playerName.toLowerCase(Locale.ROOT);
    }

    public synchronized void add(Player player, String mode) {
        String normalized = normalizeMode(mode);
        String address = player.getAddress() == null ? "" : ip(player.getAddress().getAddress());
        if ((normalized.equals("ip") || normalized.equals("both")) && address.isBlank()) {
            throw new IllegalArgumentException("Cannot enable IP protection: the player address is unavailable.");
        }
        String path = base(player.getName());
        store.data().set(path + ".name", player.getName());
        store.data().set(path + ".uuid", player.getUniqueId().toString());
        store.data().set(path + ".ip", address);
        store.data().set(path + ".mode", normalized);
        store.save();
        refreshCache();
    }

    public synchronized void remove(String playerName) {
        store.data().set(base(playerName), null);
        store.save();
        refreshCache();
    }

    public synchronized Set<String> list() {
        var section = store.data().getConfigurationSection("protection.entries");
        if (section == null) return Set.of();
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String key : section.getKeys(false)) {
            result.add(section.getString(key + ".name", key));
        }
        return result;
    }

    public synchronized void reset() {
        store.data().set("protection.entries", null);
        store.save();
        refreshCache();
    }

    /** Rebuild on the server thread after writes or a store reload. */
    public synchronized void refreshCache() {
        Map<String, ProtectionEntry> next = new HashMap<>();
        ConfigurationSection section = store.data().getConfigurationSection("protection.entries");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String path = "protection.entries." + key;
                String uuid = store.data().getString(path + ".uuid", "");
                String expectedIp = store.data().getString(path + ".ip", "");
                String mode;
                try {
                    mode = normalizeMode(store.data().getString(path + ".mode", "both"));
                } catch (IllegalArgumentException ex) {
                    // Invalid stored modes fail closed using the strongest combined check.
                    mode = "both";
                }
                next.put(key.toLowerCase(Locale.ROOT), new ProtectionEntry(uuid, expectedIp, mode));
            }
        }
        entries = Map.copyOf(next);
    }

    /** Safe to call from AsyncPlayerPreLoginEvent: this reads only an immutable snapshot. */
    public boolean allowed(String name, UUID uuid, InetAddress address) {
        if (name == null) return true;
        ProtectionEntry entry = entries.get(name.toLowerCase(Locale.ROOT));
        if (entry == null) return true;
        boolean uuidMatches = uuid != null && uuid.toString().equalsIgnoreCase(entry.uuid());
        boolean ipMatches = !entry.ip().isBlank() && entry.ip().equals(ip(address));
        return switch (entry.mode()) {
            case "uuid" -> uuidMatches;
            case "ip" -> ipMatches;
            default -> uuidMatches && ipMatches;
        };
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) return "both";
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "uuid", "ip", "both" -> mode.toLowerCase(Locale.ROOT);
            default -> throw new IllegalArgumentException("Protection mode must be uuid, ip, or both.");
        };
    }

    private String ip(InetAddress address) {
        return address == null ? "" : address.getHostAddress();
    }
}
