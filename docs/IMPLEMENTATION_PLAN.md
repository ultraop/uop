# UOP Implementation Plan

## Phase 0 — Foundation
- [x] Inspect legacy Paper repository and record its reusable resources.
- [x] Define upgraded command grammar and target UX.
- [x] Establish new package/build/bootstrap documentation.
- [ ] Add Gradle wrapper scripts/JAR; clean builds currently run in CI using an explicit Gradle 9.5.1 installation.
- [x] Add CI JAR validation and core validation tests.

## Phase 1 — Core platform
- [x] Plugin bootstrap and service wiring.
- [x] Schema-versioned YAML storage with atomic writes.
- [ ] Automated migration backups and schema migration tests.
- [x] Shared TargetResolver for Team names, selectors and Player names.
- [x] Contextual argument hints and dynamic TAB suggestions for implemented command branches.
- [x] Central execution-time permission checks and command registration.
- [ ] Lifecycle/reload tests.

## Phase 2 — Team and identity
- [x] Team CRUD, member add/remove/removeall, info/list and friendly fire.
- [x] Username-keyed persistent roster.
- [x] Independent persistent PlayerProfile.
- [x] Same-name reconnect retains membership and profile-keyed durable settings.
- [ ] Explicit cross-Team move and duplicate-membership rules.
- [x] Document offline-mode spoofing risk, log a warning when online-mode is disabled, and default new protection entries to UUID mode.
- [ ] Migrate all username-keyed profiles and permissions to UUID identities.

## Phase 3 — Legacy feature parity
- [x] Armor set/save/list/delete/saved/random give, classic weapon set and safe remove.
- [x] Full checkpoint snapshot and restore.
- [x] Damage global/outgoing/incoming/exact pair.
- [x] Enchant/disenchant scopes for online targets with offline Team actions queued.
- [x] Normal/full freeze.
- [x] Inventory operations, Player/Team swap, backup, history and locks.
- [x] Persist active history sessions across reconnect/restart; retry failed samples without cancelling the sampler.
- [x] Store per-player history separately using atomic background file writes; migrate legacy YAML history lazily.
- [x] Cache freeze/immortal/lock state for hot event paths.
- [x] Automatically back up current inventory before applying a saved armor loadout.
- [x] Create rolling full-data ZIP backups before destructive reset/clear operations.
- [x] Name visibility/tags, protection and permissions. Chat tag rendering preserves external display-name formatting; overhead nameplate effect still needs live testing.
- [ ] Complete Team support audit, including offline inventory operations and edge cases.

## Phase 4 — Requested upgrades
- [x] Direct enchanted kit: iron/gold/diamond/netherite/mixed.
- [x] Enchantment preset levels 1–5 with compatible item-specific enchantments.
- [x] Team armor distribution with offline actions queued.
- [x] Team-to-Team inventory swap with validation and rollback.
- [x] Team checkpoint save/restore and offline reporting/queued restore.
- [x] Team-vs-Player and Team-vs-Team damage rules.
- [x] Immortal Player/Team persistent profile state and lethal-damage prevention.
- [x] Independent inventory, armor and drop locks.
- [ ] Name glitch only after dependency identified and verified.

## Phase 5 — Hardening and release
- [ ] Final command permission and argument audit.
- [ ] Final TAB/hint audit across every argument and selector filter.
- [x] Selector edge-case tests (nested NBT lists, quoted commas, malformed brackets and empty entries).
- [ ] No-partial-update tests for bulk Team operations.
- [ ] Legacy data migration test with pre-migration backup.
- [ ] Paper 1.21.11 live server tests.
- [x] CI verifies JAR descriptor, plugin class and checksum.
- [x] CI publishes the built JAR artifact.
- [ ] Mark release-ready only after live Paper server testing.

## Regression checklist
- Armor replacement does not drop/lose old items.
- Loadout includes inventory, armor, hotbar and offhand.
- Checkpoint snapshots are per-player and full restore is deterministic.
- Damage precedence: exact pair > outgoing > incoming > global.
- Zero damage preserves unrelated physics where possible (Wind Charge).
- Normal freeze allows intended preparation; full freeze blocks configured actions while ESC remains usable.
- Staff exemptions work.
- Inventory history is bounded and avoids redundant snapshots.
- Tags and settings persist through reconnect.
- Protection checks at correct login stage and documents proxy forwarding.
- Team membership/profile survive quit/rejoin.
- Offline Team state applies on next join.
- Team swap validates all members and rolls back on failure.
- Completion order is Team, selectors, online Player names.

## Legacy source inspected
Legacy repo: `Ahsan3944/UOP-Recording-Tools`. It contains Gradle/Java 21/Paper 1.21.11 setup; plugin bootstrap; command handlers; recording listener; separate Armor, Checkpoint, Damage, Enchant, Freeze, Inventory, Name, Permission and Protection services; YAML DataStore; plugin.yml; GitHub Actions workflow. These components guide parity, but the old large command handler and UUID-only target state are not copied blindly.
