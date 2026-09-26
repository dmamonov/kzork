# Browser game runtime

`GameSession` serializes turns and owns one saved playthrough.
`GameFiles` reads the Markdown world and produces a public scene.
The browser polls for appended events and activity status.

## Agents

`AiTurnEngine` plans with the game master, requests proposals from up to two
present actors, and asks the game master to resolve them.
It records the resolved turn with the existing tools.
A separate updater agent reads that turn and uses `apply_step` to commit it.
The game loop only displays completed outcomes after application succeeds.

The player agent receives the objective, current public scene, and visible
transcript.
It receives no filesystem tools or hidden world context.
The offline engine and guide are explicit demo implementations for the ice
dungeon.

## Persistence

Each session directory contains:

```text
session.md
world/
  game.md
  state.md
  cells/
  actors/
  objects/
  steps/
events/
  event-000000.md
```

World pages use the existing validated update protocol.
Chat events are separate Markdown files written with atomic file replacement.
Keeping chat outside `world/` avoids invalidating a pending world revision.
On resume, the runtime publishes a committed turn missing from the transcript.
It does not automatically resume paid agent play.

The demo temporarily stores its update plan in `demo-pending.md` and
`demo-plan/` so a failed update can be retried.
The source game is never used as the mutable playthrough.

## Browser boundaries

Ktor binds to `127.0.0.1`.
API requests require a per-process token embedded in the local page.
The server exposes only public session data, web resources, and game media.
Hidden game pages, filesystem tools, and API credentials are not HTTP routes.
Game and model text is inserted as text, not executable HTML.

This is a single-user local application, not a public hosted service.
Do not run two servers against the same saved session.

## Limits

Live model behavior is probabilistic.
The game master decides action validity; tools enforce structural consistency.
The initial world loader caps context at 180,000 characters.
Actor invitations are limited to actors present before the player command.
Generalized exceptional-connection validation and media generation are future
extensions.
