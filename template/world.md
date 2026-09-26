---
cells:
  - id: cell-0-0
    coordinates: {x: 0, y: 0}
    page: cells/cell-0-0.md
exceptional_connections: []
---

# World grid

## Coordinate convention

Draft: cells have integer `(x, y)` coordinates.
Axis directions, dimensionality, and rules for traversing adjacent cells are not
yet decided.

## Exceptional connections

None in this template.
For each tunnel or warp, record its ID, source cell, destination cell,
directionality, description, and current conditions.
These supplement ordinary grid movement.

## Changelog

- Initialization: indexed the sample cell; no exceptional connections created.

- Template revision: moved structured fields into YAML front matter.

- Formatting: wrapped source lines to 120 characters and placed each prose
  sentence on its own line.

- Formatting: reduced the maximum source line width to 80 characters.
