---
id: cell-1-1
coordinates: {x: 1, y: 1}
world: ../world.md
# Authoritative placement references.
objects: []
actors:
  - ../actors/keeper.md
media:
  cover_image: {path: ../media/images/cell-1-1.png, status: not-generated}
  ambient_music: {path: ../media/music/ice-dungeon.ogg, status: not-generated}
  intro_speech: {path: ../media/voice/cell-1-1.ogg, status: not-generated}
---

# Bell Court

## Description

A brass bell hangs above a circular floor.
Beside it waits a patient figure dressed in stitched white wool: the glacier
keeper.

## Intro message

A bell gives one clear note.
The keeper inclines their head in greeting.

## Current state

The bell is scenery.
Ringing it produces a note without changing puzzle state.
Update descriptions and entry narration when their described facts change.

## Movement

- North: [Whispering Gallery](cell-1-0.md).
- East: [Ember Alcove](cell-2-1.md).
- South: [Entrance](cell-1-2.md).
- West: [Tool Room](cell-0-1.md).

## Changelog

- Initialization: established the starting game content and state; no turns have
  been played.

- Formatting: wrapped source lines to 120 characters and placed each prose
  sentence on its own line.

- Formatting: reduced the maximum source line width to 80 characters.
