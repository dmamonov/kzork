---
id: cell-0-0
coordinates: {x: 0, y: 0}
world: ../world.md
objects:
  - ../objects/object-001.md
actors:
  - ../actors/player.md
  - ../actors/actor-001.md
media:
  cover_image: {path: ../media/images/cell-0-0-cover.png, status: not-generated}
  ambient_music: {path: ../media/music/cell-0-0-ambient.ogg, status: not-generated}
  intro_speech: {path: ../media/voice/cell-0-0-intro.ogg, status: not-generated}
---

# {{Cell name}}

## Description

{{Description of this stage as it currently exists.}}

## Intro message

{{Message narrated when the player enters this cell.}}

## Current state

{{Current environmental conditions and other mutable stage facts.}}

## Movement

{{Local movement conditions. Ordinary grid adjacency rules are still undecided. Exceptional connections are indexed in
world.md.}}

## Changelog

- Initialization: placed the sample object, actor, and player in this cell.

- Template revision: moved structured fields into YAML front matter.
