---
id: sunstone
# Derived from cell or inventory membership.
location: ../cells/cell-2-0.md
frozen: true
portable: false
---

# Sunstone

## Description

A palm-sized golden stone sealed within transparent ice on the sanctuary altar.

## Current state

Frozen to the altar.

## Interaction rules

A carried ember thaws it: set frozen to false and portable to true, then update the description and cell narration. Once thawed, the player may take it and return to the Entrance to win.

Using a tool requires the target to be in the player’s current cell. Taking requires the object to be portable and present in that cell. Taking transfers membership from the cell objects list to player.carried_objects; dropping reverses that transfer. Recompute location automatically. Use current membership to keep descriptive text accurate.

## Changelog

- Initialization: established the starting game content and state; no turns have been played.
