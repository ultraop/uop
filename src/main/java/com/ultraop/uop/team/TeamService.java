package com.ultraop.uop.team;

import com.ultraop.uop.storage.UopStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class TeamService {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,24}");
    private final UopStore store;

    public TeamService(UopStore store) {
        this.store = store;
    }

    public static String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private String base(String team) {
        return "teams.entries." + key(team);
    }

    public boolean exists(String team) {
        return team != null && store.data().isConfigurationSection(base(team));
    }

    public void create(String team) {
        validateTeamName(team);
        if (exists(team)) throw new IllegalArgumentException("Team already exists: " + team);
        store.data().set(base(team) + ".name", team);
        store.data().set(base(team) + ".members", new ArrayList<String>());
        store.data().set(base(team) + ".settings.friendly-fire",
                store.data().getBoolean("teams.friendly-fire-default", true));
        store.save();
    }

    public void delete(String team) {
        requireTeam(team);
        for (String member : members(team)) store.data().set("players." + key(member) + ".team", null);
        store.data().set(base(team), null);
        store.save();
    }

    public Set<String> list() {
        var section = store.data().getConfigurationSection("teams.entries");
        return section == null ? Set.of() : section.getKeys(false);
    }

    public String displayName(String team) {
        requireTeam(team);
        return store.data().getString(base(team) + ".name", team);
    }

    public List<String> members(String team) {
        requireTeam(team);
        return new ArrayList<>(store.data().getStringList(base(team) + ".members"));
    }

    public List<Player> onlineMembers(String team) {
        List<Player> result = new ArrayList<>();
        for (String name : members(team)) {
            Player player = Bukkit.getPlayerExact(name);
            if (player != null && player.isOnline()) result.add(player);
        }
        return result;
    }

    public void add(String team, String playerName) {
        addAll(team, List.of(playerName));
    }

    public void addAll(String team, List<String> playerNames) {
        requireTeam(team);
        for (String playerName : playerNames) {
            validatePlayerName(playerName);
            String currentTeam = teamOf(playerName);
            if (currentTeam != null && !key(currentTeam).equals(key(team))) {
                throw new IllegalArgumentException(playerName + " is already in team " + currentTeam + ". Remove or move the member first.");
            }
        }
        List<String> updated = members(team);
        for (String playerName : playerNames) {
            if (updated.stream().noneMatch(n -> key(n).equals(key(playerName)))) updated.add(playerName);
            String profile = "players." + key(playerName);
            store.data().set(profile + ".team", displayName(team));
            store.data().set(profile + ".last-name", playerName);
            Player online = Bukkit.getPlayerExact(playerName);
            if (online != null) store.data().set(profile + ".last-uuid", online.getUniqueId().toString());
        }
        store.data().set(base(team) + ".members", updated);
        store.save();
    }

    public void remove(String team, String playerName) {
        requireTeam(team);
        List<String> names = members(team);
        names.removeIf(n -> key(n).equals(key(playerName)));
        store.data().set(base(team) + ".members", names);
        if (key(team).equals(key(teamOf(playerName)))) store.data().set("players." + key(playerName) + ".team", null);
        store.save();
    }

    public void removeAll(String team) {
        requireTeam(team);
        for (String member : members(team)) store.data().set("players." + key(member) + ".team", null);
        store.data().set(base(team) + ".members", new ArrayList<String>());
        store.save();
    }

    public String teamOf(String playerName) {
        if (playerName == null) return null;
        return store.data().getString("players." + key(playerName) + ".team");
    }

    public boolean contains(String team, String playerName) {
        return exists(team) && members(team).stream().anyMatch(n -> key(n).equals(key(playerName)));
    }

    public boolean friendlyFire(String team) {
        requireTeam(team);
        return store.data().getBoolean(base(team) + ".settings.friendly-fire", true);
    }

    public void friendlyFire(String team, boolean enabled) {
        requireTeam(team);
        store.data().set(base(team) + ".settings.friendly-fire", enabled);
        store.save();
    }

    public void requireTeam(String team) {
        if (!exists(team)) throw new IllegalArgumentException("Unknown team: " + team);
    }

    public static void validateTeamName(String name) {
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("Team name must be 1-24 letters, numbers, '_' or '-'.");
    }

    public static void validatePlayerName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid Minecraft player name: " + name);
    }
}