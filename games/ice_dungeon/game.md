---
id: ice-dungeon
state_file: state.md
world: world.md
player: actors/player.md
media:
  cover_image: {path: media/images/game.png, status: not-generated}
  ambient_music: {path: media/music/ice-dungeon.ogg, status: not-generated}
  intro_speech: {path: media/voice/game.ogg, status: not-generated}
---

# The Ice Dungeon

## Description

The village hearth has gone cold. Its sunstone lies frozen in a small sanctuary beneath the glacier. Enter the nine chambers, release the stone, and bring it back to the entrance before beginning your journey home.

A short exploration adventure with two simple object puzzles and one helpful actor. There is no combat, hunger, time limit, or random damage. Ambient blue ice provides enough light everywhere.

## Intro speech

The village fire has dwindled to ash. Beneath this glacier sleeps the sunstone that once kept it burning. You step through the ice arch with empty hands. Somewhere ahead, a small bell rings.

## Rules for the game master

The player can move north, east, south, or west; look; examine; take; drop; talk; and use an object on another. Interpret equivalent natural-language commands. All orthogonally adjacent grid cells are connected in both directions. There are no diagonal moves, tunnels, or warps in this game.

The ice pick can free the ember from its ice socket. A carried ember can thaw the sunstone. Neither action consumes or damages its tool. Taking a frozen object fails with an explanation and does not change its state. After freeing or thawing, taking is a separate action. Repeat uses describe the existing result without additional effects.

The keeper may speak or offer hints when invited by the game master. It does not move, take objects, or solve puzzles for the player. Experimentation has no irreversible penalties. Essential objects cannot be destroyed or thrown outside the grid.

Win when the player is in cell-1-2 and carries the sunstone. Set state.md outcome to won and narrate the return of warmth. This is a resolved game-master outcome, not an automatic consequence of prose.

## Ending narration

At the entrance, daylight catches the sunstone in your hands. Warmth settles into your fingers. Far below the glacier, the village waits—and this time you have brought its dawn.

## Changelog

- Initialization: established the starting game content and state; no turns have been played.
