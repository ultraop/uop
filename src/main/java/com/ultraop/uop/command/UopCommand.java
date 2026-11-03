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
import java.util.Locale;

public final class UopCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOTS = List.of("armor", "checkpoint", "damage", "disenchant", "enchant",
            "freeze", "unfreeze", "immortal", "team", "inv", "name", "protect", "permission",
            "help", "reload", "reset", "version");
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

    public UopCommand(UopPlugin plugin, TeamService teams, TargetResolver targets,
                      ArmorKitService kits, ArmorLoadoutService loadouts,
                      CheckpointService checkpoints, DamageService damage,
                      EnchantService enchants, FreezeImmortalService states,
                      PlayerProfileService profiles, PendingActionService pending, InventoryService inventory) {
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
    }

    private boolean admin(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender instanceof Player player && player.isOp();
    }

    private void require(CommandSender sender, String permission) {
        if (!admin(sender) && !sender.hasPermission(permission)) {
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
                help(sender);
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
                case "help" -> help(sender);
                case "version" -> msg(sender, "&fUOP &7" + plugin.getDescription().getVersion() + " &8| &fPaper 1.21.11 &8| &fJava 21+");
                case "reload" -> {
                    require(sender, "uop.admin");
                    plugin.reloadUop();
                    msg(sender, "&aConfiguration and UOP data reloaded.");
                }
                case "reset" -> reset(sender, args);
                case "name", "protect", "permission" -> {
                    msg(sender, "&eThis command group is specified but not yet implemented in this build.");
                    msg(sender, "&7See docs/COMMANDS.md for the planned command tree.");
                }
                default -> {
                    msg(sender, "&cUnknown command. Use /uop help.");
                }
            }
        } catch (IllegalArgumentException ex) {
            msg(sender, "&c" + ex.getMessage());
        } catch (Exception ex) {
            plugin.getLogger().severe("Command failure: " + ex.getMessage());
            ex.printStackTrace();
            msg(sender, "&cOperation failed safely. Check the server console for details.");
        }
        return true;
    }

    private void help(CommandSender sender) {
        msg(sender, "&fUOP command groups:");
        msg(sender, "&e★ armor &7— saved loadouts and direct OP kits");
        msg(sender, "&e★ checkpoint &7— save/list/restore full player state");
        msg(sender, "&e★ damage &7— player/team damage rules");
        msg(sender, "&e★ enchant / disenchant &7— item enchantment management");
        msg(sender, "&e★ freeze / unfreeze / immortal &7— player/team state controls");
        msg(sender, "&e★ team &7— persistent teams and friendly fire");
        msg(sender, "&e★ inv &7— inventory locks");
        msg(sender, "&ehelp, reload, reset, version");
        msg(sender, "&7Targets: ★Team, @a, @p, @r, @s, PlayerName. Team suggestions are shown first.");
    }

    private void armor(CommandSender sender, String[] a) {
        require(sender, "uop.armor");
        if (a.length < 2) {
            hint(sender, "Select armor action", "/uop armor <give|save|list|delete>");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "list" -> msg(sender, "&fSaved loadouts: &e" + String.join(", ", loadouts.list()));
            case "save" -> {
                if (a.length != 4) {
                    hint(sender, "Enter a loadout name, then one player", "/uop armor save <set_name> <player>");
                    return;
                }
                if (teams.exists(TargetResolver.clean(a[3]))) throw new IllegalArgumentException("Saving a loadout requires a single Player, not a Team.");
                List<Player> selected = targets.resolve(sender, a[3]);
                if (selected.size() != 1) throw new IllegalArgumentException("Saving a loadout requires exactly one player.");
                loadouts.save(a[2], selected.get(0));
                msg(sender, "&aSaved complete armor + inventory loadout: &f" + a[2]);
            }
            case "delete" -> {
                if (a.length != 3) {
                    hint(sender, "Select a saved loadout", "/uop armor delete <set_name>");
                    return;
                }
                loadouts.delete(a[2]);
                msg(sender, "&aDeleted loadout: &f" + a[2]);
            }
            case "give" -> armorGive(sender, a);
            default -> {
                hint(sender, "Select an armor action", "/uop armor <give|save|list|delete>");
            }
        }
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
                List<Player> selected = targets.resolve(sender, a[3]);
                int saved = checkpoints.save(a[2], selected);
                msg(sender, "&aSaved independent full-state snapshots for " + saved + " player(s).");
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
                        restored += checkpoints.restore(a[2], List.of(player));
                    } else if (checkpoints.hasSnapshot(a[2], name)) {
                        pending.queueCheckpointRestore(name, a[2]);
                        queued++;
                    } else {
                        missing++;
                    }
                }
                msg(sender, "&aCheckpoint restored for " + restored + " online player(s); queued for " + queued + " offline profile(s).");
                if (missing > 0) msg(sender, "&eNo saved snapshot for " + missing + " target(s).");
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
                if (a.length != 3 || !a[2].equalsIgnoreCase("all")) {
                    hint(sender, "Type all to clear checkpoints", "/uop checkpoint clear all");
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
                    case "all" -> damage.resetAll();
                    default -> throw new IllegalArgumentException("Unknown damage reset scope.");
                }
                msg(sender, "&aDamage rule reset.");
            }
            case "list" -> {
                msg(sender, "&fGlobal multiplier: &e" + damage.global());
                msg(sender, "&7Detailed scoped rules are stored in plugins/UOP/data/uop.yml.");
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
            List<String> names = targets.resolveNames(sender, a[2]);
            int changed = 0, queued = 0;
            for (String name : names) {
                Player player = Bukkit.getPlayerExact(name);
                if (player != null && player.isOnline()) {
                    changed += enchants.apply(List.of(player), a[1], a[3], integer(a[4], 1, 255, "Enchantment level"));
                } else {
                    pending.queueEnchant(name, a[1], a[3], integer(a[4], 1, 255, "Enchantment level"));
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
                teams.delete(TargetResolver.clean(a[2]));
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
                List<String> names = new ArrayList<>();
                for (int i = 3; i < a.length; i++) names.addAll(targets.resolveNames(sender, a[i]));
                teams.addAll(TargetResolver.clean(a[2]), names);
                msg(sender, "&aAdded " + names.size() + " member(s) to &f" + a[2]);
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
        for (String name : names) states.freeze(name, a[1].toLowerCase(Locale.ROOT));
        msg(sender, "&a" + a[1] + " freeze stored for " + names.size() + " profile(s).");
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
                int affected = 0, leftovers = 0;
                for (Player player : selected) {
                    if (a[1].equalsIgnoreCase("give")) {
                        leftovers += inventory.give(player, material, amount);
                        affected++;
                    } else {
                        affected += inventory.take(player, material, amount);
                    }
                }
                if (a[1].equalsIgnoreCase("give")) {
                    msg(sender, "&aItem given to " + affected + " player(s). Leftover items because of full inventories: " + leftovers + ".");
                } else {
                    msg(sender, "&aRemoved " + affected + " item(s) in total.");
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
                int restored = 0;
                for (Player player : selected) if (inventory.restore(player, a[3])) restored++;
                msg(sender, "&aRestored backup for " + restored + " player(s).");
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
                List<Player> selected = targets.resolve(sender, a[3]);
                if (a[2].equalsIgnoreCase("start")) inventory.startRecord(selected);
                else inventory.stopRecord(selected);
                msg(sender, "&aInventory history recording " + (a[2].equalsIgnoreCase("start") ? "started" : "stopped")
                        + " for " + selected.size() + " player(s).");
            }
            case "back" -> {
                if (a.length != 4) {
                    hint(sender, "Select target and look-back duration (e.g. 30s, 5m, 1h)", "/uop inv back <target> <time>");
                    return;
                }
                List<Player> selected = targets.resolve(sender, a[2]);
                int restored = 0;
                for (Player player : selected) if (inventory.back(player, a[3])) restored++;
                msg(sender, "&aRestored inventory history for " + restored + " player(s).");
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

    private void reset(CommandSender sender, String[] a) {
        require(sender, "uop.admin");
        if (a.length != 2 || !a[1].equalsIgnoreCase("all")) {
            hint(sender, "Type all to reset UOP persistent state", "/uop reset all");
            return;
        }
        for (String key : new ArrayList<>(plugin.store().data().getKeys(false))) {
            if (!key.equals("schema-version")) plugin.store().data().set(key, null);
        }
        plugin.store().save();
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
            if (a.length == 1) return matching(ROOTS, a[0]);
            String root = a[0].toLowerCase(Locale.ROOT);
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
                if (a.length == 3) return targets.suggestions(a[2]);
            }
            if (root.equals("inv")) {
                if (a.length == 2) return matching(List.of("lock", "unlock"), a[1]);
                if (a.length == 3) return matching(List.of("inventory", "armor", "drop"), a[2]);
                if (a.length == 4) return targets.suggestions(a[3]);
            }
            if (root.equals("reset") && a.length == 2) return matching(List.of("all"), a[1]);
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
    }

    private List<String> teamTabs(String[] a) {
        if (a.length == 2) return matching(List.of("create", "delete", "list", "info", "add", "remove", "removeall", "tp", "settings"), a[1]);
        if (a.length == 3 && List.of("delete", "info", "removeall").contains(a[1])) return teamNames(a[2]);
        if (a.length == 3 && a[1].equals("add")) return teamNames(a[2]);
        if (a.length >= 4 && a[1].equals("add")) return targets.suggestions(a[a.length - 1]);
        if (a.length == 4 && a[1].equals("remove")) return memberNames(a[2], a[3]);
        if (a.length == 4 && a[1].equals("tp")) return playerNames(a[3]);
        if (a.length == 3 && a[1].equals("settings")) return matching(List.of("friendlyfire"), a[2]);
        if (a.length == 4 && a[1].equals("settings")) return teamNames(a[3]);
        if (a.length == 5 && a[1].equals("settings")) return matching(List.of("on", "off"), a[4]);
        return List.of();
    }

    private List<String> armorTabs(String[] a) {
        if (a.length == 2) return matching(List.of("give", "save", "list", "delete"), a[1]);
        if (a.length == 3 && a[1].equals("give")) return matching(List.of("direct", "saved", "random"), a[2]);
        if (a.length == 3 && a[1].equals("delete")) return loadoutNames(a[2]);
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
        if (a.length == 4 && a[1].equals("save")) return playerNamesAndSingleSelectors(a[3]);
        return List.of();
    }

    private List<String> checkpointTabs(String[] a) {
        if (a.length == 2) return matching(List.of("save", "tp", "back", "list", "delete", "clear"), a[1]);
        if (a.length == 3 && List.of("tp", "back", "delete").contains(a[1])) return checkpointNames(a[2]);
        if (a.length == 4 && List.of("tp", "back", "save").contains(a[1])) return targets.suggestions(a[3]);
        if (a.length == 3 && a[1].equals("clear")) return matching(List.of("all"), a[2]);
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
        if (a.length == 3 && a[1].equals("reset")) return matching(List.of("global", "outgoing", "incoming", "pair", "all"), a[2]);
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
        if (a.length == 4 && remove) return matching(List.of("mending", "unbreaking", "protection", "sharpness", "efficiency", "fortune"), a[3]);
        if (a.length == 4) return matching(List.of("mending", "unbreaking", "protection", "sharpness", "efficiency", "fortune", "feather_falling", "thorns", "respiration"), a[3]);
        if (a.length == 5 && !remove) return matching(List.of("1", "2", "3", "4", "5"), a[4]);
        return List.of();
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