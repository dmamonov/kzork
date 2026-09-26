---
id: cell-0-1
coordinates: {x: 0, y: 1}
world: ../world.md
# Authoritative placement references.
objects:
  - ../objects/ice-pick.md
actors: []
media:
  cover_image: {path: ../media/images/cell-0-1.png, status: not-generated}
  ambient_music: {path: ../media/music/ice-dungeon.ogg, status: not-generated}
  intro_speech: {path: ../media/voice/cell-0-1.ogg, status: not-generated}
---

# Tool Room

## Description

A low workbench stands beside an open doorway.
A short iron ice pick rests on it, its handle wrapped in dry leather.

## Intro message

You enter a cramped workshop.
An iron pick waits on the workbench.

## Current state

The ice pick is portable.
If removed, describe the empty workbench instead.
Update descriptions and entry narration when their described facts change.

## Movement

- North: [Frozen Store](cell-0-0.md).
- East: [Bell Court](cell-1-1.md).
- South: [Snow Niche](cell-0-2.md).

## Changelog

- Initialization: established the starting game content and state; no turns have
  been played.

- Formatting: wrapped source lines to 120 characters and placed each prose
  sentence on its own line.

- Formatting: reduced the maximum source line width to 80 characters.
