# Game data model

Kotlin types in `org.example.model` describe the Markdown game model.

- `MarkdownPage<M>` separates YAML metadata, title, narrative sections, and the final changelog.
- `Game`, `World`, `Cell`, `GameObject`, `Actor`, `GameState`, and `Step` name the page types. The player is an `Actor` controlled by `PLAYER`.
- `TurnRecord` describes the semantic content of step sections, including actor proposals, game-master resolutions, application records, and formal-update reports. It is not another persisted file.
- `PageReference` paths are relative to their containing page. A consumer must resolve them before comparing membership; raw relative strings from different pages are not entity identities.
- Cell membership and actor inventories are authoritative. Entity `location` and game-state `currentCell` are derived values. Models represent those values; the future updater must enforce their consistency.

Kotlin property names correspond to snake_case header fields (`currentCell` → `current_cell`). Enum `metadataValue` properties preserve the existing hyphenated values. Game-specific fields such as `frozen`, `portable`, and `outcome` use `extensions` with typed `MetadataValue` values. A future codec must flatten these into top-level header fields and reject collisions with known fields.

Descriptions, intro messages, rule text, and narrative current state remain Markdown sections. Empty inventories use empty lists; absent active/previous steps use null. Both `music` and `ambient_music` are represented because existing pages use both names.

This module contains no Markdown/YAML codec, filesystem access, agent runtime, or rule execution. Nonempty pending-work records, exceptional connections, and turn report structures are draft types for the corresponding design concepts.

Build with `./gradlew :model:build`.
