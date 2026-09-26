---
id: cell-1-2
coordinates: {x: 1, y: 2}
world: ../world.md
# Authoritative placement references.
objects: []
actors:
  - ../actors/player.md
media:
  cover_image: {path: ../media/images/cell-1-2.png, status: not-generated}
  ambient_music: {path: ../media/music/ice-dungeon.ogg, status: not-generated}
  intro_speech: {path: ../media/voice/cell-1-2.ogg, status: not-generated}
---

# Entrance

## Description

Daylight spills through an arch at the glacier’s edge.
A dry path leads north; sheltered chambers open east and west.
This is the place to return with the sunstone.

## Intro message

Behind you lies the road to the village.
Ahead, blue corridors lead into the glacier.

## Current state

The outside road is scenery until the victory narration; there is no additional
cell beyond the grid.
Update descriptions and entry narration when their described facts change.

## Movement

- North: [Bell Court](cell-1-1.md).
- East: [Meltwater Pool](cell-2-2.md).
- West: [Snow Niche](cell-0-2.md).

## Changelog

- Initialization: established the starting game content and state; no turns have
  been played.

- Formatting: wrapped source lines to 120 characters and placed each prose
  sentence on its own line.

- Formatting: reduced the maximum source line width to 80 characters.
