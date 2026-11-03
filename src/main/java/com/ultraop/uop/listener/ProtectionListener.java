package com.ultraop.uop.listener;

import com.ultraop.uop.service.ProtectionService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class ProtectionListener implements Listener {
    private final ProtectionService protection;

    public ProtectionListener(ProtectionService protection) {
        this.protection = protection;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!protection.allowed(event.getName(), event.getUniqueId(), event.getAddress())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "This player identity is protected by UOP.");
        }
    }
}