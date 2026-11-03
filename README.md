# UOP — UltraOP Recording Toolkit

Upgraded successor to the legacy [UOP Recording Tools](https://github.com/Ahsan3944/UOP-Recording-Tools). Target: Paper 1.21.11, Java 21+, server-side plugin. Root command: `/uop` (alias `/ultraop`).

## Goals
- Preserve useful recording/admin behaviour from the legacy implementation without copying its architecture blindly.
- Consolidate repeated command paths while retaining features.
- Make Player, Team and supported Minecraft selectors first-class shared targets.
- Persist Team roster and Player profiles across disconnect/reconnect.
- Provide contextual argument hints and dynamic TAB completion.
- Build and test as a Paper plugin; no client-side mod required.

## Target platform
| Property | Value |
|---|---|
| Plugin | UOP |
| Plugin ID | `uop` |
| Platform | Paper |
| Minecraft | 1.21.11 |
| Java | 21+ |
| Build | Gradle |
| Storage | Versioned YAML initially |
| Repository | `ultraop/uop` |

## Architecture
See [Architecture](docs/ARCHITECTURE.md), [Commands](docs/COMMANDS.md), [Implementation Plan](docs/IMPLEMENTATION_PLAN.md), and [Legacy Reference](docs/LEGACY_REFERENCE.md).

## Command groups
`armor`, `checkpoint`, `damage`, `enchant`, `disenchant`, `freeze`, `unfreeze`, `immortal`, `team`, `inv`, `name`, `protect`, `permission`, `help`, `reload`, `reset`, `version`.

## Target suggestions
Target argument completion order is **★ Team names**, then valid selectors (`@a`, `@p`, `@r`, `@s`, and supported filters), then online player names. `@e` is offered only for entity-capable operations. The star is display-only. Do not add duplicate `everyone`/`nearby` target keywords where vanilla selectors cover the use case.

## Build
Gradle 9.5.1 and Java 21 are used by the GitHub Actions pipeline.

```bash
gradle clean test build
```

Windows:

```bat
gradle.bat clean test build
```

Output: `build/libs/`. The CI pipeline also validates `plugin.yml`, verifies the main plugin class, generates a SHA-256 checksum, and uploads the JAR artifact.

## Implementation status
The repository now contains the first working implementation layer for Team/profile persistence, shared Player/Team/selector targeting, armor presets and direct kits, checkpoints, damage rules, enchant/disenchant, freeze/immortal, inventory operations/history/locks, name tags, login protection, permissions, argument hints, and contextual TAB completion.

Not all release requirements are complete. Name Glitch integration is intentionally disabled until its dependency is identified. Legacy data migration, complete automated feature tests, and live Paper 1.21.11 server testing remain outstanding. A green CI build confirms compilation and packaging, not every in-game behaviour.

## License
Proprietary — UltraOP.
