package com.ultraop.uop.service;
import com.ultraop.uop.storage.UopStore;
import org.bukkit.entity.Player;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
/** Identity protection with a main-thread-updated immutable cache for async login checks. */
public final class ProtectionService {
    private record Entry(String name, String uuid, String ip, String mode) {}
    private final UopStore store;
    private volatile Map<String, Entry> entries = Map.of();
    public ProtectionService(UopStore store) { this.store = store; rebuildCache(); }
    private String base(String playerName) { return "protection.entries." + playerName.toLowerCase(Locale.ROOT); }
    public synchronized void add(Player player, String mode) {
        String normalized = normalizeMode(mode);
        String address = player.getAddress() == null ? "" : ip(player.getAddress().getAddress());
        if ((normalized.equals("ip") || normalized.equals("both")) && address.isBlank())
            throw new IllegalArgumentException("Cannot enable IP protection: the player address is unavailable.");
        String path = base(player.getName());
        store.data().set(path + ".name", player.getName());
        store.data().set(path + ".uuid", player.getUniqueId().toString());
        store.data().set(path + ".ip", address);
        store.data().set(path + ".mode", normalized);
        store.save(); rebuildCache();
    }
    public synchronized void remove(String playerName) { store.data().set(base(playerName), null); store.save(); rebuildCache(); }
    public synchronized Set<String> list() {
        var section = store.data().getConfigurationSection("protection.entries");
        if (section == null) return Set.of();
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String key : section.getKeys(false)) result.add(section.getString(key + ".name", key));
        return result;
    }
    public synchronized void reset() { store.data().set("protection.entries", null); store.save(); rebuildCache(); }
    /** Called on the server thread after data reload/reset; publishes an immutable snapshot. */
    public synchronized void rebuildCache() {
        Map<String, Entry> next = new LinkedHashMap<>();
        var section = store.data().getConfigurationSection("protection.entries");
        if (section != null) for (String key : section.getKeys(false)) {
            String path = "protection.entries." + key;
            String name = store.data().getString(path + ".name", key);
            next.put(key.toLowerCase(Locale.ROOT), new Entry(name,
                    store.data().getString(path + ".uuid", ""), store.data().getString(path + ".ip", ""),
                    normalizeMode(store.data().getString(path + ".mode", "both"))));
        }
        entries = Map.copyOf(next);
    }
    /** Safe for AsyncPlayerPreLoginEvent: no YAML/Bukkit access or mutable traversal. */
    public boolean allowed(String name, UUID uuid, InetAddress address) {
        if (name == null) return true;
        Entry entry = entries.get(name.toLowerCase(Locale.ROOT));
        if (entry == null) return true;
        boolean uuidMatches = uuid != null && uuid.toString().equalsIgnoreCase(entry.uuid());
        boolean ipMatches = !entry.ip().isBlank() && entry.ip().equals(ip(address));
        return switch (entry.mode()) { case "uuid" -> uuidMatches; case "ip" -> ipMatches; default -> uuidMatches && ipMatches; };
    }
    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) return "both";
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "uuid", "ip", "both" -> mode.toLowerCase(Locale.ROOT);
            default -> throw new IllegalArgumentException("Protection mode must be uuid, ip, or both.");
        };
    }
    private String ip(InetAddress address) { return address == null ? "" : address.getHostAddress(); }
}