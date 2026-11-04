package com.ultraop.uop.service;

import com.ultraop.uop.profile.PlayerProfileService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreezeImmortalService {
    private final PlayerProfileService profiles;
    private final JavaPlugin plugin;

    public FreezeImmortalService(PlayerProfileService profiles, JavaPlugin plugin) {
        this.profiles = profiles;
        this.plugin = plugin;
    }

    public boolean exempt(Player player) {
        if (player == null) return false;
        if (player.isOp() && plugin.getConfig().getBoolean("freeze.exempt-ops", true)) return true;
        String permission = plugin.getConfig().getString("freeze.exempt-permission", "uop.freeze.exempt");
        return permission != null && !permission.isBlank() && player.hasPermission(permission);
    }

    public void freeze(String playerName, String mode) {
        profiles.freeze(playerName, mode);
    }

    public void unfreeze(String playerName) {
        profiles.unfreeze(playerName);
    }

    public boolean frozen(Player player) {
        return profiles.frozen(player) && !exempt(player);
    }

    public boolean full(Player player) {
        return "full".equalsIgnoreCase(profiles.freezeMode(player)) && frozen(player);
    }

    public void immortal(String playerName, boolean enabled) {
        profiles.immortal(playerName, enabled);
    }

    public boolean immortal(Player player) {
        return profiles.immortal(player);
    }
}