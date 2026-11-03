package com.ultraop.uop.target;

import com.ultraop.uop.team.TeamService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TargetResolver {
    private final TeamService teams;

    public TargetResolver(TeamService teams) {
        this.teams = teams;
    }

    public static String clean(String expression) {
        if (expression == null) return "";
        String value = expression.trim();
        if (value.startsWith("★")) value = value.substring(1).trim();
        return value;
    }

    /** Commas inside selector brackets are options, not target separators. */
    private List<String> splitExpressions(String input) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;
        for (char c : input.toCharArray()) {
            if (c == '[') bracketDepth++;
            if (c == ']' && bracketDepth > 0) bracketDepth--;
            if (c == ',' && bracketDepth == 0) {
                if (!current.isEmpty()) parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) parts.add(current.toString().trim());
        return parts;
    }

    /** Resolves online players only; Team expressions expand to online roster members. */
    public List<Player> resolve(CommandSender sender, String expression) {
        String input = clean(expression);
        if (input.isBlank()) throw new IllegalArgumentException("Target is required. [Team / Player / selector]");
        Map<String, Player> result = new LinkedHashMap<>();
        for (String token : splitExpressions(input)) {
            if (token.isEmpty()) continue;
            if (teams.exists(token)) {
                for (Player player : teams.onlineMembers(token)) result.putIfAbsent(player.getUniqueId().toString(), player);
            } else if (token.startsWith("@")) {
                try {
                    for (Entity entity : Bukkit.selectEntities(sender, token)) {
                        if (entity instanceof Player player && player.isOnline()) {
                            result.putIfAbsent(player.getUniqueId().toString(), player);
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Invalid selector '" + token + "': " + ex.getMessage());
                }
            } else {
                Player player = Bukkit.getPlayerExact(token);
                if (player != null && player.isOnline()) result.putIfAbsent(player.getUniqueId().toString(), player);
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("No online players matched: " + input);
        return new ArrayList<>(result.values());
    }

    /** Resolves durable username targets. Team membership is retained even while members are offline. */
    public List<String> resolveNames(CommandSender sender, String expression) {
        String input = clean(expression);
        if (input.isBlank()) throw new IllegalArgumentException("Target is required. [Team / Player / selector]");
        Map<String, String> result = new LinkedHashMap<>();
        for (String token : splitExpressions(input)) {
            if (token.isEmpty()) continue;
            if (teams.exists(token)) {
                for (String name : teams.members(token)) result.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            } else if (token.startsWith("@")) {
                try {
                    for (Entity entity : Bukkit.selectEntities(sender, token)) {
                        if (entity instanceof Player player) result.putIfAbsent(player.getName().toLowerCase(Locale.ROOT), player.getName());
                    }
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Invalid selector '" + token + "': " + ex.getMessage());
                }
            } else {
                TeamService.validatePlayerName(token);
                result.putIfAbsent(token.toLowerCase(Locale.ROOT), token);
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("No targets matched: " + input);
        return new ArrayList<>(result.values());
    }

    /** Team suggestions are first; selectors and online names follow. */
    public List<String> suggestions(String partial) {
        String prefix = clean(partial).toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String teamKey : teams.list()) {
            String name = teams.displayName(teamKey);
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add("★" + name);
        }
        for (String selector : List.of("@a", "@p", "@r", "@s")) {
            if (selector.startsWith(prefix)) result.add(selector);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(player.getName());
        }
        return result;
    }
}