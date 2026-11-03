# Legacy Reference Notes

Source: https://github.com/Ahsan3944/UOP-Recording-Tools (main branch).

Inspected project facts:
- Paper plugin targeting Minecraft 1.21.11.
- Java 21 toolchain and Gradle build.
- Root command `/uop` with `ultraop` alias.
- Entry point and descriptor under `src/main`.
- Large command/compatibility handlers with TAB completion.
- Recording listener.
- Separate services: Armor, Checkpoint, Damage, Enchant, Freeze, Inventory, Name, Permission and Protection.
- YAML `DataStore` with atomic file replacement.
- GitHub Actions build workflow validates `plugin.yml`, packages JAR and SHA-256 checksum.

Legacy source files inspected include `README.md`, `build.gradle`, `settings.gradle`, `plugin.yml`, `UopRecordingToolsPlugin.java`, `UopCommand.java`, `ArmorService.java`, `CheckpointService.java`, `DamageService.java`, `InventoryService.java`, `FreezeService.java`, `Targets.java`, `DataStore.java`, and `.github/workflows/build.yml`.

Upgraded design decisions:
1. New package root: `com.ultraop.uop`.
2. One shared TargetResolver for Team names, selectors and Player names.
3. Username-keyed Team roster with independent Player profiles and UUID metadata.
4. Consolidated command grammar and selector-based targeting rather than redundant everyone/nearby keywords.
5. Full checkpoint restore per player.
6. Direct enchanted OP kit under `/uop armor give direct`.
7. First-class Immortal service.
8. Separate inventory/armor/drop locks.
9. Team-aware feature services.
10. Explicit, backed-up legacy data migration.

The existence of legacy classes does not prove every edge case is correct. Inspect and test before reusing implementation logic. The note does not claim all legacy features are production-ready.
