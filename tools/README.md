# Game-loop AI tools

`kzork.tools.GameLoopTools` exposes JSON function definitions and a dispatcher for an agent host.
The tools work locally against one game directory; they do not call an AI service or require an API key.

```kotlin
import java.nio.file.Path
import kotlinx.serialization.json.*
import kzork.tools.*

val gameMaster = GameLoopTools(Path.of("games/ice_dungeon"), ToolRole.GAME_MASTER)
val declarations = gameMaster.definitions().map { it.declaration() }
// Give declarations to the AI client using that provider's function-tool envelope.
val result = gameMaster.call("read_page", buildJsonObject { put("path", "state.md") })
// Return result.toString() as the tool response associated with the client's call ID.
```

The caller chooses the role; role checks are not an authentication system.
Game masters and actors cannot invoke updater tools.
These read tools expose the complete game filesystem, including private rules and history; a blind player evaluator
needs a separate player-facing interface.

| Tool | Roles | Operation |
|---|---|---|
| `list_pages` | All | List Markdown paths with pagination and a revision token |
| `read_page` | All | Read one Markdown page and revision token |
| `search_pages` | All | Literal case-insensitive search with bounded line snippets |
| `validate_state` | All | Validate memberships and grid index; report stale derived fields |
| `record_step` | Game master | Persist a resolved turn and queue it for application |
| `apply_step` | State updater | Create/update pages in a validated batch and finish the turn |

## Turn protocol

1. Read the current state and relevant pages.
   Read tool responses include a revision hash of the game's Markdown files.
2. The game master decides outcomes, including any invited actor proposals.
   Call `record_step` with `expected_revision`, `command`, `resolved_account`, and `instructions`.
   The account must include the actor proposals and game-master resolutions when relevant.
3. The tool writes `steps/step-NNNN.md`, marks it resolved, and sets `state.active_step` and `loop_phase: applying`.
   It returns the new revision.
4. The updater reads the record and builds `changes`: objects with `path`, `content`, and `reason`.
   Content is a complete Markdown document with YAML front matter.
   Omit the changelog or preserve it exactly.
   Unknown header fields and narrative sections are retained when supplied; YAML comments and formatting may be
   normalized.
5. Call `apply_step` with the latest `expected_revision`, the step path, and all changes together.
   Creating an entity and adding its membership belong in the same batch.
   To move or take something, change the membership lists; leave existing `location` and `current_cell` values
   untouched.
6. The tool validates memberships, derives locations and the player's cell, appends changelog entries, marks the step
   applied, and advances the loop.
   An empty changes list completes a turn without world changes.
   Repeat application is rejected rather than duplicating history.

Every call returns either `{"ok":true,"result":...}` or `{"ok":false,"error":"..."}`.
On a stale revision, reread the files and reconsider the changes; do not blindly repeat an old batch.
Validation failures leave the files unchanged.

## Rules and boundaries

- Tool paths are relative to the configured game root.
  Header references are relative to their containing page.
- Files outside the root, absolute paths, and symlinks are rejected.
  Only game/world/state pages and direct cell/object/actor pages can be explicitly edited.
  Step records and loop lifecycle fields are tool-managed.
- Cell `objects`/`actors` and actor `carried_objects` are authoritative.
  Every entity must have exactly one holder of the correct type.
  Duplicate IDs, duplicate grid coordinates, missing references, and mismatched world indexes fail validation.
- A held object's `location` identifies the actor, not the actor's cell.
  The player's `current_cell` follows its cell membership.
- The tools apply established outcomes; they do not decide whether an action is reachable, whether a puzzle is solved,
  or whether the game is won.
  The game master remains responsible for those decisions and for updating descriptions/narration when facts change.
- Entity deletion, arbitrary asset writes, AI/media generation, an API client, and the agent loop runner are not
  implemented.
  Media metadata can be edited, but these tools do not certify that a referenced asset exists.
  Exceptional-connection semantics remain a future validator extension.
- Calls use a game-level file lock and revision hashes.
  All cooperating tool instances must use this protocol; unrelated editors do not honor the lock.

## Interrupted updates

Updates are validated before writing.
A `.pending-update/` directory stores `before.json` and `after.json` during a write batch.
Ordinary write exceptions trigger rollback.
A process crash or failed rollback leaves the journal and blocks subsequent tool calls, preventing another turn from
starting on partial state.

This is not a crash-atomic database transaction.
Recovery is deliberately manual for this initial implementation: stop all writers, inspect the journal, restore every
entry from `before.json` (null means the page did not exist and should be removed), and remove the journal only after
all restored files are checked.
An incomplete/missing journal requires inspection before continuing.
The revision will then reflect the restored state.
Do not merely delete the journal to bypass the guard.

## Tests

Run `./gradlew :tools:test` with the project's Java 23 toolchain.
Tests copy `games/ice_dungeon` into temporary directories and never mutate the source adventure.
They cover memberships, derived fields, inventories, no-op turns, page creation, stale revisions, role checks, path
isolation, YAML safety, protected history, and interrupted-update detection.

The initial verification used an external Gradle init script to select locally installed Java 21; the repository's Java
23 requirement was not changed.

## Markdown formatting

Document serialization uses the shared model formatter.
It writes at most 120 characters per line and starts each prose sentence on a new source line.
YAML output uses block style with wrapping enabled.
Formatting failures are detected before page writes.

Run `./gradlew :tools:formatMarkdown` to format repository Markdown.
Run `./gradlew :tools:checkMarkdown` to check it without writing.
The maintenance command excludes build outputs, dependency folders, and IDE/Git metadata.
