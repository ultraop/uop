package com.ultraop.uop.listener;

import com.ultraop.uop.service.NameService;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class NameTagListener implements Listener {
    private final NameService names;

    public NameTagListener(NameService names) {
        this.names = names;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        names.refresh(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        names.refresh(event.getPlayer());
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
                names.chatName(source).append(net.kyori.adventure.text.Component.text(" » ")).append(message)));
    }
}