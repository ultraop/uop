# UOP Architecture

## Design
UOP is a Paper-native creator/recording administration plugin. The legacy `Ahsan3944/UOP-Recording-Tools` repository is a behavioural reference, not a requirement to preserve its internal class layout.

## Proposed package layout
```text
com.ultraop.uop
├── UopPlugin
├── command
│   ├── UopRootCommand
│   ├── CommandRegistry
│   ├── argument (TargetArgument, TeamArgument, SavedNameArgument, MaterialArgument, EnchantmentArgument)
│   ├── suggestion (TargetSuggestions, SavedDataSuggestions, ContextualSuggestions)
│   └── hint (ArgumentHintProvider)
├── target (TargetResolver, TargetExpression, ResolvedTargets, PlayerTarget, TeamTarget, SelectorTarget)
├── team (TeamService, TeamRepository, TeamProfile, TeamSettings, TeamMember)
├── profile (PlayerProfile, PlayerProfileService, PlayerIdentity, OfflinePlayerState)
├── service
│   ├── armor
│   ├── checkpoint
│   ├── damage
│   ├── enchant
│   ├── freeze
│   ├── immortal
│   ├── inventory
│   ├── name
│   ├── protection
│   └── permission
├── listener (PlayerLifecycleListener, DamageListener, FreezeListener, InventoryProtectionListener, NameTagListener)
├── storage (UopStorage, YamlUopStorage, DataMigration, StorageBackup)
└── util (ItemCodec, EnchantmentRules, SelectorUtil, TextUtil, Validation)
```

## Shared target resolution
Every compatible command uses one `TargetResolver`; individual services must not parse selectors or Team names independently. Its target expression parser preserves commas inside balanced selector/NBT bracket structures and quoted values, rejects mismatched/unmatched brackets, invalid direct selector nesting and empty comma-list entries, and de-duplicates resolved players.

Completion order:
1. Team names, displayed with a visual marker such as `★` (marker is not part of actual command input).
2. Supported selectors: `@a`, `@p`, `@r`, `@s`, and valid selector filters such as `@a[distance=..10]`.
3. Online Player names.

`@e` is shown only where non-player entities are genuinely supported. Player-only operations filter to players or reject invalid entity results. Single-target arguments must reject zero or multiple resolved players. Invalid selectors return clear errors. Multi-target commands may accept multiple unambiguous names/selectors.

## Team and identity behaviour
Team membership is keyed by case-insensitive Minecraft username, per the requested reconnect behaviour. Display/canonical casing is preserved. Disconnect never removes a roster member. Rejoining with the same username reconnects to the existing profile and Team record. Removing a member changes membership only and does not erase the personal profile or backups. Team deletion and profile deletion are separate. One username can belong to one Team at a time; cross-team moves must be explicit.

**Security caveat:** username-only identity is not authentication. Offline-mode/cracked servers can allow another person to claim an existing name. Retain UUID metadata and document this risk; offer an optional UUID-aware identity policy for protected deployments. Do not claim username-only matching securely proves identity.

## Player profiles
Persist a normalized-username PlayerProfile with last-seen UUID metadata. Keep durable settings such as Team membership, freeze, immortal, name visibility/tags, inventory backups/history metadata and pending offline actions. Never persist live Bukkit Player objects or mutable runtime inventory references.

## Team-aware feature contract
Armor, checkpoints, damage, enchant/disenchant, freeze/unfreeze, immortal, inventory operations/locks and name/tag operations all accept Player, Team or supported selector targets. A Team target resolves its roster and executes per-player behaviour. A Team does not own one shared inventory or one shared checkpoint snapshot unless an explicit operation says otherwise.

## Checkpoint snapshot
Capture an independent snapshot per selected online player: identity metadata, world UUID/name, position, yaw/pitch, velocity, full inventory, armor, offhand, selected hotbar slot, health, food level, saturation, XP, fire ticks, gamemode and active potion effects. Restore clamps health/food/saturation to the player's current legal ranges and skips a snapshot when its world is unavailable. `checkpoint tp` is full restore plus teleport, not teleport-only. Saving a Team checkpoint reports offline members without inventing snapshots. Restoring reports restored/offline/missing/failed counts.

## Damage precedence
1. Exact attacker → victim rule.
2. Attacker outgoing rule.
3. Victim incoming rule.
4. Global multiplier.

Attacker and victim can each be a Player or Team target. Team-vs-Team rules match current Team membership. Friendly-fire OFF cancels same-Team player damage (including player projectiles and tameable attacks) before multiplier evaluation. Zero damage must not cancel unrelated mechanics such as Wind Charge propulsion.

## Armor and direct kit
Saved loadouts contain armor, inventory/hotbar, offhand and included weapons/tools. Armor replacement must not drop old armor or silently lose items. `/uop armor give direct` generates a full kit: helmet, chestplate, leggings, boots, sword, pickaxe, bread and steak. Materials: iron, gold, diamond, netherite, mixed (retain other legacy materials only if explicitly required). Enchantment preset levels 1–5 are centrally defined: level 1 Mending only; levels 2–4 add progressively defined compatible enchantments; level 5 is the maximum compatible preset. No conflicting enchantments. Food quantities are not inferred; they need an explicit agreed mapping.

## Freeze and immortal
Normal freeze blocks movement and defined external actions while allowing intended inventory preparation. Full freeze additionally blocks rotation and inventory/hand changes; ESC remains available. Team freeze affects current members and is stored for offline members to apply on join. OP/staff exemptions remain configurable. Freeze, immortal and inventory-lock state is cached for movement/damage event hot paths; async login/chat readers use immutable snapshots rather than mutable YAML.

Immortal prevents lethal damage and maintains at least 1 HP. It applies to Player or Team and persists for reconnects. Void, `/kill`, plugin damage and direct health-setting require explicit handling and tests before claiming universal immunity.

## Inventory
Inventory belongs to each player. Team targets mean apply per member, not a pooled inventory. Support view/edit/give/take/clear, backup/restore/history, inventory/armor/drop locks, Player swap and Team-to-Team swap. Team swap initially requires equal roster sizes, deterministic member ordering and rollback snapshots; otherwise abort all changes.

## Storage and migration
Use versioned YAML initially with atomic replacement and backups. Before destructive resets, create a ZIP archive of the plugin data folder (including config and separate history files); keep the 10 newest backups. The main `uop.yml` stores `schema-version`, `teams`, `players`, `armor-loadouts`, `checkpoints`, damage rules, inventory backups, name tags, protection, permissions and the active recording roster. Inventory history snapshots are stored separately under `history/<player>.yml`, cached in memory, serialized on the server thread and written by a dedicated single-thread writer using temporary files and atomic replacement. Existing in-YAML histories migrate lazily when that player's history is first accessed.

UUID protection is the default to avoid dynamic-IP lockouts; explicit IP/both modes are available. Offline-mode servers log a warning because username-derived UUIDs are not authenticated.

Legacy migration is opt-in: detect old `plugins/UOP-Recording-Tools/data/data.yml`, back it up first, migrate only reliably mappable data, retain UUID history, create name aliases only where reliable, and report ambiguous/unsupported data. Never delete legacy data.

## Command UX
Every argument has a concise usage hint (`[Team name]`, `[Player / selector]`, `[Checkpoint name]`, `[Enchantment level 1-5]`). Suggestions are contextual and based on current persisted data. Hide invalid options. Strip display markers before parsing. Permission checks happen during execution, not just in TAB completion.

## Security and performance
Avoid full-world scans and blocking disk I/O on the main tick. Inventory history avoids rewriting the monolithic UOP data file for every sample; per-player history files are atomically replaced by a dedicated writer. Bound inventory history, detect duplicate snapshots, validate whole Team operations before mutation, snapshot before destructive bulk operations, and log detailed errors server-side while showing concise user-facing errors.
