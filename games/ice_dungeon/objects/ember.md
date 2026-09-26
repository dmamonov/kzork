---
id: ember
# Derived from cell or inventory membership.
location: ../cells/cell-2-1.md
frozen: true
portable: false
---

# Living Ember

## Description

A smooth red stone that gives off gentle, persistent warmth.
A brittle crust currently fixes it inside its socket.

## Current state

Frozen into the alcove socket.

## Interaction rules

A carried ice pick frees it: set frozen to false and portable to true, then update the description and cell narration.
Once carried, it can thaw the sunstone without being consumed.
It is safe to handle.

Using a tool requires the target to be in the player’s current cell.
Taking requires the object to be portable and present in that cell.
Taking transfers membership from the cell objects list to player.carried_objects; dropping reverses that transfer.
Recompute location automatically.
Use current membership to keep descriptive text accurate.

## Changelog

- Initialization: established the starting game content and state; no turns have been played.

- Formatting: wrapped source lines to 120 characters and placed each prose sentence on its own line.
