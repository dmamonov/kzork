---
game: game.md
player: actors/player.md
# Derived from authoritative cell membership.
current_cell: cells/cell-1-2.md
loop_phase: awaiting-command
last_applied_step: null
active_step: null
next_step_number: 1
pending_work: []
outcome: in-progress
---

# Current Game State

## Global state

The adventure has not begun.
Recover the sunstone and carry it back to the Entrance.
Puzzle state is stored on the ember and sunstone pages.

## Formal update rules

Cell objects and actors lists establish direct placement.
Actor carried_objects lists establish inventory placement.
After applying explicit membership changes, derive every entity's location from
its sole holder and derive current_cell from the player's cell.
A carried object's location references its actor; its effective cell follows
that actor.

Validate that each entity has exactly one holder, references exist, and actor
references belong in actors lists.
Apply removals and additions together.
Recompute location fields automatically; never move an entity by editing only
its location.
Log every changed page, including derived changes, against the same step.
Do not advance the loop on a conflict.
These are game rules for the future updater, not an implemented tool.

## Changelog

- Initialization: established the starting game content and state; no turns have
  been played.

- Formatting: wrapped source lines to 120 characters and placed each prose
  sentence on its own line.

- Formatting: reduced the maximum source line width to 80 characters.
