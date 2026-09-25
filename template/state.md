# Current game state

| Field             | Value                          |
|-------------------|--------------------------------|
| Game              | [Game](game.md)                |
| Player            | [Player](actors/player.md)     |
| Current cell      | [Cell 0, 0](cells/cell-0-0.md) |
| Loop phase        | awaiting-command               |
| Last applied step | None                           |
| Active step       | None                           |
| Next step number  | 0001                           |

## Global state

{{Global facts that do not belong to an individual cell, object, or actor.}}

## Pending work

None. During a turn, record any pending actor resolutions or file updates here, with a reference to its step record.

## Changelog

- Initialization: established the illustrative starting location and initial loop phase.
