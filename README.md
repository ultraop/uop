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
`./gradlew clean build` (Windows: `gradlew.bat clean build`). Output: `build/libs/`.

## Status
This initial commit establishes project documentation and the Paper bootstrap. A feature is not considered implemented until its service, listener, persistence, permission checks, command execution, TAB completion and tests are complete.

## License
Proprietary — UltraOP.
