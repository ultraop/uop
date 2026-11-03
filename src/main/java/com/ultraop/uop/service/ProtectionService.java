package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class ProtectionService {
    private final UopStore store;

    public ProtectionService(UopStore store) {
        this.store = store;
    }

    private String base(String playerName) {
        return "protection.entries." + playerName.toLowerCase(Locale.ROOT);
    }

    public void add(Player player, String mode) {
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
    }

    public void remove(String playerName) {
        store.data().set(base(playerName), null);
        store.save();
    }

    public Set<String> list() {
        var section = store.data().getConfigurationSection("protection.entries");
        if (section == null) return Set.of();
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String key : section.getKeys(false)) {
            result.add(section.getString(key + ".name", key));
        }
        return result;
    }

    public void reset() {
        store.data().set("protection.entries", null);
        store.save();
    }

    public boolean allowed(String name, UUID uuid, InetAddress address) {
        synchronized (store) {
            String path = base(name);
            if (!store.data().contains(path)) return true;
            String expectedUuid = store.data().getString(path + ".uuid", "");
            String expectedIp = store.data().getString(path + ".ip", "");
            String mode = normalizeMode(store.data().getString(path + ".mode", "both"));
            boolean uuidMatches = uuid != null && uuid.toString().equalsIgnoreCase(expectedUuid);
            boolean ipMatches = !expectedIp.isBlank() && expectedIp.equals(ip(address));
            return switch (mode) {
                case "uuid" -> uuidMatches;
                case "ip" -> ipMatches;
                default -> uuidMatches && ipMatches;
            };
        }
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