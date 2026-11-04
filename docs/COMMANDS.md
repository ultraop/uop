# UOP Command Specification

This reference describes the implemented command tree and identifies unavailable integrations explicitly. It is not a claim that every proposed legacy feature is complete.

## Target notation
`<target>` = Team name, Minecraft selector or Player name. TAB order: **★ Team names**, then supported selectors, then online Player names. The star is display-only. Suggested selectors include `@a`, `@p`, `@r`, `@s`, and player-filtered `@e[type=player]`. Selector parsing preserves commas inside brackets; player-only resolution returns only players. No duplicate `everyone`/`nearby` keywords where selectors cover the target. Command handlers show contextual usage hints on invalid or incomplete syntax.

## Armor
```text
/uop armor set <target> <leather|chainmail|iron|gold|diamond|netherite> <plain|enchanted <1-10>|max> [true|false|on|off] [weapons...]
/uop armor weapon set <target> <plain|enchanted <1-10>|max> <weapons...>
/uop armor save <set_name> <player>
/uop armor list
/uop armor delete <set_name>
/uop armor give direct <iron|gold|diamond|netherite|mixed> enchanted <level_1-5> <target>
/uop armor give saved <target> <set_name>
/uop armor give random <target> <set_name1,set_name2,...|all>
/uop armor remove <target>
```
`give` options are ordered: `direct` first (top-priority), then `saved`, then `random`. Legacy armor-set materials: `leather|chainmail|iron|gold|diamond|netherite`. Direct kit material: `iron|gold|diamond|netherite|mixed`. Legacy weapon/tool options: `sword|mace|axe|bow|crossbow|trident|pickaxe|shovel|hoe`. Level: `1|2|3|4|5`. Target suggestions: ★ Teams, selectors, player names. Kit contents: helmet, chestplate, leggings, boots, sword, pickaxe, bread, steak. Level 1 is Mending-only; levels 2–4 use centrally defined progressive compatible enchantment groups; level 5 is the maximum compatible preset. Saved/random distribution applies per recipient; random mode selects a saved preset independently per recipient. The implemented direct kit supplies 16 bread and steak (cooked beef) scaled by preset: level 1 = 4, level 2 = 8, level 3 = 12, level 4 = 16, level 5 = 20. Classic armor replacement and weapon insertion stage changes first and abort without dropping items if inventory capacity is insufficient. `armor remove` returns worn armor plus main-hand/offhand items to storage and cancels if there is no room.

## Checkpoint
```text
/uop checkpoint save <checkpoint_name> <target>
/uop checkpoint list
/uop checkpoint delete <checkpoint_name>
/uop checkpoint tp <checkpoint_name> <target>
/uop checkpoint back <checkpoint_name> <target>
/uop checkpoint clear all confirm
```
Each Player receives an independent snapshot. `tp` restores full snapshot and position: world, coordinates, rotation, inventory, armor, offhand, selected slot and effects. Team save reports offline members. TAB suggests saved checkpoint names before target choices.

## Damage
```text
/uop damage global <multiplier_0-10>
/uop damage outgoing <target> <multiplier_0-10>
/uop damage incoming <target> <multiplier_0-10>
/uop damage set <attacker_target> <victim_target> <multiplier_0-10>
/uop damage pair <attacker_target> <victim_target> <multiplier_0-10>
/uop damage reset global
/uop damage reset outgoing <target>
/uop damage reset incoming <target>
/uop damage reset pair <attacker_target> <victim_target>
/uop damage reset all confirm
/uop damage list
```
Player/Team/selectors supported. Exact pair > outgoing > incoming > global. Friendly-fire OFF cancels same-Team player damage, including player-shot projectiles and player-owned tameable attacks.

## Enchant / disenchant
```text
/uop enchant <all|armor|equipment|mainhand|offhand|inventory> <target> <enchantment> <level>
/uop disenchant <all|armor|equipment|hand|inventory> <target> [slot] <enchantment>
```
Target may be Player, Team or selector. Suggest compatible enchantments, levels and slots. Remove only selected enchantment.

## Freeze / unfreeze
```text
/uop freeze <normal|full> <target>
/uop unfreeze <target>
```
Team and Player targets accepted. Offline freeze state is stored and applied on join. `/uop freeze status` is not currently implemented; use `/uop unfreeze <target>` to clear the state.

## Immortal
```text
/uop immortal <on|off> <target>
/uop immortal status <target>
/uop immortal list
```
Player/Team/selectors. Prevent lethal damage and keep at least 1 HP.

## Team
```text
/uop team create <team_name>
/uop team delete <team_name>
/uop team list
/uop team info <team_name>
/uop team add <team_name> <players...>
/uop team remove <team_name> <player>
/uop team removeall <team_name>
/uop team tp <team_name> <destination_player>
/uop team settings friendlyfire <team_name> <on|off>
```
Membership persists across disconnects and same-name reconnects. Team delete does not delete Player profiles.

## Inventory
```text
/uop inv view <target>
/uop inv set <target> <slot_0-40> <item> [amount]
/uop inv remove <target> <slot_0-40>
/uop inv give <target> <item> [amount]
/uop inv take <target> <item> [amount]
/uop inv clear <target>
/uop inv swap player <player1> <player2>
/uop inv swap team <team1> <team2>
/uop inv backup <target> <backup_name>
/uop inv restore <target> <backup_name>
/uop inv backups <target>
/uop inv backup-delete <target> <backup_name>
/uop inv record start <target>
/uop inv record stop <target>
/uop inv record status
/uop inv back <target> <time>
/uop inv lock <inventory|armor|drop> <target>
/uop inv unlock <inventory|armor|drop> <target>
```
Team actions are per-member. Team swap requires equal roster size and deterministic pairing; validate and snapshot all members before mutation. Inventory `set`, `remove`, `clear`, `swap`, `restore`, and `back` create bounded automatic pre-change backups; checkpoint restoration also backs up the current inventory. Inventory history stores the active recording roster in `uop.yml` and snapshots in separate per-player files under `history/`. Snapshot serialization happens on the server thread; atomic file writes run on a dedicated writer thread. Legacy history in `uop.yml` migrates lazily on first access. Sampling pauses while recorded players are offline and resumes after reconnect; recording state is reloaded after plugin/server restart. `/uop inv record stop <target>` also accepts offline Player/Team names. Inventory restore/back and saved loadout application create pre-change backups. Destructive reset/clear operations create ZIP archives in `backups/` before deleting data; the newest 10 archives are retained. Live crash-recovery and long-duration performance testing are still required.

## Name / tags
```text
/uop name hide <target>
/uop name show <target>
/uop name tag create <tag_id> <color> <bold> <italic> <text>
/uop name tag edit <tag_id> <color> <bold> <italic> <text>
/uop name tag list
/uop name tag give <target> <tag_id>
/uop name tag change <target> <tag_id>
/uop name tag remove <target>
/uop name tag delete <tag_id>
```
Tags are rendered as a prefix before the existing chat display name, preserving other chat-plugin formatting. They do not claim to alter the vanilla overhead player nameplate. Name Glitch is currently an unavailable stub: the optional dependency has not been identified or verified. The command reports that it is disabled and does not change player names.

## Protection
UUID mode is the default to avoid mobile/dynamic-IP lockouts. `ip` and `both` require stable IP addresses; use them only when that is intended. Offline-mode servers emit a warning because player usernames and offline UUIDs are not authenticated.
```text
/uop protect add <target> [uuid|ip|both]
/uop protect remove <target>
/uop protect list
/uop protect reset confirm
```

## Permissions and administration
```text
/uop permission grant <player> <nodes...>
/uop permission revoke <player> <nodes...>
/uop permission list <player>
/uop permission check <player> <node>
/uop permission reset <player> [node]
/uop permission reset all confirm
/uop help [group]
/uop help unfreeze
/uop reload
/uop reset all confirm
/uop version
/uop pending list <player>
/uop pending clear <player> confirm
```
Permission administration requires `uop.permission` (granted to OP by default); global permission reset requires explicit `confirm`. Destructive UOP reset, checkpoint clear and protection reset commands also require confirmation and create a ZIP backup before clearing data.

## Contextual argument hints
Examples: `[Saved loadout name]`, `[Checkpoint name]`, `[Team name]`, `[Player / selector]`, `[Enchantment]`, `[Enchantment level]`, `[Inventory slot 0-40]`, `[Multiplier 0-10]`. Suggestions are contextual by command position, including registry-derived enchantment keys and each enchantment's maximum supported level. `/uop` and `/uop help` display the command directory; `/uop help <group>` prints nested syntax and the group is TAB-completable. Pending offline actions expire after 30 days, duplicate payloads are suppressed, failed actions are discarded after 10 attempts, and admins can inspect/clear them with `/uop pending`.
