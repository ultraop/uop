# Recording Safety and Reliability

## Recording priority
UOP is an admin toolkit used while a creator records. The plugin must not force camera movement, alter the recording operator's inventory, or apply a bulk target to the sender unless explicitly selected.

## Required invariants
- Admin/console permissions are checked before each mutation.
- Selector and Team expansion is completed before applying changes.
- A single-target operation rejects zero or multiple matches.
- Bulk operations validate all recipients and arguments before the first change.
- Destructive operations create a rollback snapshot and restore it if a transaction fails.
- Full inventories never cause silent item deletion.
- Checkpoint restore includes world, position, rotation, inventory, armor, offhand, held slot, health, food, saturation and potion effects.
- Team checkpoint data is a separate snapshot for each member.
- Team membership survives quit/rejoin; disconnect never removes roster membership.
- Team swap is deterministic and all-or-nothing.
- Freeze, immortal, inventory lock and armor lock are stored per player profile and restored on join.
- Inventory history is bounded by both time window and maximum snapshots.
- No synchronous disk operation is added to high-frequency movement, damage or inventory events.
- UOP must not cancel unrelated movement physics when damage is configured to zero.
- Reload and reset must not mutate player inventories or erase external server data.

## Verification matrix
| Area | Required tests |
|---|---|
| Targets | Team-first suggestions, selector parsing, comma handling, duplicate de-duplication, invalid selector rejection |
| Teams | add/remove/removeall, one-team policy, offline roster persistence, same-name reconnect |
| Armor | direct kit levels 1-5, material presets, no-drop replacement, full inventory rollback, saved/random distribution |
| Checkpoints | per-player snapshots, Team save, missing world handling, restore state and effects |
| Damage | global/outgoing/incoming/pair precedence, Team-vs-Team, friendly-fire, zero-damage physics |
| Freeze | normal/full modes, allowed inventory preparation, blocked actions, reconnect persistence |
| Immortal | lethal damage, void, projectile, environmental damage, reconnect |
| Inventory | slot bounds, drop lock, armor lock, history limits, player/team swap rollback |
| Recording | no forced movement, no accidental inventory edits, no server tick stalls, no data loss on reload |
| UX | every argument has a hint, list commands exist, TAB only suggests valid next arguments |
