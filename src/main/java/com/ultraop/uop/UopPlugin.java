package com.ultraop.uop;

import org.bukkit.plugin.java.JavaPlugin;

/** UOP plugin entry point. Feature services are added in staged implementation phases. */
public final class UopPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("UOP foundation loaded. Feature services are being implemented in phases.");
    }

    @Override
    public void onDisable() {
        getLogger().info("UOP disabled.");
    }
}
