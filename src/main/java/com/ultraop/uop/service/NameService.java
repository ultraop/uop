package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
import com.ultraop.uop.target.TargetResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class NameService {
    private static final String HIDDEN_TEAM = "uop_hidden";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Set<String> COLORS = Set.of("black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white");
    private final UopStore store;

    public NameService(UopStore store) {
        this.store = store;
    }

    private String playerPath(String name) {
        return "players." + name.toLowerCase(Locale.ROOT) + ".name";
    }

    public void hide(String playerName) {
        store.data().set(playerPath(playerName) + ".hidden", true);
        store.save();
        Player player = TargetResolver.onlinePlayer(playerName);
        if (player != null) refresh(player);
    }

    public void show(String playerName) {
        store.data().set(playerPath(playerName) + ".hidden", false);
        store.save();
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) refresh(player);
    }

    public boolean hidden(Player player) {
        return store.data().getBoolean(playerPath(player.getName()) + ".hidden", false);
    }

    private Team hiddenTeam() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(HIDDEN_TEAM);
        if (team == null) team = board.registerNewTeam(HIDDEN_TEAM);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        return team;
    }

    private void removeHiddenEntry(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(HIDDEN_TEAM);
        if (team != null) team.removeEntry(player.getName());
        String previousName = store.data().getString(playerPath(player.getName()) + ".previous-scoreboard-team");
        if (previousName != null) {
            Team previous = board.getTeam(previousName);
            if (previous != null) previous.addEntry(player.getName());
            store.data().set(playerPath(player.getName()) + ".previous-scoreboard-team", null);
            store.save();
        }
    }

    public void refresh(Player player) {
        removeHiddenEntry(player);
        if (hidden(player)) {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team existing = board.getEntryTeam(player.getName());
            if (existing != null && !existing.getName().equals(HIDDEN_TEAM)) {
                store.data().set(playerPath(player.getName()) + ".previous-scoreboard-team", existing.getName());
                existing.removeEntry(player.getName());
                store.save();
            }
            hiddenTeam().addEntry(player.getName());
        }
        applyTag(player);
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
    }

    private void applyTag(Player player) {
        String tag = tagText(player.getName());
        String display = tag == null ? player.getName() : tag + "§r " + player.getName();
        player.setDisplayName(display);
        player.setCustomName(tag == null ? null : display);
        player.setCustomNameVisible(tag != null && !hidden(player));
    }

    public Component chatName(Player player) {
        String tag = tagText(player.getName());
        return tag == null ? Component.text(player.getName())
                : LEGACY.deserialize(tag + "§r " + player.getName());
    }

    public void create(String id, String color, boolean bold, boolean italic, String text) {
        String normalized = normalizeId(id);
        validateId(normalized);
        if (store.data().contains("name-tags." + normalized)) {
            throw new IllegalArgumentException("Name tag already exists: " + normalized);
        }
        writeTag(normalized, color, bold, italic, text);
    }

    public void edit(String id, String color, boolean bold, boolean italic, String text) {
        String normalized = normalizeId(id);
        validateId(normalized);
        if (!store.data().contains("name-tags." + normalized)) {
            throw new IllegalArgumentException("Name tag not found: " + normalized);
        }
        writeTag(normalized, color, bold, italic, text);
        refreshAssigned(normalized);
    }

    private void writeTag(String id, String color, boolean bold, boolean italic, String text) {
        String normalizedColor = normalizeColor(color);
        String safeText = normalizeText(text);
        String value = legacyColor(normalizedColor) + (bold ? "§l" : "") + (italic ? "§o" : "") + safeText;
        String base = "name-tags." + id;
        store.data().set(base + ".text", value);
        store.data().set(base + ".color", normalizedColor);
        store.data().set(base + ".bold", bold);
        store.data().set(base + ".italic", italic);
        store.save();
    }

    public List<String> tags() {
        var section = store.data().getConfigurationSection("name-tags");
        if (section == null) return List.of();
        List<String> result = new ArrayList<>(section.getKeys(false));
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public void deleteTag(String id) {
        String normalized = normalizeId(id);
        validateId(normalized);
        if (!store.data().contains("name-tags." + normalized)) {
            throw new IllegalArgumentException("Name tag not found: " + normalized);
        }
        var players = store.data().getConfigurationSection("players");
        if (players != null) {
            for (String playerKey : players.getKeys(false)) {
                String path = "players." + playerKey + ".name.tag";
                if (normalized.equalsIgnoreCase(store.data().getString(path, ""))) {
                    store.data().set(path, null);
                }
            }
        }
        store.data().set("name-tags." + normalized, null);
        store.save();
        refreshAssigned(normalized);
    }

    public void give(String playerName, String id) {
        String normalized = normalizeId(id);
        validateId(normalized);
        if (!store.data().contains("name-tags." + normalized)) {
            throw new IllegalArgumentException("Name tag not found: " + normalized);
        }
        store.data().set(playerPath(playerName) + ".tag", normalized);
        store.save();
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) refresh(player);
    }

    public void removeTag(String playerName) {
        store.data().set(playerPath(playerName) + ".tag", null);
        store.save();
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) refresh(player);
    }

    public boolean hasTag(Player player) {
        return tagText(player.getName()) != null;
    }

    public String tagId(String playerName) {
        return store.data().getString(playerPath(playerName) + ".tag");
    }

    private String tagText(String playerName) {
        String id = tagId(playerName);
        if (id == null || !store.data().contains("name-tags." + id)) return null;
        String text = store.data().getString("name-tags." + id + ".text");
        return text == null || text.isBlank() ? null : text;
    }

    private void refreshAssigned(String id) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (id.equalsIgnoreCase(String.valueOf(tagId(player.getName())))) refresh(player);
        }
    }

    private String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private void validateId(String id) {
        if (id.isBlank() || id.length() > 32 || !id.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Tag ID must use 1-32 letters, numbers, '_' or '-'.");
        }
    }

    private String normalizeText(String text) {
        if (text == null) throw new IllegalArgumentException("Tag text is required.");
        String safe = ChatColor.stripColor(text).trim();
        if (safe.isBlank()) throw new IllegalArgumentException("Tag text is required.");
        if (safe.length() > 32) throw new IllegalArgumentException("Tag text must be 32 characters or fewer.");
        return safe;
    }

    private String normalizeColor(String color) {
        if (color == null || color.isBlank()) throw new IllegalArgumentException("Tag color is required.");
        String value = color.trim().toLowerCase(Locale.ROOT);
        if (!COLORS.contains(value) && !value.matches("#[0-9a-f]{6}")) {
            throw new IllegalArgumentException("Color must be a Minecraft color name or #RRGGBB.");
        }
        return value;
    }

    private String legacyColor(String color) {
        if (color.startsWith("#")) {
            StringBuilder builder = new StringBuilder("§x");
            for (char digit : color.substring(1).toCharArray()) builder.append('§').append(digit);
            return builder.toString();
        }
        return switch (color) {
            case "black" -> "§0";
            case "dark_blue" -> "§1";
            case "dark_green" -> "§2";
            case "dark_aqua" -> "§3";
            case "dark_red" -> "§4";
            case "dark_purple" -> "§5";
            case "gold" -> "§6";
            case "gray" -> "§7";
            case "dark_gray" -> "§8";
            case "blue" -> "§9";
            case "green" -> "§a";
            case "aqua" -> "§b";
            case "red" -> "§c";
            case "light_purple" -> "§d";
            case "yellow" -> "§e";
            case "white" -> "§f";
            default -> throw new IllegalArgumentException("Unsupported tag color: " + color);
        };
    }
}