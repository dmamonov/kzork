---
id: ice-pick
# Derived from cell or inventory membership.
location: ../cells/cell-0-1.md
portable: true
---

# Iron Ice Pick

## Description

A short iron pick with a leather-wrapped handle.
Its tip is designed for chipping thin ice.

## Current state

Ready to take from the workbench.

## Interaction rules

When carried, use it on the ember to set the ember frozen field to false and
portable to true.
The pick remains intact.

Using a tool requires the target to be in the player’s current cell.
Taking requires the object to be portable and present in that cell.
Taking transfers membership from the cell objects list to
player.carried_objects; dropping reverses that transfer.
Recompute location automatically.
Use current membership to keep descriptive text accurate.

## Changelog

- Initialization: established the starting game content and state; no turns have
  been played.

- Formatting: wrapped source lines to 120 characters and placed each prose
  sentence on its own line.

- Formatting: reduced the maximum source line width to 80 characters.
