package com.ultraop.uop.command;

import com.ultraop.uop.UopPlugin;
import com.ultraop.uop.service.ArmorKitService;
import com.ultraop.uop.service.ArmorLoadoutService;
import com.ultraop.uop.service.CheckpointService;
import com.ultraop.uop.service.DamageService;
import com.ultraop.uop.service.EnchantService;
import com.ultraop.uop.service.FreezeImmortalService;
import com.ultraop.uop.service.PendingActionService;
import com.ultraop.uop.service.InventoryService;
import com.ultraop.uop.service.NameService;
import com.ultraop.uop.service.ProtectionService;
import com.ultraop.uop.service.PermissionService;
import org.bukkit.Material;
import com.ultraop.uop.profile.PlayerProfileService;
import com.ultraop.uop.target.TargetResolver;
import com.ultraop.uop.team.TeamService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.logging.Level;

public final class UopCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOTS = List.of("armor", "checkpoint", "damage", "disenchant", "enchant",
            "freeze", "unfreeze", "immortal", "team", "inv", "name", "protect", "permission",
            "help", "reload", "reset", "version", "pending");
    private final UopPlugin plugin;
    private final TeamService teams;
    private final TargetResolver targets;
    private final ArmorKitService kits;
    private final ArmorLoadoutService loadouts;
    private final CheckpointService checkpoints;
    private final DamageService damage;
    private final EnchantService enchants;
    private final FreezeImmortalService states;
    private final PlayerProfileService profiles;
    private final PendingActionService pending;
    private final InventoryService inventory;
    private final NameService names;
    private final ProtectionService protection;
    private final PermissionService permissions;

    public UopCommand(UopPlugin plugin, TeamService teams, TargetResolver targets,
                      ArmorKitService kits, ArmorLoadoutService loadouts,
                      CheckpointService checkpoints, DamageService damage,
                      EnchantService enchants, FreezeImmortalService states,
                      PlayerProfileService profiles, PendingActionService pending, InventoryService inventory,
                      NameService names, ProtectionService protection, PermissionService permissions) {
        this.plugin = plugin;
        this.teams = teams;
        this.targets = targets;
        this.kits = kits;
        this.loadouts = loadouts;
        this.checkpoints = checkpoints;
        this.damage = damage;
        this.enchants = enchants;
        this.states = states;
        this.profiles = profiles;
        this.pending = pending;
        this.inventory = inventory;
        this.names = names;
        this.protection = protection;
        this.permissions = permissions;
    }

    private boolean admin(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender instanceof Player player && player.isOp();
    }

    private void require(CommandSender sender, String permission) {
        if (!admin(sender) && !(sender instanceof Player player
                && (sender.hasPermission(permission) || permissions.has(player, permission)))) {
            throw new IllegalArgumentException("You do not have permission: " + permission);
        }
    }

    private void msg(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&6UOP&7] " + message));
    }

    private void hint(CommandSender sender, String label, String usage) {
        msg(sender, "&e[" + label + "]");
        msg(sender, "&7Usage: &f" + usage);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0) {
                help(sender, args);
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "armor" -> armor(sender, args);
                case "checkpoint" -> checkpoint(sender, args);
                case "damage" -> damage(sender, args);
                case "enchant" -> enchant(sender, args, false);
                case "disenchant" -> enchant(sender, args, true);
                case "team" -> team(sender, args);
                case "freeze" -> freeze(sender, args, false);
                case "unfreeze" -> freeze(sender, args, true);
                case "immortal" -> immortal(sender, args);
                case "inv" -> inventory(sender, args);
                case "help" -> help(sender, args);
                case "version" -> msg(sender, "&fUOP &7" + plugin.getPluginMeta().getVersion()
                        + " &8| &f" + Bukkit.getVersion() + " &8| &fJava " + System.getProperty("java.version"));
                case "pending" -> pending(sender, args);
                case "reload" -> {
                    require(sender, "uop.admin");
                    plugin.reloadUop();
                    msg(sender, "&aConfiguration and UOP data reloaded.");
                }
                case "reset" -> reset(sender, args);
                case "name" -> name(sender, args);
                case "protect" -> protect(sender, args);
                case "permission" -> permission(sender, args);
                default -> {
                    msg(sender, "&cUnknown command. Use /uop help.");
                }
            }
        } catch (IllegalArgumentException ex) {
            msg(sender, "&c" + ex.getMessage());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Command failure", ex);
            msg(sender, "&cOperation failed safely. Check the server console for details.");
        }
        return true;
    }

    private void help(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            msg(sender, "&6UOP &f— Command Directory");
            msg(sender, "&e★ team &7— create, delete, list, info, add, remove, removeall, tp, settings");
            msg(sender, "&e★ armor &7— set, save, list, delete, give direct/saved/random, remove, weapon");
            msg(sender, "&e★ checkpoint &7— save, tp, back, list, delete, clear");
            msg(sender, "&e★ damage &7— global, outgoing, incoming, set/pair, reset, list");
            msg(sender, "&e★ enchant / disenchant &7— all, armor, equipment, hand, inventory");
            msg(sender, "&e★ freeze / unfreeze &7— normal/full; &eimmortal &7— on/off/status/list");
            msg(sender, "&e★ inv &7— view, set, remove, give, take, clear, swap, backups, record, back, locks");
            msg(sender, "&e★ name &7— hide, show, glitch, tag");
            msg(sender, "&e★ protect / permission &7— identity and permission management");
            msg(sender, "&e★ admin &7— reload, reset all, version");
            msg(sender, "&7Usage: /uop help <group>");
            msg(sender, "&7Targets: ★ Team → @a, @p, @r, @s, @e[type=player] → Player names.");
            return;
        }

        String group = args[1].toLowerCase(Locale.ROOT);
        switch (group) {
            case "team" -> {
                msg(sender, "&f/uop team create <team_name>");
                msg(sender, "&f/uop team delete|info|removeall <team_name>");
                msg(sender, "&f/uop team add <team_name> <player|selector>...");
                msg(sender, "&f/uop team remove <team_name> <player>");
                msg(sender, "&f/uop team tp <team_name> <destination_player>");
                msg(sender, "&f/uop team settings friendlyfire <team_name> <on|off>");
                msg(sender, "&f/uop team list");
            }
            case "armor" -> {
                msg(sender, "&f/uop armor set <target> <material> <plain|enchanted 1-10|max> [true|false|on|off] [weapons...]");
                msg(sender, "&f/uop armor weapon set <target> <plain|enchanted 1-10|max> <weapons...>");
                msg(sender, "&f/uop armor save <set_name> <player> | list | delete <set_name>");
                msg(sender, "&f/uop armor give direct <material> enchanted <1-5> <target>");
                msg(sender, "&f/uop armor give saved <target> <set_name>");
                msg(sender, "&f/uop armor give random <target> <set1,set2,...|all>");
                msg(sender, "&f/uop armor remove <target>");
            }
            case "checkpoint" -> {
                msg(sender, "&f/uop checkpoint save <checkpoint_name> <target>");
                msg(sender, "&f/uop checkpoint tp|back <checkpoint_name> <target>");
                msg(sender, "&f/uop checkpoint list | delete <checkpoint_name> | clear all");
            }
            case "damage" -> {
                msg(sender, "&f/uop damage global <0-10>");
                msg(sender, "&f/uop damage outgoing|incoming <target> <0-10>");
                msg(sender, "&f/uop damage set|pair <attacker_target> <victim_target> <0-10>");
                msg(sender, "&f/uop damage reset global|all");
                msg(sender, "&f/uop damage reset outgoing|incoming <target>");
                msg(sender, "&f/uop damage reset pair <attacker_target> <victim_target> | list");
            }
            case "enchant" -> msg(sender, "&f/uop enchant <all|armor|equipment|mainhand|offhand|inventory> <target> <enchantment> <level>");
            case "disenchant" -> msg(sender, "&f/uop disenchant <all|armor|equipment|hand|inventory> <target> [mainhand|offhand|head|chest|legs|feet] <enchantment>");
            case "freeze" -> msg(sender, "&f/uop freeze <normal|full> <target>");
            case "unfreeze" -> msg(sender, "&f/uop unfreeze <target>");
            case "immortal" -> {
                msg(sender, "&f/uop immortal on|off <target>");
                msg(sender, "&f/uop immortal status <target> | list");
            }
            case "inv" -> {
                msg(sender, "&f/uop inv view|clear|backups <target>");
                msg(sender, "&f/uop inv set <target> <slot 0-40> <item> [amount] | remove <target> <slot>");
                msg(sender, "&f/uop inv give|take <target> <item> [amount]");
                msg(sender, "&f/uop inv swap player <player1> <player2> | swap team <team1> <team2>");
                msg(sender, "&f/uop inv backup|restore <target> <backup_name> | backup-delete <target> <backup_name>");
                msg(sender, "&f/uop inv record start|stop <target> | record status | back <target> <time>");
                msg(sender, "&f/uop inv lock|unlock <inventory|armor|drop> <target>");
            }
            case "name" -> {
                msg(sender, "&f/uop name hide|show <target>");
                msg(sender, "&f/uop name tag create|edit <id> <color|#RRGGBB> <bold> <italic> <text...>");
                msg(sender, "&f/uop name tag list | give|change <target> <id> | remove <target> | delete <id>");
                msg(sender, "&f/uop name glitch <target> (optional integration disabled)");
            }
            case "protect" -> {
                msg(sender, "&f/uop protect add <target> [uuid|ip|both]");
                msg(sender, "&f/uop protect remove <target> | list | reset");
            }
            case "permission" -> {
                msg(sender, "&f/uop permission grant|revoke <target> <nodes...>");
                msg(sender, "&f/uop permission list <target> | check <target> <node>");
                msg(sender, "&f/uop permission reset <target> [node|all] | reset all");
            }
            case "pending" -> {
                msg(sender, "&f/uop pending list <player>");
                msg(sender, "&f/uop pending clear <player> confirm");
            }
            case "admin" -> {
                msg(sender, "&f/uop reload");
                msg(sender, "&f/uop reset all");
                msg(sender, "&f/uop version");
            }
            default -> {
                msg(sender, "&cUnknown help group: " + group);
                msg(sender, "&7Available: team, armor, checkpoint, damage, enchant, disenchant, freeze, unfreeze, immortal, inv, name, protect, permission, admin");
            }
        }
    }

    private void armor(CommandSender sender, String[] a) {
        require(sender, "uop.armor");
        if (a.length < 2) {
            hint(sender, "Choose armor operation",
                    "/uop armor <set|save|list|delete|give|remove|weapon>");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "set" -> armorSet(sender, a);
            case "save" -> {
                if (a.length != 4) {
                    hint(sender, "Enter a loadout name, then one Player",
                            "/uop armor save <set_name> <player>");
                    return;
                }
                if (teams.exists(TargetResolver.clean(a[3]))) {
                    throw new IllegalArgumentException("Saving a loadout requires exactly one Player, not a Team.");
                }
                List<Player> selected = targets.resolve(sender, a[3]);
                if (selected.size() != 1) throw new IllegalArgumentException("Saving a loadout requires exactly one Player.");
                loadouts.save(a[2], selected.get(0));
                msg(sender, "&aSaved complete armor + inventory loadout: &f" + a[2]);
            }
            case "list" -> msg(sender, "&fSaved loadouts: &e" + String.join(", ", loadouts.list()));
            case "delete" -> {
                if (a.length != 3) {
                    hint(sender, "Select a saved loadout", "/uop armor delete <set_name>");
                    return;
                }
                loadouts.delete(a[2]);
                msg(sender, "&aDeleted loadout: &f" + a[2]);
            }
            case "give" -> armorGive(sender, a);
            case "remove" -> armorRemove(sender, a);
            case "weapon" -> armorWeapon(sender, a);
            default -> hint(sender, "Choose armor operation",
                    "/uop armor <set|save|list|delete|give|remove|weapon>");
        }
    }

    private record ArmorMode(String value, int nextIndex) {}

    private ArmorMode parseArmorMode(String[] args, int index) {
        if (args.length <= index) throw new IllegalArgumentException("Armor mode is required. [plain|enchanted 1-10|max]");
        String mode = args[index].toLowerCase(Locale.ROOT);
        if (mode.equals("plain") || mode.equals("max")) return new ArmorMode(mode, index + 1);
        if (mode.equals("enchanted")) {
            if (args.length <= index + 1) throw new IllegalArgumentException("Enchantment level is required. [1-10]");
            int level = integer(args[index + 1], 1, 10, "Armor enchantment level");
            return new ArmorMode("enchanted " + level, index + 2);
        }
        throw new IllegalArgumentException("Armor mode must be plain, enchanted 1-10, or max.");
    }

    private void armorSet(CommandSender sender, String[] a) {
        if (a.length < 5) {
            hint(sender, "Select target, material, armor mode and optional weapon setting",
                    "/uop armor set <target> <material> <plain|enchanted 1-10|max> [true|false] [weapons...]");
            return;
        }
        ArmorMode mode = parseArmorMode(a, 4);
        boolean includeWeapons = false;
        List<String> weapons = List.of();
        if (a.length > mode.nextIndex()) {
            String flag = a[mode.nextIndex()];
            if (!flag.equalsIgnoreCase("true") && !flag.equalsIgnoreCase("false")
                    && !flag.equalsIgnoreCase("on") && !flag.equalsIgnoreCase("off")) {
                throw new IllegalArgumentException("Weapon option must be true or false before weapon names.");
            }
            includeWeapons = bool(flag);
            if (includeWeapons) {
                if (a.length <= mode.nextIndex() + 1) {
                    throw new IllegalArgumentException("weapon_on is true; add at least one weapon/tool.");
                }
                weapons = Arrays.asList(a).subList(mode.nextIndex() + 1, a.length);
            } else if (a.length != mode.nextIndex() + 1) {
                throw new IllegalArgumentException("Do not add weapon names when weapon_on is false.");
            }
        }
        kits.validateSetArgs(a[3], mode.value(), includeWeapons, weapons);
        List<String> selectedNames = targets.resolveNames(sender, a[2]);
        int applied = 0, queued = 0;
        List<String> noSpace = new ArrayList<>();
        for (String name : selectedNames) {
            Player player = TargetResolver.onlinePlayer(name);
            if (player != null && player.isOnline()) {
                if (kits.setArmor(player, a[3], mode.value(), includeWeapons, weapons)) applied++;
                else noSpace.add(name);
            } else {
                pending.queueArmorSet(name, a[3], mode.value(), includeWeapons, weapons);
                queued++;
            }
        }
        msg(sender, "&aArmor set applied to " + applied + " online player(s); queued for " + queued + " offline profile(s).");
        if (!noSpace.isEmpty()) msg(sender, "&eNot enough inventory space; left unchanged: &f" + String.join(", ", noSpace));
    }

    private void armorWeapon(CommandSender sender, String[] a) {
        if (a.length < 3 || !a[2].equalsIgnoreCase("set")) {
            hint(sender, "Choose weapon operation", "/uop armor weapon set <target> <mode> <weapons...>");
            return;
        }
        if (a.length < 6) {
            hint(sender, "Select target, mode, then one or more weapons/tools",
                    "/uop armor weapon set <target> <plain|enchanted 1-10|max> <weapons...>");
            return;
        }
        ArmorMode mode = parseArmorMode(a, 4);
        if (a.length <= mode.nextIndex()) {
            throw new IllegalArgumentException("Select at least one weapon/tool after the armor mode.");
        }
        List<String> weapons = Arrays.asList(a).subList(mode.nextIndex(), a.length);
        kits.validateWeaponArgs(mode.value(), weapons);
        List<String> selectedNames = targets.resolveNames(sender, a[3]);
        int applied = 0, queued = 0;
        List<String> noSpace = new ArrayList<>();
        for (String name : selectedNames) {
            Player player = Bukkit.getPlayerExact(name);
            if (player != null && player.isOnline()) {
                if (kits.giveWeapons(player, mode.value(), weapons)) applied++;
                else noSpace.add(name);
            } else {
                pending.queueWeaponSet(name, mode.value(), weapons);
                queued++;
            }
        }
        msg(sender, "&aWeapon set applied to " + applied + " online player(s); queued for " + queued + " offline profile(s).");
        if (!noSpace.isEmpty()) msg(sender, "&eNot enough inventory space; left unchanged: &f" + String.join(", ", noSpace));
    }

    private void armorRemove(CommandSender sender, String[] a) {
        if (a.length != 3) {
            hint(sender, "Select ★ Team / Player / selector", "/uop armor remove <target>");
            return;
        }
        List<String> selectedNames = targets.resolveNames(sender, a[2]);
        int removed = 0, queued = 0;
        List<String> noSpace = new ArrayList<>();
        for (String name : selectedNames) {
            Player player = Bukkit.getPlayerExact(name);
            if (player != null && player.isOnline()) {
                if (kits.removeArmor(player)) removed++;
                else noSpace.add(name);
            } else {
                pending.queueArmorRemove(name);
                queued++;
            }
        }
        msg(sender, "&aArmor removed for " + removed + " online player(s); queued for " + queued + " offline profile(s).");
        if (!noSpace.isEmpty()) msg(sender, "&eInventory is full; armor left unchanged: &f" + String.join(", ", noSpace));
    }

    private void armorGive(CommandSender sender, String[] a) {
        if (a.length < 3) {
            hint(sender, "Choose ★ Direct, Saved or Random", "/uop armor give <direct|saved|random> ...");
            return;
        }
        String mode = a[2].toLowerCase(Locale.ROOT);
        if (mode.equals("direct")) {
            if (a.length != 7 || !a[4].equalsIgnoreCase("enchanted")) {
                hint(sender, "Choose material, enchanted preset level 1-5, then a target",
                        "/uop armor give direct <iron|gold|diamond|netherite|mixed> enchanted <1-5> <target>");
                return;
            }
            int level = integer(a[5], 1, 5, "Enchantment preset level");
            if (!kits.materials().contains(a[3].toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Material must be iron, gold, diamond, netherite or mixed.");
            }
            List<String> names = targets.resolveNames(sender, a[6]);
            int success = 0, queued = 0;
            List<String> skipped = new ArrayList<>();
            for (String name : names) {
                Player player = Bukkit.getPlayerExact(name);
                if (player != null && player.isOnline()) {
                    if (kits.giveDirect(player, a[3], level)) success++;
                    else skipped.add(name + " (inventory full)");
                } else {
                    pending.queueDirectKit(name, a[3], level);
                    queued++;
                }
            }
            msg(sender, "&aDirect kit applied to " + success + " online player(s); queued for " + queued + " offline profile(s).");
            if (!skipped.isEmpty()) msg(sender, "&eNo inventory space; left unchanged: &f" + String.join(", ", skipped));
            return;
        }
        if (mode.equals("saved") || mode.equals("random")) {
            if (a.length != 5) {
                hint(sender, "Choose ★ Team / Player / selector, then a saved loadout",
                        "/uop armor give <saved|random> <target> <set_name|set1,set2,...|all>");
                return;
            }
            List<String> candidates = mode.equals("random") && !a[4].equalsIgnoreCase("all")
                    ? Arrays.stream(a[4].split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                    : List.of();
            List<String> names = targets.resolveNames(sender, a[3]);
            int success = 0, queued = 0;
            List<String> failed = new ArrayList<>();
            if (mode.equals("saved") && !loadouts.exists(a[4])) throw new IllegalArgumentException("Loadout not found: " + a[4]);
            for (String name : names) {
                Player player = Bukkit.getPlayerExact(name);
                if (mode.equals("saved")) {
                    if (player != null && player.isOnline()) {
                        if (loadouts.give(a[4], player)) success++;
                        else failed.add(name);
                    } else {
                        pending.queueLoadout(name, a[4]);
                        queued++;
                    }
                } else {
                    String chosen = loadouts.chooseRandomName(candidates);
                    if (chosen == null) {
                        failed.add(name);
                    } else if (player != null && player.isOnline()) {
                        if (loadouts.give(chosen, player)) success++;
                        else failed.add(name);
                    } else {
                        pending.queueLoadout(name, chosen);
                        queued++;
                    }
                }
            }
            msg(sender, "&aLoadout applied to " + success + " online player(s); queued for " + queued + " offline profile(s).");
            if (!failed.isEmpty()) msg(sender, "&eNo matching saved loadout for: &f" + String.join(", ", failed));
            return;
        }
        hint(sender, "Choose ★ Direct, Saved or Random", "/uop armor give <direct|saved|random> ...");
    }

    private void checkpoint(CommandSender sender, String[] a) {
        require(sender, "uop.checkpoint");
        if (a.length < 2) {
            hint(sender, "Choose checkpoint action", "/uop checkpoint <save|tp|back|list|delete|clear>");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "list" -> msg(sender, "&fSaved checkpoints: &e" + String.join(", ", checkpoints.list()));
            case "save" -> {
                if (a.length != 4) {
                    hint(sender, "Enter checkpoint name, then a target", "/uop checkpoint save <checkpoint_name> <target>");
                    return;
                }
                List<String> targetNames = targets.resolveNames(sender, a[3]);
                List<Player> selected = new ArrayList<>();
                List<String> offline = new ArrayList<>();
                for (String name : targetNames) {
                    Player player = Bukkit.getPlayerExact(name);
                    if (player != null && player.isOnline()) selected.add(player);
                    else offline.add(name);
                }
                if (selected.isEmpty()) throw new IllegalArgumentException("No selected target is online; a checkpoint snapshot can only be captured from a live player state.");
                int saved = checkpoints.save(a[2], selected);
                msg(sender, "&aSaved independent full-state snapshots for " + saved + " player(s).");
                if (!offline.isEmpty()) msg(sender, "&eOffline members were not captured: &f" + String.join(", ", offline));
            }
            case "tp", "back" -> {
                if (a.length != 4) {
                    hint(sender, "Select saved checkpoint, then target", "/uop checkpoint tp <checkpoint_name> <target>");
                    return;
                }
                List<String> names = targets.resolveNames(sender, a[3]);
                int restored = 0, queued = 0, missing = 0;
                for (String name : names) {
                    Player player = Bukkit.getPlayerExact(name);
                    if (player != null && player.isOnline()) {
                        restored += a[1].equalsIgnoreCase("back")
                                ? checkpoints.back(a[2], List.of(player))
                                : checkpoints.restore(a[2], List.of(player));
                    } else if (!a[1].equalsIgnoreCase("back") && checkpoints.hasSnapshot(a[2], name)) {
                        pending.queueCheckpointRestore(name, a[2]);
                        queued++;
                    } else {
                        missing++;
                    }
                }
                if (restored > 0 || queued > 0) {
                    msg(sender, "&a" + (a[1].equalsIgnoreCase("back") ? "Returned " : "Restored ")
                            + restored + " online player(s)" + (queued > 0 ? "; queued for " + queued + " offline profile(s)." : "."));
                }
                if (missing > 0) msg(sender, "&eNo saved checkpoint/return position for " + missing + " target(s).");
                if (restored == 0 && queued == 0 && missing == 0) {
                    msg(sender, "&eNo selected player could be restored; no location or snapshot was changed.");
                }
            }
            case "delete" -> {
                if (a.length != 3) {
                    hint(sender, "Select checkpoint name", "/uop checkpoint delete <checkpoint_name>");
                    return;
                }
                if (!checkpoints.delete(a[2])) throw new IllegalArgumentException("Checkpoint not found: " + a[2]);
                msg(sender, "&aCheckpoint deleted.");
            }
            case "clear" -> {
                if (a.length != 4 || !a[2].equalsIgnoreCase("all") || !a[3].equalsIgnoreCase("confirm")) {
                    hint(sender, "Confirm destructive checkpoint removal",
                            "/uop checkpoint clear all confirm");
                    return;
                }
                checkpoints.clear();
                msg(sender, "&aAll checkpoints cleared.");
            }
            default -> hint(sender, "Choose checkpoint action", "/uop checkpoint <save|tp|back|list|delete|clear>");
        }
    }

    private void damage(CommandSender sender, String[] a) {
        require(sender, "uop.damage");
        if (a.length < 2) {
            hint(sender, "Choose damage rule", "/uop damage <global|outgoing|incoming|set|reset|list>");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "global" -> {
                if (a.length != 3) {
                    hint(sender, "Multiplier range 0.0-10.0", "/uop damage global <multiplier>");
                    return;
                }
                damage.global(decimal(a[2], 0.0, 10.0));
                msg(sender, "&aGlobal damage multiplier: &f" + damage.global());
            }
            case "outgoing", "incoming" -> {
                if (a.length != 4) {
                    hint(sender, "Select target, then multiplier 0.0-10.0", "/uop damage " + a[1] + " <target> <multiplier>");
                    return;
                }
                damage.scoped(sender, a[1].toLowerCase(Locale.ROOT), a[2], decimal(a[3], 0.0, 10.0));
                msg(sender, "&a" + a[1] + " damage rule updated for &f" + a[2]);
            }
            case "set", "pair" -> {
                if (a.length != 5) {
                    hint(sender, "Select attacker target, victim target, then multiplier", "/uop damage set <attacker_target> <victim_target> <multiplier>");
                    return;
                }
                damage.pair(sender, a[2], a[3], decimal(a[4], 0.0, 10.0));
                msg(sender, "&aExact attacker → victim damage rule saved.");
            }
            case "reset" -> {
                if (a.length < 3) {
                    hint(sender, "Choose rule to reset", "/uop damage reset <global|outgoing|incoming|pair|all> ...");
                    return;
                }
                switch (a[2].toLowerCase(Locale.ROOT)) {
                    case "global" -> damage.resetGlobal();
                    case "outgoing", "incoming" -> {
                        if (a.length != 4) throw new IllegalArgumentException("Usage: /uop damage reset " + a[2] + " <target>");
                        damage.resetScope(a[2].toLowerCase(Locale.ROOT), sender, a[3]);
                    }
                    case "pair" -> {
                        if (a.length != 5) throw new IllegalArgumentException("Usage: /uop damage reset pair <attacker_target> <victim_target>");
                        damage.resetPair(sender, a[3], a[4]);
                    }
                    case "all" -> {
                        if (a.length != 4 || !a[3].equalsIgnoreCase("confirm")) {
                            hint(sender, "Confirm removal of all damage rules",
                                    "/uop damage reset all confirm");
                            return;
                        }
                        damage.resetAll();
                    }
                    default -> throw new IllegalArgumentException("Unknown damage reset scope.");
                }
                msg(sender, "&aDamage rule reset.");
            }
            case "list" -> {
                for (String rule : damage.describeRules()) msg(sender, "&f" + rule);
            }
            default -> hint(sender, "Choose damage rule", "/uop damage <global|outgoing|incoming|set|reset|list>");
        }
    }

    private void enchant(CommandSender sender, String[] a, boolean remove) {
        require(sender, remove ? "uop.enchant" : "uop.enchant");
        if (!remove) {
            if (a.length != 5) {
                hint(sender, "Select scope, target, enchantment and valid level", "/uop enchant <all|armor|equipment|mainhand|offhand|inventory> <target> <enchantment> <level>");
                return;
            }
            int level = integer(a[4], 1, 255, "Enchantment level");
            enchants.validateApply(a[1], a[3], level);
            List<String> names = targets.resolveNames(sender, a[2]);
            int changed = 0, queued = 0;
            for (String name : names) {
                Player player = Bukkit.getPlayerExact(name);
                if (player != null && player.isOnline()) {
                    changed += enchants.apply(List.of(player), a[1], a[3], level);
                } else {
                    pending.queueEnchant(name, a[1], a[3], level);
                    queued++;
                }
            }
            msg(sender, "&aEnchantment applied to " + changed + " online item(s); queued for " + queued + " offline profile(s).");
        } else {
            if (a.length != 4 && a.length != 5) {
                hint(sender, "Select scope, target, optional slot and enchantment", "/uop disenchant <all|armor|equipment|hand|inventory> <target> [slot] <enchantment>");
                return;
            }
            List<String> names = targets.resolveNames(sender, a[2]);
            String slot = a.length == 5 ? a[3] : null;
            String enchantment = a.length == 5 ? a[4] : a[3];
            enchants.validateRemove(a[1], slot, enchantment);
            int changed = 0, queued = 0;
            for (String name : names) {
                Player player = Bukkit.getPlayerExact(name);
                if (player != null && player.isOnline()) {
                    changed += enchants.remove(List.of(player), a[1], slot, enchantment);
                } else {
                    pending.queueDisenchant(name, a[1], slot, enchantment);
                    queued++;
                }
            }
            msg(sender, "&aDisenchantment removed from " + changed + " online item(s); queued for " + queued + " offline profile(s).");
        }
    }

    private void team(CommandSender sender, String[] a) {
        require(sender, "uop.team");
        if (a.length < 2) {
            hint(sender, "Choose team action", "/uop team <create|delete|list|info|add|remove|removeall|tp|settings>");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (a.length != 3) throw new IllegalArgumentException("Usage: /uop team create <team_name>");
                teams.create(a[2]);
                msg(sender, "&aCreated team &f" + a[2]);
            }
            case "delete" -> {
                if (a.length != 3) throw new IllegalArgumentException("Usage: /uop team delete <team_name>");
                String teamName = TargetResolver.clean(a[2]);
                teams.requireTeam(teamName);
                plugin.store().createBackup("team-delete");
                damage.removeTeamRules(teamName);
                teams.delete(teamName);
                msg(sender, "&aDeleted team. Player profiles were retained.");
            }
            case "list" -> msg(sender, "&fTeams: &e" + String.join(", ", teams.list().stream().map(teams::displayName).toList()));
            case "info" -> {
                if (a.length != 3) throw new IllegalArgumentException("Usage: /uop team info <team_name>");
                teams.requireTeam(TargetResolver.clean(a[2]));
                List<String> members = teams.members(TargetResolver.clean(a[2]));
                List<String> online = teams.onlineMembers(TargetResolver.clean(a[2])).stream().map(Player::getName).toList();
                msg(sender, "&f" + teams.displayName(TargetResolver.clean(a[2])) + " &7members (" + members.size() + "): &e" + String.join(", ", members));
                msg(sender, "&7Online: &a" + String.join(", ", online) + " &8| Friendly fire: " + teams.friendlyFire(TargetResolver.clean(a[2])));
            }
            case "add" -> {
                if (a.length < 4) throw new IllegalArgumentException("Usage: /uop team add <team_name> <player|selector>...");
                java.util.LinkedHashSet<String> resolved = new java.util.LinkedHashSet<>();
                for (int i = 3; i < a.length; i++) resolved.addAll(targets.resolveNames(sender, a[i]));
                List<String> names = new ArrayList<>(resolved);
                teams.requireTeam(TargetResolver.clean(a[2]));
                int newMembers = (int) names.stream()
                        .filter(name -> !teams.contains(TargetResolver.clean(a[2]), name)).count();
                teams.addAll(TargetResolver.clean(a[2]), names);
                msg(sender, "&aAdded " + newMembers + " new member(s) to &f" + a[2] + ".");
                if (newMembers < names.size()) msg(sender, "&7" + (names.size() - newMembers)
                        + " selected player(s) were already members.");
            }
            case "remove" -> {
                if (a.length != 4) throw new IllegalArgumentException("Usage: /uop team remove <team_name> <player>");
                teams.remove(TargetResolver.clean(a[2]), TargetResolver.clean(a[3]));
                msg(sender, "&aMember removed. Personal profile retained.");
            }
            case "removeall" -> {
                if (a.length != 3) throw new IllegalArgumentException("Usage: /uop team removeall <team_name>");
                teams.removeAll(TargetResolver.clean(a[2]));
                msg(sender, "&aAll members removed. Personal profiles retained.");
            }
            case "tp" -> {
                if (a.length != 4) throw new IllegalArgumentException("Usage: /uop team tp <team_name> <destination_player>");
                List<Player> destination = targets.resolve(sender, a[3]);
                if (destination.size() != 1) throw new IllegalArgumentException("Destination must resolve to exactly one player.");
                int count = 0;
                for (Player member : teams.onlineMembers(TargetResolver.clean(a[2]))) {
                    if (member.equals(destination.get(0))) continue;
                    if (member.teleport(destination.get(0).getLocation())) count++;
                }
                msg(sender, "&aTeleported " + count + " online team member(s).");
            }
            case "settings" -> {
                if (a.length != 5 || !a[2].equalsIgnoreCase("friendlyfire")) {
                    throw new IllegalArgumentException("Usage: /uop team settings friendlyfire <team_name> <on|off>");
                }
                boolean enabled = bool(a[4]);
                teams.friendlyFire(TargetResolver.clean(a[3]), enabled);
                msg(sender, "&aFriendly fire for &f" + a[3] + " &7is now " + (enabled ? "&aON" : "&cOFF"));
            }
            default -> hint(sender, "Choose team action", "/uop team <create|delete|list|info|add|remove|removeall|tp|settings>");
        }
    }

    private void freeze(CommandSender sender, String[] a, boolean unfreeze) {
        require(sender, "uop.freeze");
        if (unfreeze) {
            if (a.length != 2) {
                hint(sender, "Select ★ Team / Player / selector", "/uop unfreeze <target>");
                return;
            }
            List<String> names = targets.resolveNames(sender, a[1]);
            for (String name : names) states.unfreeze(name);
            msg(sender, "&aUnfroze " + names.size() + " profile(s).");
            return;
        }
        if (a.length != 3 || !(a[1].equalsIgnoreCase("normal") || a[1].equalsIgnoreCase("full"))) {
            hint(sender, "Select normal/full mode, then target", "/uop freeze <normal|full> <target>");
            return;
        }
        List<String> names = targets.resolveNames(sender, a[2]);
        List<String> exemptOnline = new ArrayList<>();
        for (String name : names) {
            states.freeze(name, a[1].toLowerCase(Locale.ROOT));
            Player online = Bukkit.getPlayerExact(name);
            if (online != null && states.exempt(online)) exemptOnline.add(name);
        }
        msg(sender, "&a" + a[1] + " freeze saved for " + names.size() + " profile(s).");
        if (!exemptOnline.isEmpty()) {
            msg(sender, "&eFreeze is not active for exempt online player(s): &f" + String.join(", ", exemptOnline));
            msg(sender, "&7Set freeze.exempt-ops=false or remove the configured exemption, then run /uop reload.");
        }
    }

    private void immortal(CommandSender sender, String[] a) {
        require(sender, "uop.immortal");
        if (a.length < 2) {
            hint(sender, "Choose on/off/status/list", "/uop immortal <on|off|status|list> [target]");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "on", "off" -> {
                if (a.length != 3) throw new IllegalArgumentException("Usage: /uop immortal <on|off> <target>");
                List<String> names = targets.resolveNames(sender, a[2]);
                boolean enabled = a[1].equalsIgnoreCase("on");
                for (String name : names) states.immortal(name, enabled);
                msg(sender, "&aImmortal " + (enabled ? "enabled" : "disabled") + " for " + names.size() + " profile(s).");
            }
            case "status" -> {
                if (a.length != 3) throw new IllegalArgumentException("Usage: /uop immortal status <target>");
                List<Player> selected = targets.resolve(sender, a[2]);
                for (Player player : selected) msg(sender, "&f" + player.getName() + ": " + (states.immortal(player) ? "&aON" : "&cOFF"));
            }
            case "list" -> {
                List<String> enabled = Bukkit.getOnlinePlayers().stream().filter(states::immortal).map(Player::getName).toList();
                msg(sender, "&fOnline immortal players: &e" + String.join(", ", enabled));
            }
            default -> hint(sender, "Choose on/off/status/list", "/uop immortal <on|off|status|list> [target]");
        }
    }

    private void inventory(CommandSender sender, String[] a) {
        require(sender, "uop.inv");
        if (a.length < 2) {
            hint(sender, "Choose inventory operation",
                    "/uop inv <view|set|remove|give|take|clear|swap|backup|restore|backups|backup-delete|record|back|lock|unlock>");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "view" -> {
                if (a.length != 3) {
                    hint(sender, "Select ★ Team / Player / selector", "/uop inv view <target>");
                    return;
                }
                List<Player> selected = targets.resolve(sender, a[2]);
                for (Player player : selected) {
                    for (String line : inventory.describe(player)) msg(sender, "&f" + line);
                }
            }
            case "set" -> {
                if (a.length != 5 && a.length != 6) {
                    hint(sender, "Select target, slot 0-40, item and optional amount", "/uop inv set <target> <slot> <item> [amount]");
                    return;
                }
                int slot = integer(a[3], 0, 40, "Inventory slot");
                Material material = Material.matchMaterial(a[4]);
                if (material == null || material.isAir()) throw new IllegalArgumentException("Unknown item: " + a[4]);
                int amount = a.length == 6 ? integer(a[5], 1, 99, "Amount") : 1;
                List<Player> selected = targets.resolve(sender, a[2]);
                for (Player player : selected) inventory.set(player, slot, material, amount);
                msg(sender, "&aSet slot " + slot + " for " + selected.size() + " player(s).");
            }
            case "remove" -> {
                if (a.length != 4) {
                    hint(sender, "Select target and inventory slot", "/uop inv remove <target> <slot>");
                    return;
                }
                int slot = integer(a[3], 0, 40, "Inventory slot");
                List<Player> selected = targets.resolve(sender, a[2]);
                for (Player player : selected) inventory.remove(player, slot);
                msg(sender, "&aRemoved slot " + slot + " for " + selected.size() + " player(s).");
            }
            case "give", "take" -> {
                if (a.length != 4 && a.length != 5) {
                    hint(sender, "Select target, item and optional amount", "/uop inv " + a[1] + " <target> <item> [amount]");
                    return;
                }
                Material material = Material.matchMaterial(a[3]);
                if (material == null || material.isAir()) throw new IllegalArgumentException("Unknown item: " + a[3]);
                int amount = a.length == 5 ? integer(a[4], 1, 99, "Amount") : 1;
                List<Player> selected = targets.resolve(sender, a[2]);
                int affected = 0, leftovers = 0, inserted = 0;
                for (Player player : selected) {
                    if (a[1].equalsIgnoreCase("give")) {
                        int remaining = inventory.give(player, material, amount);
                        leftovers += remaining;
                        int received = amount - remaining;
                        if (received > 0) affected++;
                        inserted += received;
                    } else {
                        affected += inventory.take(player, material, amount);
                    }
                }
                if (a[1].equalsIgnoreCase("give")) {
                    if (inserted == 0) {
                        msg(sender, "&eNo items were inserted; selected inventories may be full.");
                    } else {
                        msg(sender, "&aInserted " + inserted + " item(s) into " + affected
                                + " player inventory(s). Left over due to capacity: " + leftovers + ".");
                    }
                } else {
                    if (affected == 0) msg(sender, "&eNo matching items were found; no inventory items were removed.");
                    else msg(sender, "&aRemoved " + affected + " item(s) in total.");
                }
            }
            case "clear" -> {
                if (a.length != 3) {
                    hint(sender, "Select ★ Team / Player / selector", "/uop inv clear <target>");
                    return;
                }
                List<Player> selected = targets.resolve(sender, a[2]);
                for (Player player : selected) inventory.clear(player);
                msg(sender, "&aCleared inventories for " + selected.size() + " player(s).");
            }
            case "swap" -> {
                if (a.length < 3) {
                    hint(sender, "Choose Player-to-Player or Team-to-Team swap", "/uop inv swap <player|team> ...");
                    return;
                }
                if (a[2].equalsIgnoreCase("player")) {
                    if (a.length != 5) throw new IllegalArgumentException("Usage: /uop inv swap player <player1> <player2>");
                    List<Player> first = targets.resolve(sender, a[3]);
                    List<Player> second = targets.resolve(sender, a[4]);
                    if (first.size() != 1 || second.size() != 1) throw new IllegalArgumentException("Each swap side must resolve to exactly one Player.");
                    inventory.swap(first.get(0), second.get(0));
                    msg(sender, "&aComplete inventories swapped.");
                } else if (a[2].equalsIgnoreCase("team")) {
                    if (a.length != 5) throw new IllegalArgumentException("Usage: /uop inv swap team <team1> <team2>");
                    int count = inventory.swapTeams(TargetResolver.clean(a[3]), TargetResolver.clean(a[4]));
                    msg(sender, "&aInventories swapped across " + count + " deterministic Team pair(s).");
                } else {
                    hint(sender, "Choose player or team", "/uop inv swap <player|team> ...");
                }
            }
            case "backup" -> {
                if (a.length != 4) {
                    hint(sender, "Select online target and backup name", "/uop inv backup <target> <backup_name>");
                    return;
                }
                List<Player> selected = targets.resolve(sender, a[2]);
                for (Player player : selected) inventory.backup(player, a[3]);
                msg(sender, "&aCreated inventory backup for " + selected.size() + " player(s).");
            }
            case "restore" -> {
                if (a.length != 4) {
                    hint(sender, "Select target and saved backup name", "/uop inv restore <target> <backup_name>");
                    return;
                }
                List<Player> selected = targets.resolve(sender, a[2]);
                int restored = 0, missing = 0;
                for (Player player : selected) {
                    if (inventory.restore(player, a[3])) restored++;
                    else missing++;
                }
                if (restored > 0) msg(sender, "&aRestored backup for " + restored + " player(s).");
                if (missing > 0) msg(sender, "&eBackup '" + a[3] + "' was missing or invalid for "
                        + missing + " player(s); their inventory was not changed.");
            }
            case "backups" -> {
                if (a.length != 3) {
                    hint(sender, "Select Player or Team", "/uop inv backups <target>");
                    return;
                }
                List<String> names = targets.resolveNames(sender, a[2]);
                for (String name : names) msg(sender, "&f" + name + " backups: &e" + String.join(", ", inventory.backups(name)));
            }
            case "backup-delete" -> {
                if (a.length != 4) throw new IllegalArgumentException("Usage: /uop inv backup-delete <target> <backup_name>");
                List<String> names = targets.resolveNames(sender, a[2]);
                for (String name : names) inventory.deleteBackup(name, a[3]);
                msg(sender, "&aDeleted backup '" + a[3] + "' from " + names.size() + " profile(s).");
            }
            case "record" -> {
                if (a.length == 3 && a[2].equalsIgnoreCase("status")) {
                    msg(sender, "&fRecording inventory history: &e" + String.join(", ", inventory.recordingNames()));
                    return;
                }
                if (a.length != 4 || !(a[2].equalsIgnoreCase("start") || a[2].equalsIgnoreCase("stop"))) {
                    hint(sender, "Choose start/stop and a target, or record status",
                            "/uop inv record <start|stop> <target> | /uop inv record status");
                    return;
                }
                if (a[2].equalsIgnoreCase("start")) {
                    List<Player> selected = targets.resolve(sender, a[3]);
                    int started = inventory.startRecord(selected);
                    msg(sender, "&aInventory history recording started for " + started + " of "
                            + selected.size() + " selected online player(s).");
                } else {
                    List<String> selected = targets.resolveNames(sender, a[3]);
                    inventory.stopRecordNames(selected);
                    msg(sender, "&aInventory history recording stopped for " + selected.size() + " profile(s).");
                }
            }
            case "back" -> {
                if (a.length != 4) {
                    hint(sender, "Select target and look-back duration (e.g. 30s, 5m, 1h)", "/uop inv back <target> <time>");
                    return;
                }
                List<Player> selected = targets.resolve(sender, a[2]);
                int restored = 0;
                for (Player player : selected) if (inventory.back(player, a[3])) restored++;
                if (restored == 0) {
                    msg(sender, "&eNo snapshot was available at least " + a[3] + " ago for the selected player(s).");
                } else {
                    msg(sender, "&aRestored inventory history for " + restored + " player(s).");
                }
            }
            case "lock", "unlock" -> {
                if (a.length != 4) {
                    hint(sender, "Choose inventory/armor/drop lock and a target",
                            "/uop inv <lock|unlock> <inventory|armor|drop> <target>");
                    return;
                }
                String lock = a[2].toLowerCase(Locale.ROOT);
                if (!List.of("inventory", "armor", "drop").contains(lock)) {
                    throw new IllegalArgumentException("Lock type must be inventory, armor or drop.");
                }
                List<String> names = targets.resolveNames(sender, a[3]);
                boolean enabled = a[1].equalsIgnoreCase("lock");
                for (String name : names) profiles.lock(name, lock, enabled);
                msg(sender, "&a" + lock + " lock " + (enabled ? "enabled" : "disabled") + " for " + names.size() + " profile(s).");
            }
            default -> hint(sender, "Choose inventory operation",
                    "/uop inv <view|set|remove|give|take|clear|swap|backup|restore|backups|backup-delete|record|back|lock|unlock>");
        }
    }


    private void name(CommandSender sender, String[] a) {
        require(sender, "uop.name");
        if (a.length < 2) {
            hint(sender, "Choose name action", "/uop name <hide|show|tag|glitch>");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "hide", "show" -> {
                if (a.length != 3) {
                    hint(sender, "Select ★ Team / Player / selector", "/uop name " + a[1] + " <target>");
                    return;
                }
                List<String> selected = targets.resolveNames(sender, a[2]);
                for (String player : selected) {
                    if (a[1].equalsIgnoreCase("hide")) names.hide(player);
                    else names.show(player);
                }
                msg(sender, "&aName visibility updated for " + selected.size() + " profile(s).");
            }
            case "tag" -> nameTag(sender, a);
            case "glitch" -> {
                msg(sender, "&eName Glitch integration is not enabled.");
                msg(sender, "&7The optional compatibility mod/dependency has not yet been identified and verified.");
            }
            default -> hint(sender, "Choose name action", "/uop name <hide|show|tag|glitch>");
        }
    }

    private void nameTag(CommandSender sender, String[] a) {
        if (a.length < 3) {
            hint(sender, "Choose name-tag operation",
                    "/uop name tag <create|edit|list|give|change|remove|delete|help>");
            return;
        }
        switch (a[2].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                msg(sender, "&fName tag commands:");
                msg(sender, "&7/uop name tag create <id> <color> <bold> <italic> <text>");
                msg(sender, "&7/uop name tag edit <id> <color> <bold> <italic> <text>");
                msg(sender, "&7/uop name tag give <target> <id>");
                msg(sender, "&7/uop name tag remove <target> | delete <id>");
            }
            case "list" -> msg(sender, "&fSaved name tags: &e" + String.join(", ", names.tags()));
            case "create", "edit" -> {
                if (a.length < 8) {
                    hint(sender, "Enter ID, color, bold, italic and tag text",
                            "/uop name tag " + a[2] + " <tag_id> <color|#RRGGBB> <bold> <italic> <text>");
                    return;
                }
                String text = String.join(" ", Arrays.copyOfRange(a, 7, a.length));
                boolean bold = bool(a[5]);
                boolean italic = bool(a[6]);
                if (a[2].equalsIgnoreCase("create")) names.create(a[3], a[4], bold, italic, text);
                else names.edit(a[3], a[4], bold, italic, text);
                msg(sender, "&aName tag " + (a[2].equalsIgnoreCase("create") ? "created" : "updated") + ": &f" + a[3]);
            }
            case "give", "change" -> {
                if (a.length != 5) {
                    hint(sender, "Select ★ Team / Player / selector, then tag ID",
                            "/uop name tag " + a[2] + " <target> <tag_id>");
                    return;
                }
                List<String> selected = targets.resolveNames(sender, a[3]);
                for (String player : selected) names.give(player, a[4]);
                msg(sender, "&aName tag assigned to " + selected.size() + " profile(s).");
            }
            case "remove" -> {
                if (a.length != 4) {
                    hint(sender, "Select ★ Team / Player / selector", "/uop name tag remove <target>");
                    return;
                }
                List<String> selected = targets.resolveNames(sender, a[3]);
                for (String player : selected) names.removeTag(player);
                msg(sender, "&aName tags removed from " + selected.size() + " profile(s).");
            }
            case "delete" -> {
                if (a.length != 4) {
                    hint(sender, "Select saved tag ID", "/uop name tag delete <tag_id>");
                    return;
                }
                names.deleteTag(a[3]);
                msg(sender, "&aDeleted tag and cleared its assignments: &f" + a[3]);
            }
            default -> hint(sender, "Choose name-tag operation",
                    "/uop name tag <create|edit|list|give|change|remove|delete|help>");
        }
    }

    private void protect(CommandSender sender, String[] a) {
        require(sender, "uop.protect");
        if (a.length < 2) {
            hint(sender, "Choose identity-protection action", "/uop protect <add|remove|list|reset>");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (a.length != 3 && a.length != 4) {
                    hint(sender, "Select online target and optional UUID/IP mode",
                            "/uop protect add <target> [uuid|ip|both]");
                    return;
                }
                String mode = a.length == 4 ? a[3]
                        : plugin.getConfig().getString("protection.default-mode", "uuid");
                if (!List.of("uuid", "ip", "both").contains(mode.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Protection mode must be uuid, ip or both.");
                }
                List<Player> selected = targets.resolve(sender, a[2]);
                for (Player player : selected) protection.add(player, mode);
                msg(sender, "&aProtected identity for " + selected.size() + " online player(s) using " + mode.toUpperCase(Locale.ROOT) + ".");
            }
            case "remove" -> {
                if (a.length != 3) {
                    hint(sender, "Select Player / Team / selector", "/uop protect remove <target>");
                    return;
                }
                List<String> selected = targets.resolveNames(sender, a[2]);
                for (String player : selected) protection.remove(player);
                msg(sender, "&aRemoved identity protection for " + selected.size() + " profile(s).");
            }
            case "list" -> msg(sender, "&fProtected identities: &e" + String.join(", ", protection.list()));
            case "reset" -> {
                if (a.length != 3 || !a[2].equalsIgnoreCase("confirm")) {
                    hint(sender, "Confirm removal of every protected identity",
                            "/uop protect reset confirm");
                    return;
                }
                protection.reset();
                msg(sender, "&aAll protected identity entries cleared.");
            }
            default -> hint(sender, "Choose identity-protection action", "/uop protect <add|remove|list|reset>");
        }
    }

    private void permission(CommandSender sender, String[] a) {
        require(sender, "uop.permission");
        if (a.length < 2) {
            hint(sender, "Choose permission operation", "/uop permission <grant|revoke|list|check|reset>");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "grant", "revoke" -> {
                if (a.length < 4) {
                    hint(sender, "Select Player / Team / selector and one or more permission nodes",
                            "/uop permission " + a[1] + " <target> <permission...>");
                    return;
                }
                List<String> selected = targets.resolveNames(sender, a[2]);
                List<String> nodes = Arrays.asList(Arrays.copyOfRange(a, 3, a.length));
                for (String player : selected) {
                    if (a[1].equalsIgnoreCase("grant")) permissions.grant(player, nodes);
                    else permissions.revoke(player, nodes);
                }
                msg(sender, "&a" + a[1] + " completed for " + selected.size() + " profile(s).");
            }
            case "list" -> {
                if (a.length != 3) throw new IllegalArgumentException("Usage: /uop permission list <target>");
                List<String> selected = targets.resolveNames(sender, a[2]);
                for (String player : selected) msg(sender, "&f" + player + " permissions: &e" + String.join(", ", permissions.list(player)));
            }
            case "check" -> {
                if (a.length != 4) throw new IllegalArgumentException("Usage: /uop permission check <target> <permission>");
                List<String> selected = targets.resolveNames(sender, a[2]);
                for (String player : selected) {
                    boolean allowed = permissions.check(player, a[3]);
                    msg(sender, "&f" + player + " → " + a[3] + ": " + (allowed ? "&aGRANTED" : "&cNOT GRANTED"));
                }
            }
            case "reset" -> {
                if (a.length == 4 && a[2].equalsIgnoreCase("all") && a[3].equalsIgnoreCase("confirm")) {
                    permissions.resetAll();
                    msg(sender, "&aAll UOP custom permission grants reset.");
                    return;
                }
                if (a.length == 3 && a[2].equalsIgnoreCase("all")) {
                    hint(sender, "Confirm removing every UOP custom permission grant",
                            "/uop permission reset all confirm");
                    return;
                }
                if (a.length != 3 && a.length != 4) {
                    hint(sender, "Select Player / Team / selector and optional permission node",
                            "/uop permission reset <target> [permission|all]");
                    return;
                }
                List<String> selected = targets.resolveNames(sender, a[2]);
                String node = a.length == 4 ? a[3] : "all";
                for (String player : selected) permissions.reset(player, node);
                msg(sender, "&aPermissions reset for " + selected.size() + " profile(s).");
            }
            default -> hint(sender, "Choose permission operation", "/uop permission <grant|revoke|list|check|reset>");
        }
    }

    private void pending(CommandSender sender, String[] a) {
        require(sender, "uop.admin");
        if (a.length < 3) {
            hint(sender, "Inspect or clear offline actions",
                    "/uop pending <list|clear> <player> [confirm]");
            return;
        }
        String action = a[1].toLowerCase(Locale.ROOT);
        String playerName = TargetResolver.clean(a[2]);
        TeamService.validatePlayerName(playerName);
        if (action.equals("list")) {
            int count = pending.pendingCount(playerName);
            msg(sender, "&fPending actions for &e" + playerName + "&f: &e" + count);
            for (String description : pending.pendingDescriptions(playerName)) msg(sender, "&7" + description);
            if (count == 0) msg(sender, "&7(none)");
            return;
        }
        if (action.equals("clear")) {
            if (a.length != 4 || !a[3].equalsIgnoreCase("confirm")) {
                hint(sender, "Confirm deletion of queued actions",
                        "/uop pending clear " + playerName + " confirm");
                return;
            }
            int removed = pending.pendingCount(playerName);
            pending.clearPending(playerName);
            msg(sender, "&aCleared " + removed + " pending action(s) for &f" + playerName);
            return;
        }
        throw new IllegalArgumentException("Pending action must be list or clear.");
    }

    private void reset(CommandSender sender, String[] a) {
        require(sender, "uop.admin");
        if (a.length != 3 || !a[1].equalsIgnoreCase("all") || !a[2].equalsIgnoreCase("confirm")) {
            hint(sender, "Confirm destructive UOP data reset", "/uop reset all confirm");
            return;
        }
        plugin.resetAllUopData();
        msg(sender, "&aUOP state reset. Server-managed player inventories were not edited.");
    }

    private int integer(String value, int min, int max, String label) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        }
    }

    private double decimal(String value, double min, double max) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Value must be between " + min + " and " + max + ".");
        }
    }

    private boolean bool(String value) {
        if (value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("Value must be on/off.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] a) {
        try {
            if (a.length == 1) {
                List<String> allowed = ROOTS.stream().filter(root -> root.equals("help") || root.equals("version")
                        || rootAllowed(sender, root)).toList();
                return matching(allowed, a[0]);
            }
            String root = a[0].toLowerCase(Locale.ROOT);
            if (root.equals("help") && a.length == 2) return matching(List.of("team", "armor", "checkpoint", "damage",
                    "enchant", "disenchant", "freeze", "unfreeze", "immortal", "inv", "name", "protect", "permission", "admin", "pending"), a[1]);
            if (root.equals("team")) return teamTabs(a);
            if (root.equals("armor")) return armorTabs(a);
            if (root.equals("checkpoint")) return checkpointTabs(a);
            if (root.equals("damage")) return damageTabs(a);
            if (root.equals("enchant")) return enchantTabs(a, false);
            if (root.equals("disenchant")) return enchantTabs(a, true);
            if (root.equals("freeze")) {
                if (a.length == 2) return matching(List.of("normal", "full"), a[1]);
                if (a.length == 3) return targets.suggestions(a[2]);
            }
            if (root.equals("unfreeze") && a.length == 2) return targets.suggestions(a[1]);
            if (root.equals("immortal")) {
                if (a.length == 2) return matching(List.of("on", "off", "status", "list"), a[1]);
                if (a.length == 3 && List.of("on", "off", "status").contains(a[1].toLowerCase(Locale.ROOT))) {
                    return targets.suggestions(a[2]);
                }
            }
            if (root.equals("inv")) return inventoryTabs(sender, a);
            if (root.equals("pending")) return pendingTabs(a);
            if (root.equals("name")) return nameTabs(a);
            if (root.equals("protect")) return protectTabs(a);
            if (root.equals("permission")) return permissionTabs(a);
            if (root.equals("reset") && a.length == 2) return matching(List.of("all"), a[1]);
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
    }


    private boolean rootAllowed(CommandSender sender, String root) {
        if (root.equals("pending")) return admin(sender) || sender.hasPermission("uop.admin")
                || sender instanceof Player player && permissions.has(player, "uop.admin");
        if (root.equals("permission")) return admin(sender) || sender.hasPermission("uop.permission")
                || sender instanceof Player player && permissions.has(player, "uop.permission");
        if (root.equals("reload") || root.equals("reset")) return admin(sender)
                || sender.hasPermission("uop.admin")
                || sender instanceof Player player && permissions.has(player, "uop.admin");
        String node = switch (root) {
            case "armor" -> "uop.armor";
            case "checkpoint" -> "uop.checkpoint";
            case "damage" -> "uop.damage";
            case "disenchant", "enchant" -> "uop.enchant";
            case "freeze", "unfreeze" -> "uop.freeze";
            case "immortal" -> "uop.immortal";
            case "team" -> "uop.team";
            case "inv" -> "uop.inv";
            case "name" -> "uop.name";
            case "protect" -> "uop.protect";
            default -> "";
        };
        return !node.isBlank() && (admin(sender) || sender.hasPermission(node)
                || sender instanceof Player player && permissions.has(player, node));
    }

    private List<String> pendingTabs(String[] a) {
        if (a.length == 2) return matching(List.of("list", "clear"), a[1]);
        if (a.length == 3 && List.of("list", "clear").contains(a[1].toLowerCase(Locale.ROOT))) {
            return playerNames(a[2]);
        }
        if (a.length == 4 && a[1].equalsIgnoreCase("clear")) return matching(List.of("confirm"), a[3]);
        return List.of();
    }

    private List<String> targetSuggestions(String partial) {
        if (partial == null) return targets.suggestions("");
        int square = 0, curly = 0, lastComma = -1;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < partial.length(); i++) {
            char ch = partial.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '"' || ch == '\'') quote = ch;
            else if (ch == '[') square++;
            else if (ch == ']') square = Math.max(0, square - 1);
            else if (ch == '{') curly++;
            else if (ch == '}') curly = Math.max(0, curly - 1);
            else if (ch == ',' && square == 0 && curly == 0) lastComma = i;
        }
        String listPrefix = lastComma < 0 ? "" : partial.substring(0, lastComma + 1);
        String token = lastComma < 0 ? partial : partial.substring(lastComma + 1);
        return targets.suggestions(token).stream().map(value -> listPrefix + value).toList();
    }

    private List<String> historyTimeSuggestions(String partial) {
        long configuredWindow = Math.max(1L, plugin.getConfig().getLong("history.window-seconds", 300L));
        long sampleTicks = Math.max(1L, plugin.getConfig().getLong("history.sample-ticks", 100L));
        long maxSnapshots = Math.max(1L, plugin.getConfig().getLong("history.max-snapshots", 600L));
        long snapshotCapacity = Math.max(1L, sampleTicks * maxSnapshots / 20L);
        long effectiveWindow = Math.min(configuredWindow, snapshotCapacity);
        Map<String, Long> options = new java.util.LinkedHashMap<>();
        options.put("10s", 10L);
        options.put("30s", 30L);
        options.put("1m", 60L);
        options.put("5m", 300L);
        options.put("10m", 600L);
        options.put("30m", 1800L);
        options.put("1h", 3600L);
        return options.entrySet().stream()
                .filter(entry -> entry.getValue() <= effectiveWindow)
                .map(Map.Entry::getKey)
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(
                        partial == null ? "" : partial.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<String> inventoryTabs(CommandSender sender, String[] a) {
        if (a.length == 2) return matching(List.of("view", "set", "remove", "give", "take", "clear",
                "swap", "backup", "restore", "backups", "backup-delete", "record", "back", "lock", "unlock"), a[1]);
        String action = a[1].toLowerCase(Locale.ROOT);
        if (List.of("view", "clear", "backups", "back").contains(action) && a.length == 3) return targets.suggestions(a[2]);
        if (action.equals("set")) {
            if (a.length == 3) return targets.suggestions(a[2]);
            if (a.length == 4) return matching(java.util.stream.IntStream.rangeClosed(0, 40).mapToObj(String::valueOf).toList(), a[3]);
            if (a.length == 5) return matching(java.util.Arrays.stream(Material.values()).filter(m -> !m.isAir() && m.isItem() && !m.isLegacy())
                    .map(m -> m.name().toLowerCase(Locale.ROOT)).toList(), a[4]);
            if (a.length == 6) return matching(List.of("1", "16", "32", "64"), a[5]);
        }
        if (action.equals("remove")) {
            if (a.length == 3) return targets.suggestions(a[2]);
            if (a.length == 4) return matching(java.util.stream.IntStream.rangeClosed(0, 40).mapToObj(String::valueOf).toList(), a[3]);
        }
        if (List.of("give", "take").contains(action)) {
            if (a.length == 3) return targets.suggestions(a[2]);
            if (a.length == 4) return matching(java.util.Arrays.stream(Material.values()).filter(m -> !m.isAir() && m.isItem() && !m.isLegacy())
                    .map(m -> m.name().toLowerCase(Locale.ROOT)).toList(), a[3]);
            if (a.length == 5) return matching(List.of("1", "16", "32", "64"), a[4]);
        }
        if (action.equals("swap")) {
            if (a.length == 3) return matching(List.of("player", "team"), a[2]);
            if (a.length == 4 && a[2].equalsIgnoreCase("player")) return targets.suggestions(a[3]);
            if (a.length == 5 && a[2].equalsIgnoreCase("player")) return targets.suggestions(a[4]);
            if (a.length == 4 && a[2].equalsIgnoreCase("team")) return teamNames(a[3]);
            if (a.length == 5 && a[2].equalsIgnoreCase("team")) return teamNames(a[4]);
        }
        if (List.of("backup", "restore", "backup-delete").contains(action)) {
            if (a.length == 3) return targets.suggestions(a[2]);
            if (a.length == 4 && !action.equals("backup")) {
                String target = TargetResolver.clean(a[3 - 1]);
                List<String> names = targets.resolveNames(sender, target);
                if (names.isEmpty()) return List.of();
                return matching(inventory.backups(names.get(0)), a[3]);
            }
        }
        if (action.equals("record")) {
            if (a.length == 3) return matching(List.of("start", "stop", "status"), a[2]);
            if (a.length == 4 && (a[2].equalsIgnoreCase("start") || a[2].equalsIgnoreCase("stop"))) return targetSuggestions(a[3]);
        }
        if (action.equals("lock") || action.equals("unlock")) {
            if (a.length == 3) return matching(List.of("inventory", "armor", "drop"), a[2]);
            if (a.length == 4) return targets.suggestions(a[3]);
        }
        if (action.equals("back") && a.length == 4) return historyTimeSuggestions(a[3]);
        return List.of();
    }


    private List<String> nameTabs(String[] a) {
        if (a.length == 2) return matching(List.of("hide", "show", "tag", "glitch"), a[1]);
        if (a[1].equalsIgnoreCase("hide") || a[1].equalsIgnoreCase("show") || a[1].equalsIgnoreCase("glitch")) {
            if (a.length == 3 && !a[1].equalsIgnoreCase("glitch")) return targets.suggestions(a[2]);
        }
        if (a[1].equalsIgnoreCase("tag")) {
            if (a.length == 3) return matching(List.of("help", "create", "edit", "list", "give", "change", "remove", "delete"), a[2]);
            String action = a[2].toLowerCase(Locale.ROOT);
            if (a.length == 4 && List.of("edit", "delete").contains(action)) return matching(names.tags(), a[3]);
            if (a.length == 4 && List.of("give", "change", "remove").contains(action)) return targets.suggestions(a[3]);
            if (a.length == 5 && List.of("give", "change").contains(action)) return matching(names.tags(), a[4]);
            if (a.length == 5 && List.of("create", "edit").contains(action)) return matching(List.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "#RRGGBB"), a[4]);
            if (a.length == 6 && List.of("create", "edit").contains(action)) return matching(List.of("true", "false", "on", "off"), a[5]);
            if (a.length == 7 && List.of("create", "edit").contains(action)) return matching(List.of("true", "false", "on", "off"), a[6]);
        }
        return List.of();
    }

    private List<String> protectTabs(String[] a) {
        if (a.length == 2) return matching(List.of("add", "remove", "list", "reset"), a[1]);
        String action = a[1].toLowerCase(Locale.ROOT);
        if (a.length == 3 && List.of("add", "remove").contains(action)) return targets.suggestions(a[2]);
        if (a.length == 3 && action.equals("reset")) return matching(List.of("confirm"), a[2]);
        if (a.length == 4 && action.equals("add")) return matching(List.of("uuid", "ip", "both"), a[3]);
        return List.of();
    }

    private List<String> permissionTabs(String[] a) {
        if (a.length == 2) return matching(List.of("grant", "revoke", "list", "check", "reset"), a[1]);
        String action = a[1].toLowerCase(Locale.ROOT);
        if (a.length == 3 && List.of("grant", "revoke", "list", "check", "reset").contains(action)) {
            if (action.equals("reset")) {
                List<String> options = new ArrayList<>();
                if ("all".startsWith(a[2].toLowerCase(Locale.ROOT))) options.add("all");
                options.addAll(targets.suggestions(a[2]));
                return options;
            }
            return targets.suggestions(a[2]);
        }
        if (a.length >= 4 && List.of("grant", "revoke").contains(action)) {
            return matching(List.of("uop.*", "uop.armor", "uop.checkpoint", "uop.damage", "uop.enchant",
                    "uop.freeze", "uop.immortal", "uop.team", "uop.inv", "uop.name", "uop.protect",
                    "uop.permission", "uop.admin", "all"), a[a.length - 1]);
        }
        if (a.length == 4 && action.equals("check")) return matching(List.of("uop.*", "uop.armor", "uop.checkpoint",
                "uop.damage", "uop.enchant", "uop.freeze", "uop.immortal", "uop.team", "uop.inv", "uop.name",
                "uop.protect", "uop.permission", "uop.admin"), a[3]);
        if (a.length == 4 && action.equals("reset") && a[2].equalsIgnoreCase("all")) {
            return matching(List.of("confirm"), a[3]);
        }
        if (a.length == 4 && action.equals("reset")) return matching(List.of("all", "uop.*", "uop.armor",
                "uop.checkpoint", "uop.damage", "uop.enchant", "uop.freeze", "uop.immortal", "uop.team",
                "uop.inv", "uop.name", "uop.protect", "uop.permission", "uop.admin"), a[3]);
        if (a.length == 5 && action.equals("reset") && a[3].equalsIgnoreCase("all")) {
            return matching(List.of("confirm"), a[4]);
        }
        return List.of();
    }

    private List<String> teamTabs(String[] a) {
        if (a.length == 2) return matching(List.of("create", "delete", "list", "info", "add", "remove", "removeall", "tp", "settings"), a[1]);
        if (a.length == 3 && List.of("delete", "info", "removeall").contains(a[1])) return teamNames(a[2]);
        if (a.length == 3 && a[1].equals("add")) return teamNames(a[2]);
        if (a.length == 3 && a[1].equalsIgnoreCase("tp")) return teamNames(a[2]);
        if (a.length == 4 && a[1].equalsIgnoreCase("tp")) return playerNames(a[3]);
        if (a.length >= 4 && a[1].equals("add")) return targets.suggestions(a[a.length - 1]);
        if (a.length == 4 && a[1].equals("remove")) return memberNames(a[2], a[3]);
        if (a.length == 3 && a[1].equals("settings")) return matching(List.of("friendlyfire"), a[2]);
        if (a.length == 4 && a[1].equals("settings")) return teamNames(a[3]);
        if (a.length == 5 && a[1].equals("settings")) return matching(List.of("on", "off"), a[4]);
        return List.of();
    }

    private List<String> armorTabs(String[] a) {
        if (a.length == 2) return matching(List.of("set", "save", "list", "delete", "give", "remove", "weapon"), a[1]);
        if (a.length == 3 && a[1].equals("give")) return matching(List.of("direct", "saved", "random"), a[2]);
        if (a.length == 3 && a[1].equals("remove")) return targets.suggestions(a[2]);
        if (a.length == 3 && a[1].equals("delete")) return loadoutNames(a[2]);
        if (a.length == 3 && a[1].equals("set")) return targets.suggestions(a[2]);
        if (a.length == 4 && a[1].equals("set")) return matching(kits.classicMaterials(), a[3]);
        if (a.length == 5 && a[1].equals("set")) return matching(kits.modes(), a[4]);
        if (a.length == 6 && a[1].equals("set") && a[4].equals("enchanted")) {
            return matching(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), a[5]);
        }
        if (a.length == 6 && a[1].equals("set") && !a[4].equals("enchanted")) {
            return matching(List.of("true", "false", "on", "off"), a[5]);
        }
        if (a.length == 7 && a[1].equals("set") && a[4].equals("enchanted")) {
            return matching(List.of("true", "false", "on", "off"), a[6]);
        }
        if (a.length >= 7 && a[1].equals("set") && !a[4].equals("enchanted")
                && List.of("true", "on").contains(a[5].toLowerCase(Locale.ROOT))) {
            return matching(kits.weaponNames(), a[a.length - 1]);
        }
        if (a.length >= 8 && a[1].equals("set") && a[4].equals("enchanted")
                && List.of("true", "on").contains(a[6].toLowerCase(Locale.ROOT))) {
            return matching(kits.weaponNames(), a[a.length - 1]);
        }
        if (a.length == 3 && a[1].equals("weapon")) return matching(List.of("set"), a[2]);
        if (a.length == 4 && a[1].equals("weapon") && a[2].equals("set")) return targets.suggestions(a[3]);
        if (a.length == 5 && a[1].equals("weapon") && a[2].equals("set")) return matching(kits.modes(), a[4]);
        if (a.length == 6 && a[1].equals("weapon") && a[2].equals("set") && a[4].equals("enchanted")) {
            return matching(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), a[5]);
        }
        if (a.length >= 6 && a[1].equals("weapon") && a[2].equals("set") && !a[4].equals("enchanted")) {
            return matching(kits.weaponNames(), a[a.length - 1]);
        }
        if (a.length >= 7 && a[1].equals("weapon") && a[2].equals("set") && a[4].equals("enchanted")) {
            return matching(kits.weaponNames(), a[a.length - 1]);
        }
        if (a.length == 4 && a[1].equals("save")) return playerNamesAndSingleSelectors(a[3]);
        if (a.length == 4 && a[1].equals("give") && a[2].equals("direct")) return matching(kits.materials(), a[3]);
        if (a.length == 5 && a[1].equals("give") && a[2].equals("direct")) return matching(List.of("enchanted"), a[4]);
        if (a.length == 6 && a[1].equals("give") && a[2].equals("direct")) return matching(List.of("1", "2", "3", "4", "5"), a[5]);
        if (a.length == 7 && a[1].equals("give") && a[2].equals("direct")) return targets.suggestions(a[6]);
        if (a.length == 4 && a[1].equals("give") && (a[2].equals("saved") || a[2].equals("random"))) return targets.suggestions(a[3]);
        if (a.length == 5 && a[1].equals("give") && a[2].equals("saved")) return loadoutNames(a[4]);
        if (a.length == 5 && a[1].equals("give") && a[2].equals("random")) {
            List<String> options = new ArrayList<>(loadoutNames(a[4]));
            options.add("all");
            return options;
        }
        return List.of();
    }
    private List<String> checkpointTabs(String[] a) {
        if (a.length == 2) return matching(List.of("save", "tp", "back", "list", "delete", "clear"), a[1]);
        if (a.length == 3 && List.of("tp", "back", "delete").contains(a[1])) return checkpointNames(a[2]);
        if (a.length == 4 && List.of("tp", "back", "save").contains(a[1])) return targets.suggestions(a[3]);
        if (a.length == 3 && a[1].equals("clear")) return matching(List.of("all"), a[2]);
        if (a.length == 4 && a[1].equals("clear") && a[2].equalsIgnoreCase("all")) return matching(List.of("confirm"), a[3]);
        return List.of();
    }

    private List<String> damageTabs(String[] a) {
        if (a.length == 2) return matching(List.of("global", "outgoing", "incoming", "set", "pair", "reset", "list"), a[1]);
        if (a.length == 3 && a[1].equals("global")) return matching(List.of("0", "0.25", "0.5", "1", "1.5", "2", "5", "10"), a[2]);
        if (a.length == 3 && List.of("outgoing", "incoming").contains(a[1])) return targets.suggestions(a[2]);
        if (a.length == 4 && List.of("outgoing", "incoming").contains(a[1])) return matching(List.of("0", "0.25", "0.5", "1", "1.5", "2", "5", "10"), a[3]);
        if (a.length == 3 && List.of("set", "pair").contains(a[1])) return targets.suggestions(a[2]);
        if (a.length == 4 && List.of("set", "pair").contains(a[1])) return targets.suggestions(a[3]);
        if (a.length == 5 && List.of("set", "pair").contains(a[1])) return matching(List.of("0", "0.25", "0.5", "1", "1.5", "2", "5", "10"), a[4]);
        if (a.length == 3 && a[1].equalsIgnoreCase("reset")) return matching(List.of("global", "outgoing", "incoming", "pair", "all"), a[2]);
        if (a.length == 4 && a[1].equalsIgnoreCase("reset") && a[2].equalsIgnoreCase("all"))
            return matching(List.of("confirm"), a[3]);
        if (a.length == 4 && a[1].equals("reset") && List.of("outgoing", "incoming").contains(a[2])) return targets.suggestions(a[3]);
        if (a.length == 4 && a[1].equals("reset") && a[2].equals("pair")) return targets.suggestions(a[3]);
        if (a.length == 5 && a[1].equals("reset") && a[2].equals("pair")) return targets.suggestions(a[4]);
        return List.of();
    }

    private List<String> enchantTabs(String[] a, boolean remove) {
        if (a.length == 2) return matching(remove
                ? List.of("all", "armor", "equipment", "hand", "inventory")
                : List.of("all", "armor", "equipment", "mainhand", "offhand", "inventory"), a[1]);
        if (a.length == 3) return targets.suggestions(a[2]);
        if (a.length == 4 && remove) {
            List<String> options = new ArrayList<>(enchantmentNames(a[3]));
            options.addAll(matching(List.of("mainhand", "offhand", "head", "helmet", "chest",
                    "chestplate", "legs", "leggings", "feet", "boots"), a[3]));
            return options;
        }
        if (a.length == 5 && remove) return enchantmentNames(a[4]);
        if (a.length == 4) return enchantmentNames(a[3]);
        if (a.length == 5) return enchantmentLevels(a[3], a[4]);
        return List.of();
    }

    private List<String> enchantmentNames(String partial) {
        List<String> values = new ArrayList<>();
        for (org.bukkit.enchantments.Enchantment enchantment : org.bukkit.Registry.ENCHANTMENT) {
            org.bukkit.NamespacedKey key = enchantment.getKey();
            values.add(key.getNamespace().equals("minecraft") ? key.getKey() : key.toString());
        }
        values.sort(String.CASE_INSENSITIVE_ORDER);
        return matching(values, partial);
    }

    private List<String> enchantmentLevels(String enchantmentName, String partial) {
        try {
            int max = enchants.resolve(enchantmentName).getMaxLevel();
            List<String> levels = new ArrayList<>();
            for (int level = 1; level <= max; level++) levels.add(Integer.toString(level));
            return matching(levels, partial);
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
    }

    private List<String> teamNames(String partial) {
        return teams.list().stream().map(teams::displayName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(TargetResolver.clean(partial).toLowerCase(Locale.ROOT))).map(n -> "★" + n).toList();
    }

    private List<String> memberNames(String team, String partial) {
        if (!teams.exists(TargetResolver.clean(team))) return List.of();
        String prefix = TargetResolver.clean(partial).toLowerCase(Locale.ROOT);
        return teams.members(TargetResolver.clean(team)).stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private List<String> playerNamesAndSingleSelectors(String partial) {
        List<String> values = new ArrayList<>(playerNames(partial));
        for (String selector : List.of("@p", "@r", "@s")) if (selector.startsWith(partial.toLowerCase(Locale.ROOT))) values.add(selector);
        return values;
    }

    private List<String> playerNames(String partial) {
        String prefix = partial.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private List<String> loadoutNames(String partial) {
        String prefix = partial.toLowerCase(Locale.ROOT);
        return loadouts.list().stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private List<String> checkpointNames(String partial) {
        String prefix = partial.toLowerCase(Locale.ROOT);
        return checkpoints.list().stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private List<String> matching(List<String> values, String partial) {
        String prefix = partial.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}