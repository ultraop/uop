package com.ultraop.uop.profile;

import com.ultraop.uop.storage.UopStore;
import com.ultraop.uop.team.TeamService;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class PlayerProfileService {
    private final UopStore store;
    private final TeamService teams;

    public PlayerProfileService(UopStore store, TeamService teams) {
        this.store = store;
        this.teams = teams;
    }

    private String path(String playerName) {
        return "players." + playerName.toLowerCase(Locale.ROOT);
    }

    public void observe(Player player) {
        String base = path(player.getName());
        store.data().set(base + ".last-name", player.getName());
        store.data().set(base + ".last-uuid", player.getUniqueId().toString());
        // Team membership is intentionally name-based and is not recreated or removed here.
        store.save();
    }

    public void freeze(String playerName, String mode) {
        store.data().set(path(playerName) + ".freeze", mode);
        store.save();
    }

    public void unfreeze(String playerName) {
        store.data().set(path(playerName) + ".freeze", null);
        store.save();
    }

    public String freezeMode(Player player) {
        return store.data().getString(path(player.getName()) + ".freeze");
    }

    public boolean frozen(Player player) {
        return freezeMode(player) != null;
    }

    public void immortal(String playerName, boolean enabled) {
        store.data().set(path(playerName) + ".immortal", enabled);
        store.save();
    }

    public boolean immortal(Player player) {
        return store.data().getBoolean(path(player.getName()) + ".immortal", false);
    }

    public void lock(String playerName, String lock, boolean enabled) {
        store.data().set(path(playerName) + ".locks." + lock.toLowerCase(Locale.ROOT), enabled);
        store.save();
    }

    public boolean locked(Player player, String lock) {
        return store.data().getBoolean(path(player.getName()) + ".locks." + lock.toLowerCase(Locale.ROOT), false);
    }

    public String team(Player player) {
        return teams.teamOf(player.getName());
    }
}