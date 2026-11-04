package com.ultraop.uop;

import com.ultraop.uop.command.UopCommand;
import com.ultraop.uop.listener.GameplayListener;
import com.ultraop.uop.listener.NameTagListener;
import com.ultraop.uop.listener.ProtectionListener;
import com.ultraop.uop.profile.PlayerProfileService;
import com.ultraop.uop.service.ArmorKitService;
import com.ultraop.uop.service.ArmorLoadoutService;
import com.ultraop.uop.service.CheckpointService;
import com.ultraop.uop.service.DamageService;
import com.ultraop.uop.service.EnchantService;
import com.ultraop.uop.service.FreezeImmortalService;
import com.ultraop.uop.service.InventoryService;
import org.bukkit.Bukkit;
import com.ultraop.uop.service.PendingActionService;
import com.ultraop.uop.service.NameService;
import com.ultraop.uop.service.ProtectionService;
import com.ultraop.uop.service.PermissionService;
import com.ultraop.uop.storage.UopStore;
import com.ultraop.uop.target.TargetResolver;
import com.ultraop.uop.team.TeamService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class UopPlugin extends JavaPlugin {
    private UopStore store;
    private TeamService teams;
    private TargetResolver targets;
    private PlayerProfileService profiles;
    private ArmorKitService kits;
    private ArmorLoadoutService loadouts;
    private CheckpointService checkpoints;
    private DamageService damage;
    private EnchantService enchants;
    private FreezeImmortalService states;
    private PendingActionService pending;
    private InventoryService inventory;
    private NameService names;
    private ProtectionService protection;
    private PermissionService permissions;
    private int historyTask = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initializeServices();
        PluginCommand root = getCommand("uop");
        if (root == null) throw new IllegalStateException("The /uop command is missing from plugin.yml");
        UopCommand executor = new UopCommand(this, teams, targets, kits, loadouts, checkpoints,
                damage, enchants, states, profiles, pending, inventory, names, protection, permissions);
        root.setExecutor(executor);
        root.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(new GameplayListener(this, profiles, states, damage, pending), this);
        getServer().getPluginManager().registerEvents(new NameTagListener(names), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(protection), this);
        long sampleTicks = Math.max(1L, getConfig().getLong("history.sample-ticks", 100L));
        historyTask = Bukkit.getScheduler().runTaskTimer(this, inventory::tickHistory, sampleTicks, sampleTicks).getTaskId();
        getLogger().info("UOP enabled: Team, target, checkpoint, damage, enchantment, freeze and immortal services registered.");
    }

    private void initializeServices() {
        store = new UopStore(this);
        teams = new TeamService(store, getConfig().getBoolean("teams.friendly-fire-default", true));
        targets = new TargetResolver(teams);
        profiles = new PlayerProfileService(store, teams);
        kits = new ArmorKitService();
        loadouts = new ArmorLoadoutService(store);
        checkpoints = new CheckpointService(store);
        damage = new DamageService(store, teams, targets);
        enchants = new EnchantService();
        states = new FreezeImmortalService(profiles);
        configureFreezeExemptions();
        pending = new PendingActionService(store, this, kits, loadouts, checkpoints, enchants);
        inventory = new InventoryService(this, store, teams);
        names = new NameService(store);
        protection = new ProtectionService(store);
        permissions = new PermissionService(store);
    }

    @Override
    public void onDisable() {
        if (historyTask != -1) Bukkit.getScheduler().cancelTask(historyTask);
        if (names != null) names.shutdown();
        if (store != null) store.save();
    }

    private void configureFreezeExemptions() {
        if (states != null) {
            states.configureExemptions(
                    getConfig().getBoolean("freeze.exempt-ops", true),
                    getConfig().getString("freeze.exempt-permission", "uop.freeze.exempt"));
        }
    }

    /** Clears persisted UOP data and all runtime state that could recreate it. */
    public void resetAllUopData() {
        if (names != null) names.shutdown();
        if (inventory != null) inventory.clearRuntimeRecordingState();
        for (String key : new java.util.ArrayList<>(store.data().getKeys(false))) {
            if (!key.equals("schema-version")) store.data().set(key, null);
        }
        store.save();
        if (names != null) names.refreshTagCache();
        if (protection != null) protection.refreshCache();
    }

    public void reloadUop() {
        // Persist the active in-memory state before re-reading configuration/data.
        store.save();
        reloadConfig();
        store.reload();

        teams.defaultFriendlyFire(getConfig().getBoolean("teams.friendly-fire-default", true));
        configureFreezeExemptions();

        if (historyTask != -1) Bukkit.getScheduler().cancelTask(historyTask);
        long sampleTicks = Math.max(1L, getConfig().getLong("history.sample-ticks", 100L));
        historyTask = Bukkit.getScheduler()
                .runTaskTimer(this, inventory::tickHistory, sampleTicks, sampleTicks).getTaskId();

        names.refreshAll();
    }

    public UopStore store() { return store; }
    public InventoryService inventory() { return inventory; }
    public NameService names() { return names; }
    public ProtectionService protection() { return protection; }
    public PermissionService permissions() { return permissions; }
}