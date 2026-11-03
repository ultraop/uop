# UOP Command Specification

This is the target command tree, not a claim that all commands are already implemented.

## Target notation
`<target>` = Team name, Minecraft selector or Player name. TAB order: **★ Team names**, then selectors, then online Player names. The star is display-only. Selectors: `@a`, `@p`, `@r`, `@s`, and supported filters. For player-only operations, `@e[type=player]` is the only entity selector suggested. No duplicate `everyone`/`nearby` keywords where selectors cover the target. Each argument must show a contextual hint.

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
/uop checkpoint clear all
```
Each Player receives an independent snapshot. `tp` restores full snapshot and position: world, coordinates, rotation, inventory, armor, offhand, selected slot and effects. Team save reports offline members. TAB suggests saved checkpoint names before target choices.

## Damage
```text
/uop damage global <multiplier_0-10>
/uop damage outgoing <target> <multiplier_0-10>
/uop damage incoming <target> <multiplier_0-10>
/uop damage set <attacker_target> <victim_target> <multiplier_0-10>
/uop damage reset global
/uop damage reset outgoing <target>
/uop damage reset incoming <target>
/uop damage reset pair <attacker_target> <victim_target>
/uop damage reset all
/uop damage list
```
Player/Team/selectors supported. Exact pair > outgoing > incoming > global. Friendly-fire OFF overrides same-Team damage.

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
/uop freeze status <target>
```
Team and Player targets accepted. Offline durable state applied on join.

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
Team actions are per-member. Team swap requires equal roster size and deterministic pairing; validate and snapshot all members before mutation.

## Name / tags
```text
/uop name hide <target>
/uop name show <target>
/uop name glitch <target> <glitch_value>
/uop name glitch reset <target>
/uop name tag create <tag_id> <color> <bold> <italic> <text>
/uop name tag edit <tag_id> <color> <bold> <italic> <text>
/uop name tag list
/uop name tag give <target> <tag_id>
/uop name tag change <target> <tag_id>
/uop name tag remove <target>
/uop name tag delete <tag_id>
```
Name glitch dependency must be identified and verified before implementation is advertised.

## Protection
```text
/uop protect add <target> [uuid|ip|both]
/uop protect remove <target>
/uop protect list
/uop protect reset
```

## Permissions and administration
```text
/uop permission grant <player> <nodes...>
/uop permission revoke <player> <nodes...>
/uop permission list <player>
/uop permission check <player> <node>
/uop permission reset <player> [node]
/uop permission reset all
/uop help [category]
/uop reload
/uop reset all
/uop version
```
Permission administration is OP/console-only by default.

## Contextual argument hints
Examples: `[Saved loadout name]`, `[Checkpoint name]`, `[Team name]`, `[Player / selector]`, `[Enchantment]`, `[Enchantment level]`, `[Inventory slot 0-40]`, `[Multiplier 0-10]`. Suggestions must match the exact command position. `/uop help` displays top-level groups; `/uop help <group>` displays that group's command syntax and its group name is TAB-completable.
