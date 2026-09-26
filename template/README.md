# Game storage template

This folder drafts the storage structure described in [design.md](../design.md).
It is not a playable game.
Names, IDs, and a two-coordinate grid are illustrative choices.
Structured fields use YAML front matter at the top of each game data page.

Start with [game.md](game.md), [state.md](state.md), and [world.md](world.md).
One sample cell references one object, one actor, and the player.
[step0001.md](steps/step-0001.md) is an empty turn template, not a completed
turn.

Replace `{{...}}` placeholders when creating actual content.
Duplicate entity pages with unique IDs and update their relative paths.
Keep each page's current content and bottom changelog in sync.
Update media metadata status when the referenced files exist; see
[media guidance](media/README.md).

Cell occupant references establish placement.
Entity `location` fields and `state.current_cell` are derived automatically; see
the formal-logic update rules in [design.md](../design.md).
Inventory membership is a proposed extension.

## Changelog

- Initialization: created the storage draft.

- Template revision: documented YAML header metadata and media references.

- Template revision: documented authoritative membership and derived locations.

- Formatting: wrapped source lines to 120 characters and placed each prose
  sentence on its own line.

- Formatting: reduced the maximum source line width to 80 characters.
