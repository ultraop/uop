package com.ultraop.uop.profile;

import com.ultraop.uop.storage.UopStore;
import com.ultraop.uop.team.TeamService;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class PlayerProfileService {
    private record RuntimeState(String freeze, boolean immortal, Map<String, Boolean> locks) {}

    private final UopStore store;
    private final TeamService teams;
    // Profile state is read and mutated on the server thread; event hot paths use this cache.
    private final Map<String, RuntimeState> runtime = new HashMap<>();

    public PlayerProfileService(UopStore store, TeamService teams) {
        this.store = store;
        this.teams = teams;
    }

    private String path(String playerName) {
        return "players." + playerName.toLowerCase(Locale.ROOT);
    }

    private String key(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }

    private RuntimeState state(String playerName) {
        String key = key(playerName);
        return runtime.computeIfAbsent(key, ignored -> {
            String base = path(playerName);
            Map<String, Boolean> locks = new HashMap<>();
            for (String lock : new String[]{"inventory", "armor", "drop"}) {
                locks.put(lock, store.data().getBoolean(base + ".locks." + lock, false));
            }
            return new RuntimeState(store.data().getString(base + ".freeze"),
                    store.data().getBoolean(base + ".immortal", false), Map.copyOf(locks));
        });
    }

    private void refresh(String playerName) {
        runtime.remove(key(playerName));
        state(playerName);
    }

    public void clearRuntimeCache() {
        runtime.clear();
    }

    public void observe(Player player) {
        String base = path(player.getName());
        String previousName = store.data().getString(base + ".last-name");
        String previousUuid = store.data().getString(base + ".last-uuid");
        boolean changed = !player.getName().equals(previousName)
                || !player.getUniqueId().toString().equals(previousUuid);
        if (changed) {
            store.data().set(base + ".last-name", player.getName());
            store.data().set(base + ".last-uuid", player.getUniqueId().toString());
            store.save();
        }
        // Team membership is intentionally name-based and is not recreated or removed here.
        state(player.getName());
    }

    public void freeze(String playerName, String mode) {
        if (mode == null || (!mode.equalsIgnoreCase("normal") && !mode.equalsIgnoreCase("full"))) {
            throw new IllegalArgumentException("Freeze mode must be normal or full.");
        }
        store.data().set(path(playerName) + ".freeze", mode.toLowerCase(Locale.ROOT));
        store.save();
        refresh(playerName);
    }

    public void unfreeze(String playerName) {
        store.data().set(path(playerName) + ".freeze", null);
        store.save();
        refresh(playerName);
    }

    public String freezeMode(Player player) {
        return state(player.getName()).freeze();
    }

    public boolean frozen(Player player) {
        return freezeMode(player) != null;
    }

    public void immortal(String playerName, boolean enabled) {
        store.data().set(path(playerName) + ".immortal", enabled);
        store.save();
        refresh(playerName);
    }

    public boolean immortal(Player player) {
        return state(player.getName()).immortal();
    }

    public void lock(String playerName, String lock, boolean enabled) {
        String normalized = lock.toLowerCase(Locale.ROOT);
        if (!Map.of("inventory", true, "armor", true, "drop", true).containsKey(normalized)) {
            throw new IllegalArgumentException("Lock must be inventory, armor or drop.");
        }
        store.data().set(path(playerName) + ".locks." + normalized, enabled);
        store.save();
        refresh(playerName);
    }

    public boolean locked(Player player, String lock) {
        return state(player.getName()).locks().getOrDefault(lock.toLowerCase(Locale.ROOT), false);
    }

    public String team(Player player) {
        return teams.teamOf(player.getName());
    }
}
