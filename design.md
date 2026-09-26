# Game design

This document records the agreed game model and agent loop.
The storage layout and file templates are drafts for discussion.
No engine, tools, or UI are implemented by this document.

## Game model

The world is a grid of cells.
Each cell is a stage with its own description, objects, and actors.
Movement normally follows the grid; a tunnel or warp is an exceptional connection.
Whether adjacent cells are traversable by default remains undecided.

Each cell has a cover image, ambient music, and an intro message narrated when the player enters.
The whole game similarly has a description, cover image, music, and narrated intro speech for its beginning.

Each object has its own Markdown page describing it and its current state.
Each actor, including the player, has the same kind of individual page.

All game data is stored in Markdown files on the filesystem.
Images, music, sound effects, and generated speech are separate media files referenced by those pages.

Every page can change during play.
A change updates its actual content and adds an entry to a `Changelog` section at the bottom of that page.
The body describes current truth; the changelog records changes.

A separate current game state file anchors the game loop.
A separate `stepXXXX.md` file records each turn.

## Game loop and responsibilities

1. The player submits a command.
2. The game master agent evaluates it and describes what happens.
3. The game master may allow selected actors to act.
4. Those actor agents state their intended actions.
5. The game master resolves each actor action into an outcome.
6. Once the turn's events have been resolved, they and the game master's update instructions are recorded in
   `stepXXXX.md`.
7. A separate state-update agent uses tools to update the actual files according to those instructions, including page
   changelogs and the current game state file.
8. Images, sounds, and voice messages may be generated to support presentation of the step.

Actor proposals are not established events until resolved by the game master.
The state-update agent applies established outcomes; it does not decide new story outcomes.

## Draft storage layout

`template/` illustrates one game instance.
It contains placeholders, not a playable adventure.
A separate authored source and per-playthrough copies were proposed but are not yet agreed; this layout does not assume
that separation.

```text
template/
  README.md
  game.md
  state.md
  world.md
  cells/
    cell-0-0.md
  objects/
    object-001.md
  actors/
    player.md
    actor-001.md
  steps/
    step0001.md
  media/
    README.md
    images/
    music/
    sounds/
    voice/
```

| File | Responsibility |
|---|---|
| `game.md` | Overall game description, cover, music, and intro speech |
| `state.md` | Current step, loop phase, player reference, current cell, and global state |
| `world.md` | Grid coordinate convention, cell index, and exceptional connections |
| `cells/*.md` | Stage description, presentation, current state, and references to occupants |
| `objects/*.md` | Individual object description, location, and current state |
| `actors/*.md` | Individual actor description, location, current state, and carried objects; includes player |
| `steps/stepXXXX.md` | Command, proposals, outcomes, update instructions, application status, and media |
| `media/` | Referenced presentation assets |

### Draft Markdown conventions

- Markdown source lines contain at most 120 Unicode characters.
- Each prose sentence starts on a new source line, even when the preceding line has room.
- Long sentences wrap at word boundaries; list continuations retain their indentation.
- YAML, code blocks, tables, links, and inline code retain their syntax.
  Indivisible content exceeding the limit must be restructured rather than split unsafely.

- Store structured fields in YAML front matter between `---` delimiters at the very beginning of each game data page.
  Use metadata for IDs, references, coordinates, loop status, inventories, media, and other structured state properties.
  Keep descriptions, narration, narrative state, and changelogs in the Markdown body.
  No separate database or JSON state is proposed.
- Each game entity has a stable ID.
  Display names and descriptions may change without changing its ID.
- Metadata references are plain relative path strings, resolved from the containing file.
  Body references use relative Markdown links.
  Media metadata records a path and status; `not-generated` paths are planned assets, not existing files.
- Use YAML `null` for absent values and `[]` for empty lists.
  Step numbers are integers, padded to four digits in filenames (for example, `1` becomes `step0001.md`).
  Quote textual placeholders in metadata.
  Avoid duplicating structured fields in body tables or lists.
- `{{...}}` marks content to supply.
  Placeholder values are not actual game state.
- Each page ends with `## Changelog`.
  Entries identify the step, what changed, and why.
  Initialization is identified separately.
- The templates show possible fields, not a finalized parser schema.
  New state properties can be introduced as needed; constraints will be designed with the tools.

### State ownership and consistency: proposed

Entity pages own their descriptions and local state.
`state.md` holds global state and loop progress rather than copying every entity's state.
`world.md` indexes the grid and special connections.

Cell occupant references are authoritative for placement.
An entity's `location` is derived automatically from those references, including for actors and the player.
The updater changes membership; formal rules maintain the corresponding location fields.
`state.md.current_cell` is derived from the player's placement.

### Formal-logic update

Every turn has a deterministic consistency stage after its explicit changes.
The game master determines outcomes; the state-update agent translates them into source-data changes; formal rules
derive dependent fields.
This stage does not invent events or interpret narrative prose.

Only designated YAML membership fields establish placement.
A mention in a description, step, changelog, or ordinary Markdown link does not place an entity anywhere.
Resolve relative references to the same entity before comparing them.

For a cell `c` and entity `e` (object or actor, including the player):

```text
cell_member(c, e) := e is referenced in c.objects or c.actors
cell_member(c, e) => location(e) = c
current_cell := location(player)
```

`location` and `current_cell` are materialized derived fields: the automatic stage writes their current values into
Markdown header metadata.
They are not independent inputs.
Changing an entity's `location` alone cannot move it; it is recomputed from membership.

Proposed extension for the existing inventory model:

```text
inventory_member(a, o) := object o is referenced in actor a.carried_objects
inventory_member(a, o) => location(o) = a
inventory_member(a, o) and location(a) = c => effective_cell(o) = c
```

Here `location` means the immediate holder; `effective_cell` is the inferred cell of a carried object, not an additional
stored field.
A carried object is not also listed among the cell's loose objects.
Inventory rules remain a draft extension of the agreed cell-reference rule.

Draft validation rules:

- An actor, including the player, belongs to exactly one cell.
  A present object belongs to exactly one cell or actor inventory.
- Multiple holders are a conflict; do not choose a location arbitrarily.
  Missing membership is also a validation failure until rules for absent or destroyed entities are designed.
- Referenced entities must exist and match their list's type (`objects`, `actors`, or `carried_objects`).
- Validate the complete proposed changes together, so removing an old membership and adding a new one form one move.

Example: moving the player from cell A to cell B removes its reference from A's `actors` and adds it to B's `actors`.
The formal stage automatically updates `player.location` and `state.current_cell`.
Each changed page, including automatically changed pages, receives a changelog entry identifying the step and the source
change or rule responsible.

The update order is: prepare explicit changes, validate memberships, derive dependent fields, record all changed pages
and changelogs, then mark the step applied.
A failed validation prevents the turn from being marked applied.
Rerunning derivation on unchanged inputs produces no additional field changes or duplicate changelog entries.
Atomic storage and failure recovery mechanisms remain to be designed.

The step record distinguishes proposed actions, resolved outcomes, and applied changes.
Proposed phases in `state.md` are `awaiting-command`, `resolving`, and `applying`.
Only after all required changes are applied should the next command begin.
Exact handling of interrupted updates, retries, and partial failures remains to be designed.

Optional generated media must be linked from the relevant page or step when available.
A pending asset must not be presented as an existing file.
Whether media generation blocks presentation or happens in the background remains open.

## Tools: design scope, not an approved API

The updater needs ways to read pages, create pages, update their contents, and append changelog entries.
Applying a step may involve several linked pages.
We still need to decide how tools validate references and prevent a partially applied turn from becoming the next turn's
starting state.

Candidate operations for discussion:

| Capability | Purpose |
|---|---|
| Read and list pages | Obtain current state and follow references |
| Find by ID or grid coordinate | Locate a known entity or cell |
| Search text | Discover relevant descriptions or history when the exact page is unknown |
| Create or update a page with a changelog entry | Persist a game-master outcome |
| Move an entity | Change authoritative membership references |
| Apply formal rules | Validate membership and automatically derive locations and the player's current cell |
| Record and apply a step | Relate resolved events to their persistent updates |
| Generate and attach media | Supply images, audio, and narration |

Search is undecided.
Direct references and an index may suffice for a small world; text search may help with larger worlds and step
histories.
Semantic search is not assumed.

## UI/UX: agreed content and open choices

The player sends commands and receives the description of what happens.
The current cell supplies a cover image, ambient music, and narrated entry message.
The game supplies its own opening presentation.
Step-specific images, sounds, and voice can support the events.

Still to design: screen layout, command input, inspection of objects and actors, turn history, agent activity
indicators, audio controls, transitions between cells, and which information is visible to the player versus private to
agents.
Markdown storage does not imply that every page is exposed to the player.

## Further decisions

Discuss one area at a time, beginning with the storage draft:

1. Grid dimensionality, coordinate convention, and ordinary movement rules.
2. Authored game versus independent playthrough storage.
3. Required page fields, reference ownership, and changelog format.
4. Update tools, search, validation, and interrupted-turn recovery.
5. Player UI and media presentation.
6. Adventure generation and automatic play evaluation, after the model and loop are settled.

## Changelog

- Initial draft: consolidated the agreed model and turn loop; added explicitly provisional storage, tool, and UI design
  notes.

- Design revision: adopted YAML header metadata for structured fields and documented reference, media, and value
  conventions.

- Design revision: made cell membership authoritative and documented formal derivation of locations, with draft
  inventory and validation rules.

- Formatting: wrapped source lines to 120 characters and placed each prose sentence on its own line.
