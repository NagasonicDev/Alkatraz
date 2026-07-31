# Research Graph Connector Textures — Design Spec

## Overview

Replace the current plain-colored glass pane edge rendering in `ResearchGraphMenu` with textured connector pieces. The system uses only **straight** and **corner** (90°) piece types, with all edge paths using exclusively cardinal (horizontal/vertical) segments. This simplifies the texture set to 30 textures and gives the graph a clean, grid-like visual appearance.

## Part 1: Texture System

### Piece Types (6 total)

All segments are cardinal. There are no diagonal segments.

| Piece | Key | Connects | Visual |
|-------|-----|----------|--------|
| Horizontal straight | `straight_h` | E ↔ W | `—` |
| Vertical straight | `straight_v` | N ↔ S | `│` |
| NE corner | `corner_ne` | N and E | `└` |
| SE corner | `corner_se` | S and E | `┌` |
| SW corner | `corner_sw` | S and W | `┐` |
| NW corner | `corner_nw` | N and W | `┘` |

### State Variants (5 per piece)

Each piece type exists in 5 states matching `ResearchState`:

| State | Style suffix | Purpose |
|-------|-------------|---------|
| COMPLETED | `_completed` | Both parent and child completed |
| AVAILABLE | `_available` | Parent completed, child available |
| IN_PROGRESS | `_in_progress` | Parent completed, child in progress |
| LOCKED | `_locked` | Parent not completed (or child locked) |
| HIDDEN | `_hidden` | Parent hidden |

### Texture Count

6 piece types × 5 states = **30 textures**

### CMD Assignment

```yaml
connector_textures:
  straight_h_completed: 6001
  straight_h_available: 6002
  straight_h_in_progress: 6003
  straight_h_locked: 6004
  straight_h_hidden: 6005
  straight_v_completed: 6006
  straight_v_available: 6007
  straight_v_in_progress: 6008
  straight_v_locked: 6009
  straight_v_hidden: 6010
  corner_ne_completed: 6011
  corner_ne_available: 6012
  corner_ne_in_progress: 6013
  corner_ne_locked: 6014
  corner_ne_hidden: 6015
  corner_se_completed: 6016
  corner_se_available: 6017
  corner_se_in_progress: 6018
  corner_se_locked: 6019
  corner_se_hidden: 6020
  corner_sw_completed: 6021
  corner_sw_available: 6022
  corner_sw_in_progress: 6023
  corner_sw_locked: 6024
  corner_sw_hidden: 6025
  corner_nw_completed: 6026
  corner_nw_available: 6027
  corner_nw_in_progress: 6028
  corner_nw_locked: 6029
  corner_nw_hidden: 6030
```

### Material

All connector pieces use `GRAY_STAINED_GLASS_PANE` as the base material (same as the `blank` pane), with custom model data to select the correct texture from the resource pack. The color variation comes from the texture, not the material.

## Part 2: Edge Material Logic (Color Assignment)

The existing `getEdgeMaterial()` method is replaced with a state-based system that maps `(parentState, childState)` → state string for texture lookup:

| Parent State | Child State | Connector State |
|-------------|-------------|-----------------|
| COMPLETED | COMPLETED | `completed` |
| COMPLETED | AVAILABLE | `available` |
| COMPLETED | IN_PROGRESS | `in_progress` |
| COMPLETED | LOCKED / HIDDEN | `locked` |
| IN_PROGRESS / LOCKED / AVAILABLE | any | `locked` |
| HIDDEN | any | `hidden` |

This matches the current color mapping exactly (LIME→completed, YELLOW→available, CYAN→in_progress, RED→locked, GRAY→hidden), but instead of selecting different materials, it selects the appropriate texture variant.

## Part 3: Edge Path Redesign (research.yml)

All edge paths are converted to all-cardinal segments. Each waypoint creates a clean 90° corner.

### Direction Convention

Cardinal directions from parent→child at each segment:
- **N**: dy = -1 (up on screen)
- **S**: dy = +1 (down on screen)
- **E**: dx = +1 (right on screen)
- **W**: dx = -1 (left on screen)

### Pattern A — Central Branch (magic only, 3 edges)

**A1: disciplined_channel (0,3) → mana_conservation (0,-3)**
- Old: [(1,-2), (1,1)] → NE→S→NW (135° turns)
- New: [(1,3), (1,-3)] → E→S→W (90° corners)
- Path: (0,3)→E→(1,3)→S→(1,-3)→W→(0,-3)
- Avoids: awakened_focus at (0,0)

**A2: disciplined_channel (0,3) → ritual_geometry (-3,-2)**
- Old: [(-2,-1), (-2,1)] → NW→S→NW (135° turns)
- New: [(-3,3)] → W→S (1 corner)
- Path: (0,3)→W→(-3,3)→S→(-3,-2)

**A3: disciplined_channel (0,3) → spellcraft_field_notes (3,-2)**
- Old: [(2,-1), (2,1)] → NE→S→NE (135° turns)
- New: [(3,3)] → E→S (1 corner)
- Path: (0,3)→E→(3,3)→S→(3,-2)

### Pattern B — Ward to Root (7 edges across all branches)

**B: ward_craft (-3,1) → awakened_focus (0,0)**
- Old: [(-2,0)] → NE→E (45° transition)
- New: [(-3,0)] → N→E (90° corner)
- Path: (-3,1)→N→(-3,0)→E→(0,0)

### Pattern C — Straight-through (2 edges)

**C: mana_reservoir (0,-6) → ritual_geometry (-3,-2)**
- Old: [(-1,-5)] → SW→SW (straight diagonal)
- New: [(-3,-6)] → W→S (90° corner)
- Path: (0,-6)→W→(-3,-6)→S→(-3,-2)

### Pattern D — Conduit to Discipline (7 edges)

**D: mana_conduit (0,6) → disciplined_channel (0,3)**
- Old: [(1,4), (1,5)] → NE→S→NW (135° turns)
- New: [(1,6), (1,3)] → E→S→W (90° corners)
- Path: (0,6)→E→(1,6)→S→(1,3)→W→(0,3)

### Pattern E — Grand Ward to Barrier (7 edges)

**E: grand_ward (-8,5) → barrier_weave (-5,3)**
- Old: [(-7,4), (-8,4)] → NE→W→NE (135° turns)
- New: [(-8,4), (-5,4)] → N→E→N (90° corners)
- Path: (-8,5)→N→(-8,4)→E→(-5,4)→N→(-5,3)

### Pattern F — Mastery to Pyroclasm (6 edges)

**F: inferno_mastery (0,-9) → pyroclasm (5,-3)**
- Old: [(1,-7), (1,-8)] → SE→N→SE (135° turns)
- New: [(1,-9), (1,-3)] → E→N→E (90° corners)
- Path: (0,-9)→E→(1,-9)→N→(1,-3)→E→(5,-3)

### Pattern G — Barrier to Discipline (7 edges)

**G: barrier_weave (-5,3) → disciplined_channel (0,3)**
- Old: [(0,2), (-3,1), (-5,2)] → NE→NW→SW→SE (90° diagonal corners)
- New: [(-5,2), (0,2)] → N→E→S (90° cardinal corners)
- Path: (-5,3)→N→(-5,2)→E→(0,2)→S→(0,3)

### Pattern H — Barrier to Ward (7 edges)

**H: barrier_weave (-5,3) → ward_craft (-3,1)**
- Old: [(-4,1), (-5,2)] → NE→SW→NE (180° reversal)
- New: [(-5,1)] → N→E (90° corner)
- Path: (-5,3)→N→(-5,1)→E→(-3,1)

### Pattern I — Sovereignty to Theory (magic only)

**I: mana_sovereignty (0,-9) → arcane_theory (-5,-3)**
- Old: [(-1,-7), (-1,-8)] → SW→N→SW (135° turns)
- New: [(-1,-9), (-1,-3)] → W→N→W (90° corners)
- Path: (0,-9)→W→(-1,-9)→N→(-1,-3)→W→(-5,-3)

### Edges Without edge_paths (cardinal L-routes)

These edges currently have no `edge_paths` and use diagonal straight lines. They need new L-shaped routes:

| Edge | Child → Parent | New Waypoint | Direction |
|------|---------------|--------------|-----------|
| ritual_geometry→awakened_focus | (-3,-2)→(0,0) | [(-3,0)] | S→E |
| spellcraft→awakened_focus | (3,-2)→(0,0) | [(3,0)] | S→W |
| focus_discipline→awakened_focus | (3,1)→(0,0) | [(3,0)] | N→W |
| spell_resonance→spellcraft | (5,-3)→(3,-2) | [(5,-2)] | N→W |
| combat_casting→focus_discipline | (5,3)→(3,1) | [(5,1)] | N→W |
| arcane_theory→ritual_geometry | (-5,-3)→(-3,-2) | [(-5,-2)] | N→E |
| arcane_theory→ward_craft | (-5,-3)→(-3,1) | [(-5,1)] | S→E |
| archmage→arcane_theory | (-8,-5)→(-5,-3) | [(-8,-3)] | S→E |
| archmage→barrier_weave | (-8,-5)→(-5,3) | [(-8,3)] | S→E |
| mana_conduit→ward_craft | (0,6)→(-3,1) | [(-3,6)] | W→N |
| grand_ward→mana_conduit | (-8,5)→(0,6) | [(-8,6)] | S→E |
| battle_mage→combat_casting | (8,5)→(5,3) | [(8,3)] | N→W |
| battle_mage→spell_resonance | (8,5)→(5,-3) | [(8,-3)] | N→W |

## Part 4: Java Code Changes

### TexturePackManager.java

Add a fourth cache for connector CMDs:

```java
private static final Map<String, Integer> connectorCMDCache = new HashMap<>();

private static void loadConnectorCMDs() {
    ConfigurationSection section = config.getConfigurationSection("connector_textures");
    if (section == null) return;
    for (String key : section.getKeys(false)) {
        connectorCMDCache.put(key, section.getInt(key));
    }
}

public static int getConnectorCMD(String pieceKey) {
    return connectorCMDCache.getOrDefault(pieceKey, 0);
}
```

Call `loadConnectorCMDs()` from the existing `load()` method.

### ResearchGraphMenu.java — Core Rendering Rewrite

#### New Direction Enum

```java
private enum Direction { N, S, E, W }
```

#### New Piece Type Determination

```java
private enum PieceType {
    STRAIGHT_H, STRAIGHT_V,
    CORNER_NE, CORNER_SE, CORNER_SW, CORNER_NW
}

private PieceType getPieceType(Direction incoming, Direction outgoing) {
    if (incoming == outgoing) {
        return (incoming == Direction.E || incoming == Direction.W)
            ? PieceType.STRAIGHT_H : PieceType.STRAIGHT_V;
    }
    return switch (incoming) {
        case N -> outgoing == Direction.E ? PieceType.CORNER_NE : PieceType.CORNER_NW;
        case S -> outgoing == Direction.E ? PieceType.CORNER_SE : PieceType.CORNER_SW;
        case E -> outgoing == Direction.N ? PieceType.CORNER_NE : PieceType.CORNER_SE;
        case W -> outgoing == Direction.N ? PieceType.CORNER_NW : PieceType.CORNER_SW;
    };
}
```

#### New Direction Calculation

```java
private Direction getDirection(int fromX, int fromY, int toX, int toY) {
    int dx = Integer.compare(toX, fromX);
    int dy = Integer.compare(toY, fromY);
    if (dx == 1) return Direction.E;
    if (dx == -1) return Direction.W;
    if (dy == 1) return Direction.S;
    return Direction.N;
}
```

#### Rewritten drawSegment

```java
private void drawStraightSegment(int fromX, int fromY, int toX, int toY, String stateKey) {
    Direction dir = getDirection(fromX, fromY, toX, toY);
    PieceType piece = (dir == Direction.E || dir == Direction.W)
        ? PieceType.STRAIGHT_H : PieceType.STRAIGHT_V;

    int dx = (dir == Direction.E) ? 1 : (dir == Direction.W) ? -1 : 0;
    int dy = (dir == Direction.S) ? 1 : (dir == Direction.N) ? -1 : 0;

    int x = fromX + dx;
    int y = fromY + dy;
    while (x != toX || y != toY) {
        slotFor(x, y).ifPresent(slot -> {
            if (isBlankSlot(slot)) {
                inventory.setItem(slot, createConnectorItem(piece, stateKey));
            }
        });
        if (x != toX) x += dx;
        if (y != toY) y += dy;
    }
}
```

#### Rewritten drawEdgeWithWaypoints

```java
private void drawEdgeWithWaypoints(ResearchNode parent, ResearchNode child, List<int[]> waypoints) {
    ResearchState parentState = ResearchService.getState(viewer, parent);
    ResearchState childState = ResearchService.getState(viewer, child);
    String stateKey = getStateKey(parentState, childState);

    int px = parent.getX(), py = parent.getY();
    int cx = child.getX(), cy = child.getY();

    List<int[]> fullPath = new ArrayList<>();
    fullPath.add(new int[]{px, py});
    fullPath.addAll(waypoints);
    fullPath.add(new int[]{cx, cy});

    // Draw straight segments between consecutive points
    for (int i = 0; i < fullPath.size() - 1; i++) {
        int[] from = fullPath.get(i);
        int[] to = fullPath.get(i + 1);
        drawStraightSegment(from[0], from[1], to[0], to[1], stateKey);
    }

    // Draw corner pieces at waypoints
    for (int i = 1; i < fullPath.size() - 1; i++) {
        int[] prev = fullPath.get(i - 1);
        int[] wp = fullPath.get(i);
        int[] next = fullPath.get(i + 1);
        Direction in = getDirection(prev[0], prev[1], wp[0], wp[1]);
        Direction out = getDirection(wp[0], wp[1], next[0], next[1]);
        placeCornerAt(wp[0], wp[1], in, out, stateKey);
    }
}
```

#### drawCardinalEdge (for edges without edge_paths)

```java
private void drawCardinalEdge(ResearchNode parent, ResearchNode child) {
    ResearchState parentState = ResearchService.getState(viewer, parent);
    ResearchState childState = ResearchService.getState(viewer, child);
    String stateKey = getStateKey(parentState, childState);

    int px = parent.getX(), py = parent.getY();
    int cx = child.getX(), cy = child.getY();

    if (px == cx || py == cy) {
        drawStraightSegment(px, py, cx, cy, stateKey);
        return;
    }

    // L-route: parent → (cx, py) → child
    drawStraightSegment(px, py, cx, py, stateKey);
    Direction hDir = (cx > px) ? Direction.E : Direction.W;
    Direction vDir = (cy > py) ? Direction.S : Direction.N;
    placeCornerAt(cx, py, hDir, vDir, stateKey);
    drawStraightSegment(cx, py, cx, cy, stateKey);
}
```

## Part 5: Implementation Order

1. Add `connector_textures` section to `texturepack.yml` (30 CMD entries)
2. Add connector CMD loading to `TexturePackManager.java`
3. Rewrite `ResearchGraphMenu.java` — new Direction enum, PieceType, helpers
4. Rewrite `ResearchGraphMenu.java` — drawSegment, drawEdge, drawEdgeWithWaypoints
5. Update `research.yml` — magic category edge_paths
6. Update `research.yml` — elemental categories edge_paths

## Part 6: Verification

- Build compiles clean (`mvn compile`)
- All 7 categories render correctly with cardinal paths
- No overlapping edge items at node positions
- State-based coloring still works (completed=green, available=yellow, etc.)
- Panning/scrolling still works correctly with the new edge paths
