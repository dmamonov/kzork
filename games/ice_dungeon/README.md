---
id: ice-dungeon-readme
---

# Ice Dungeon

A simple 3×3 adventure stored as Markdown.
Begin with [game.md](game.md); current state is in [state.md](state.md) and the map in [world.md](world.md).

## Starting a playthrough

The player begins at (1, 2).
Follow the agent loop in the root design document.
No turns have been played and steps/ is empty.
Record the first actual turn as steps/step-0001.md; do not treat a walkthrough as a played step.

Membership is authoritative.
Location headers are initialized consistently but require a future formal updater or careful application of the
documented rules.
No game engine is included.

## Media

Cover images, ambient music, and intro audio have planned paths with not-generated status.
Narration text is written for the game and all nine cells.
Media production directions are in [media/README.md](media/README.md); no binary media assets are included.

## Game-master walkthrough (spoilers)

From the Entrance: north to Bell Court, west to Tool Room, take ice pick, east to Bell Court, east to Ember Alcove, use
pick on ember, take ember, north to Sanctuary, use ember on sunstone, take sunstone, south to Alcove, south to Pool,
west to Entrance.
Resolve victory while carrying the sunstone.

Optional exploration: the Frozen Store explains both puzzles, the Snow Niche points toward the tool, and the Pool points
toward the ember.
The keeper can offer hints.
A wrong action does not consume any required item.

## Changelog

- Initialization: established the starting game content and state; no turns have been played.

- Formatting: wrapped source lines to 120 characters and placed each prose sentence on its own line.
