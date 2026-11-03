package com.ultraop.uop.service;

import com.ultraop.uop.profile.PlayerProfileService;
import org.bukkit.entity.Player;

public final class FreezeImmortalService {
    private final PlayerProfileService profiles;

    public FreezeImmortalService(PlayerProfileService profiles) {
        this.profiles = profiles;
    }

    public boolean exempt(Player player) {
        return player.isOp() || player.hasPermission("uop.freeze.exempt");
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