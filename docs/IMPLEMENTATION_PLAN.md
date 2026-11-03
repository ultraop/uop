# UOP Implementation Plan

## Phase 0 — Foundation
- [x] Inspect legacy Paper repository and record its reusable resources.
- [x] Define upgraded command grammar and target UX.
- [x] Establish new package/build/bootstrap documentation.
- [ ] Add Gradle wrapper and run a clean Paper build.
- [ ] Add CI validation and baseline tests.

## Phase 1 — Core platform
- [ ] Plugin bootstrap and service registry.
- [ ] Versioned storage, atomic writes, backups.
- [ ] Shared TargetResolver for Team names, selectors and Player names.
- [ ] Contextual argument hints and dynamic TAB suggestions.
- [ ] Central permission checks and command registry.
- [ ] Lifecycle/reload tests.

## Phase 2 — Team and identity
- [ ] Team CRUD, member add/remove/removeall, info/list and friendly fire.
- [ ] Username-keyed persistent roster.
- [ ] Independent persistent PlayerProfile.
- [ ] Same-name reconnect restores membership and durable profile settings.
- [ ] Explicit cross-Team move and duplicate-membership rules.
- [ ] Offline-mode spoofing risk documentation and UUID-aware option.

## Phase 3 — Legacy feature parity
- [x] Armor set/save/list/delete/saved/random give, classic weapon set and safe remove.
- [ ] Full checkpoint snapshot and restore.
- [ ] Damage global/outgoing/incoming/exact pair.
- [ ] Enchant/disenchant scopes.
- [ ] Normal/full freeze.
- [x] Inventory operations, Player/Team swap, backup, history and locks.
- [ ] Name visibility/tags, protection and permissions.
- [ ] Team support for every compatible service.

## Phase 4 — Requested upgrades
- [ ] Direct enchanted kit: iron/gold/diamond/netherite/mixed.
- [ ] Enchantment preset levels 1–5, compatibility table and tests.
- [ ] Team armor distribution.
- [ ] Team-to-Team inventory swap with validation and rollback.
- [ ] Team checkpoint save/restore and offline reporting.
- [ ] Team-vs-Player and Team-vs-Team damage rules.
- [x] Immortal Player/Team persistent profile state and lethal-damage prevention.
- [x] Independent inventory, armor and drop locks.
- [ ] Name glitch only after dependency identified and verified.

## Phase 5 — Hardening and release
- [ ] Command permission and argument audit.
- [ ] TAB/hint audit across every argument.
- [ ] Selector edge-case tests.
- [ ] No-partial-update tests for bulk Team operations.
- [ ] Legacy data migration test with pre-migration backup.
- [ ] Paper 1.21.11 live server tests.
- [ ] Verify JAR descriptor, plugin class and checksum.
- [ ] Publish CI artifact; mark only verified features as implemented.

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
