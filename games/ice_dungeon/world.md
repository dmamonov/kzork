---
id: ice-dungeon-world
width: 3
height: 3
movement: orthogonal-adjacency
cells:
  - id: cell-0-0
    coordinates: { x: 0, y: 0 }
    page: cells/cell-0-0.md
  - id: cell-1-0
    coordinates: { x: 1, y: 0 }
    page: cells/cell-1-0.md
  - id: cell-2-0
    coordinates: { x: 2, y: 0 }
    page: cells/cell-2-0.md
  - id: cell-0-1
    coordinates: { x: 0, y: 1 }
    page: cells/cell-0-1.md
  - id: cell-1-1
    coordinates: { x: 1, y: 1 }
    page: cells/cell-1-1.md
  - id: cell-2-1
    coordinates: { x: 2, y: 1 }
    page: cells/cell-2-1.md
  - id: cell-0-2
    coordinates: { x: 0, y: 2 }
    page: cells/cell-0-2.md
  - id: cell-1-2
    coordinates: { x: 1, y: 2 }
    page: cells/cell-1-2.md
  - id: cell-2-2
    coordinates: { x: 2, y: 2 }
    page: cells/cell-2-2.md
exceptional_connections: [ ]
---

# Ice Dungeon Grid

## Coordinates and movement

The northwest cell is (0, 0). East increases x; south increases y. North decreases y; west decreases x. All nine cells
exist. Every orthogonally adjacent pair is traversable both ways; the outside boundary is solid rock. A move beyond the
grid leaves the player in place.

|       | x = 0        | x = 1              | x = 2              |
|-------|--------------|--------------------|--------------------|
| y = 0 | Frozen Store | Whispering Gallery | Sunstone Sanctuary |
| y = 1 | Tool Room    | Bell Court         | Ember Alcove       |
| y = 2 | Snow Niche   | Entrance           | Meltwater Pool     |

## Exceptional connections

None.

## Changelog

- Initialization: established the starting game content and state; no turns have been played.
