# Game storage template

This folder drafts the storage structure described in [design.md](../design.md). It is not a playable game. Names, IDs,
and a two-coordinate grid are illustrative choices. Structured fields use YAML front matter at the top of each game data page.

Start with [game.md](game.md), [state.md](state.md), and [world.md](world.md). One sample cell references one object,
one actor, and the player. [step0001.md](steps/step-0001.md) is an empty turn template, not a completed turn.

Replace `{{...}}` placeholders when creating actual content. Duplicate entity pages with unique IDs and update their
relative paths. Keep each page's current content and bottom changelog in sync. Update media metadata status when the referenced files exist; see [media guidance](media/README.md).

## Changelog

- Initialization: created the storage draft.

- Template revision: documented YAML header metadata and media references.
