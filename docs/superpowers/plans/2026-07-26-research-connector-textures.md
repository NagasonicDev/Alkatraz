# Research Graph Connector Textures — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace plain glass pane edges in the research graph with textured cardinal connector pieces (straights + 90° corners), totaling 30 textures.

**Architecture:** All edge paths converted to cardinal (horizontal/vertical) segments. `TexturePackManager` loads connector CMDs. `ResearchGraphMenu` rewritten to track direction and place correct piece type (straight or corner) at each slot.

**Tech Stack:** Java 17+, Bukkit/Paper API, SnakeYAML (config), Maven build

## Global Constraints
- Java 17+ (switch expressions, pattern matching)
- Bukkit API (Material, ItemStack, Inventory)
- All connector pieces use `GRAY_STAINED_GLASS_PANE` as base material
- 30 textures: 6 piece types × 5 states, CMD range 6001–6030
- No diagonal segments — all cardinal only

## Files to Modify

| File | Change |
|------|--------|
| `core/src/main/resources/texturepack.yml` | Add `connector_textures` section (30 CMDs) |
| `core/src/main/java/me/nagasonic/alkatraz/texturepack/TexturePackManager.java` | Add connector CMD cache + loading |
| `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java` | Rewrite edge rendering (Direction, PieceType, drawSegment, drawEdge) |
| `core/src/main/resources/research.yml` | Add/update edge_paths for all categories |

---

### Task 1: Add connector textures to texturepack.yml

**Files:**
- Modify: `core/src/main/resources/texturepack.yml`

**Interfaces:**
- Produces: `connector_textures` section readable by `TexturePackManager.getConnectorCMD(String)`

- [ ] **Step 1: Add connector_textures section**

Add at the end of `texturepack.yml`:

```yaml
# Connector textures for research graph edges (6001-6030)
# Piece types: straight_h, straight_v, corner_ne, corner_se, corner_sw, corner_nw
# States: completed, available, in_progress, locked, hidden
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

- [ ] **Step 2: Verify YAML parses correctly**

Run: `mvn compile -pl core` from project root
Expected: BUILD SUCCESS (validates YAML syntax)

- [ ] **Step 3: Commit**

```bash
git add core/src/main/resources/texturepack.yml
git commit -m "feat: add connector texture CMDs to texturepack.yml"
```

---

### Task 2: Add connector CMD loading to TexturePackManager

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/texturepack/TexturePackManager.java`

**Interfaces:**
- Produces: `TexturePackManager.getConnectorCMD(String pieceKey)` → int (CMD value)

- [ ] **Step 1: Add connectorCMDCache field**

After the existing `guiMaterialCache` field, add:

```java
private static final Map<String, Integer> connectorCMDCache = new HashMap<>();
```

- [ ] **Step 2: Add loadConnectorCMDs method**

After the existing `loadGuiMaterials()` method, add:

```java
private static void loadConnectorCMDs() {
    ConfigurationSection section = config.getConfigurationSection("connector_textures");
    if (section == null) return;
    for (String key : section.getKeys(false)) {
        connectorCMDCache.put(key, section.getInt(key));
    }
}
```

- [ ] **Step 3: Call loadConnectorCMDs from load()**

In the existing `load()` method, add after `loadGuiMaterials();`:

```java
loadConnectorCMDs();
```

- [ ] **Step 4: Add getConnectorCMD method**

After the existing `getGuiMaterial()` method, add:

```java
public static int getConnectorCMD(String pieceKey) {
    return connectorCMDCache.getOrDefault(pieceKey, 0);
}
```

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -pl core`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/texturepack/TexturePackManager.java
git commit -m "feat: add connector CMD loading to TexturePackManager"
```

---

### Task 3: Rewrite ResearchGraphMenu — Direction and PieceType enums + helpers

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java`

**Interfaces:**
- Consumes: `TexturePackManager.getConnectorCMD(String)`
- Produces: `Direction`, `PieceType` enums, `getDirection()`, `getPieceType()`, `getStateKey()`, `createConnectorItem()`, `isBlankSlot()`

- [ ] **Step 1: Add Direction enum**

Inside `ResearchGraphMenu` class, add:

```java
private enum Direction { N, S, E, W }
```

- [ ] **Step 2: Add PieceType enum**

Inside `ResearchGraphMenu` class, add:

```java
private enum PieceType {
    STRAIGHT_H, STRAIGHT_V,
    CORNER_NE, CORNER_SE, CORNER_SW, CORNER_NW
}
```

- [ ] **Step 3: Add getDirection method**

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

- [ ] **Step 4: Add getPieceType method**

```java
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

- [ ] **Step 5: Add getStateKey method**

Replace the existing `getEdgeMaterial()` method with:

```java
private String getStateKey(ResearchState parentState, ResearchState childState) {
    if (parentState == ResearchState.HIDDEN) return "hidden";
    if (parentState != ResearchState.COMPLETED) return "locked";
    return switch (childState) {
        case COMPLETED -> "completed";
        case AVAILABLE -> "available";
        case IN_PROGRESS -> "in_progress";
        case LOCKED, HIDDEN -> "locked";
    };
}
```

- [ ] **Step 6: Add createConnectorItem method**

```java
private ItemStack createConnectorItem(PieceType piece, String stateKey) {
    String cmdKey = piece.name().toLowerCase() + "_" + stateKey;
    int cmd = TexturePackManager.getConnectorCMD(cmdKey);
    return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
        .name(" ")
        .customModelData(cmd)
        .build();
}
```

- [ ] **Step 7: Add isBlankSlot helper**

```java
private boolean isBlankSlot(int slot) {
    ItemStack existing = inventory.getItem(slot);
    if (existing == null || existing.getType() == Material.AIR) return true;
    return existing.isSimilar(Alkatraz.getGuiItemRegistry().getItem("blank"));
}
```

- [ ] **Step 8: Verify compilation**

Run: `mvn compile -pl core`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java
git commit -m "feat: add Direction, PieceType enums and helper methods to ResearchGraphMenu"
```

---

### Task 4: Rewrite ResearchGraphMenu — drawSegment, drawEdge, drawEdgeWithWaypoints

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java`

**Interfaces:**
- Consumes: `Direction`, `PieceType`, `getDirection()`, `getPieceType()`, `getStateKey()`, `createConnectorItem()`, `isBlankSlot()`, `slotFor()`
- Produces: Rewritten `drawStraightSegment()`, `drawEdgeWithWaypoints()`, `drawCardinalEdge()`, updated `drawEdge()`

- [ ] **Step 1: Add drawStraightSegment method**

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

- [ ] **Step 2: Add placeCornerAt method**

```java
private void placeCornerAt(int x, int y, Direction in, Direction out, String stateKey) {
    PieceType piece = getPieceType(in, out);
    slotFor(x, y).ifPresent(slot -> {
        if (isBlankSlot(slot)) {
            inventory.setItem(slot, createConnectorItem(piece, stateKey));
        }
    });
}
```

- [ ] **Step 3: Rewrite drawEdgeWithWaypoints**

Replace the existing `drawEdgeWithWaypoints` method:

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

    for (int i = 0; i < fullPath.size() - 1; i++) {
        int[] from = fullPath.get(i);
        int[] to = fullPath.get(i + 1);
        drawStraightSegment(from[0], from[1], to[0], to[1], stateKey);
    }

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

- [ ] **Step 4: Add drawCardinalEdge method**

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

    drawStraightSegment(px, py, cx, py, stateKey);
    Direction hDir = (cx > px) ? Direction.E : Direction.W;
    Direction vDir = (cy > py) ? Direction.S : Direction.N;
    placeCornerAt(cx, py, hDir, vDir, stateKey);
    drawStraightSegment(cx, py, cx, cy, stateKey);
}
```

- [ ] **Step 5: Rewrite drawEdge**

Replace the existing `drawEdge` method:

```java
private void drawEdge(ResearchNode parent, ResearchNode child) {
    ResearchState parentState = ResearchService.getState(viewer, parent);
    if (parentState == ResearchState.HIDDEN) return;

    List<int[]> waypoints = child.getEdgePaths() != null
        ? child.getEdgePaths().get(parent.getId()) : null;
    if (waypoints != null && !waypoints.isEmpty()) {
        drawEdgeWithWaypoints(parent, child, waypoints);
    } else {
        drawCardinalEdge(parent, child);
    }
}
```

- [ ] **Step 6: Remove old drawSegment method**

Delete the old `drawSegment` method (the one that moves diagonally with `Integer.compare`).

- [ ] **Step 7: Remove old getEdgeMaterial method**

Delete the old `getEdgeMaterial` method (replaced by `getStateKey`).

- [ ] **Step 8: Verify compilation**

Run: `mvn compile -pl core`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java
git commit -m "feat: rewrite ResearchGraphMenu edge rendering for cardinal connectors"
```

---

### Task 5: Update research.yml — magic category edge_paths

**Files:**
- Modify: `core/src/main/resources/research.yml`

**Interfaces:**
- Consumes: `ResearchNode.getEdgePaths()` returns `Map<String, List<int[]>>`
- Produces: Updated edge_paths for all magic category edges

- [ ] **Step 1: Update existing edge_paths for magic category**

Replace the `edge_paths` section of each magic node that already has waypoints:

**disciplined_channel** — update 3 parent entries:
```yaml
    edge_paths:
      mana_conservation:
      - x: 1
        y: 3
      - x: 1
        y: -3
      ritual_geometry:
      - x: -3
        y: 3
      spellcraft_field_notes:
      - x: 3
        y: 3
```

**ward_craft** — update 1 parent entry:
```yaml
    edge_paths:
      awakened_focus:
      - x: -3
        y: 0
```

**mana_reservoir** — update 1 parent entry:
```yaml
    edge_paths:
      ritual_geometry:
      - x: -3
        y: -6
```

**mana_conduit** — update 1 parent entry:
```yaml
    edge_paths:
      disciplined_channel:
      - x: 1
        y: 6
      - x: 1
        y: 3
```

**barrier_weave** — update 2 parent entries:
```yaml
    edge_paths:
      disciplined_channel:
      - x: -5
        y: 2
      - x: 0
        y: 2
      ward_craft:
      - x: -5
        y: 1
```

**grand_ward** — update 1 parent entry:
```yaml
    edge_paths:
      barrier_weave:
      - x: -8
        y: 4
      - x: -5
        y: 4
```

**mana_sovereignty** — update 1 parent entry:
```yaml
    edge_paths:
      arcane_theory:
      - x: -1
        y: -9
      - x: -1
        y: -3
```

**arcane_nexus** — update 1 parent entry:
```yaml
    edge_paths:
      combat_casting:
      - x: 1
        y: 9
      - x: 1
        y: 6
```

- [ ] **Step 2: Add edge_paths for magic edges that currently lack them**

**ritual_geometry** — add for parent awakened_focus:
```yaml
    edge_paths:
      awakened_focus:
      - x: -3
        y: 0
```

**spellcraft_field_notes** — add for parent awakened_focus:
```yaml
    edge_paths:
      awakened_focus:
      - x: 3
        y: 0
```

**focus_discipline** — add for parent awakened_focus:
```yaml
    edge_paths:
      awakened_focus:
      - x: 3
        y: 0
```

**spell_resonance** — add for parent spellcraft_field_notes:
```yaml
    edge_paths:
      spellcraft_field_notes:
      - x: 5
        y: -2
```

**combat_casting** — add for parent focus_discipline:
```yaml
    edge_paths:
      focus_discipline:
      - x: 5
        y: 1
```

**arcane_theory** — add for parents:
```yaml
    edge_paths:
      ritual_geometry:
      - x: -5
        y: -2
      ward_craft:
      - x: -5
        y: 1
```

**archmage** — add for parents:
```yaml
    edge_paths:
      arcane_theory:
      - x: -8
        y: -3
      barrier_weave:
      - x: -8
        y: 3
```

**mana_conduit** — add for parent ward_craft:
```yaml
    edge_paths:
      disciplined_channel:
      - x: 1
        y: 6
      - x: 1
        y: 3
      ward_craft:
      - x: -3
        y: 6
```

**grand_ward** — add for parent mana_conduit:
```yaml
    edge_paths:
      barrier_weave:
      - x: -8
        y: 4
      - x: -5
        y: 4
      mana_conduit:
      - x: -8
        y: 6
```

**battle_mage** — add for parents:
```yaml
    edge_paths:
      combat_casting:
      - x: 8
        y: 3
      spell_resonance:
      - x: 8
        y: -3
```

- [ ] **Step 3: Verify magic category YAML parses**

Run: `mvn compile -pl core`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/resources/research.yml
git commit -m "feat: update magic category edge_paths for cardinal connectors"
```

---

### Task 6: Update research.yml — elemental categories edge_paths

**Files:**
- Modify: `core/src/main/resources/research.yml`

**Interfaces:**
- Consumes: Same topology as magic, applied to fire/water/earth/air/light/dark nodes
- Produces: Updated edge_paths for all 6 elemental categories

- [ ] **Step 1: Apply Pattern B update to all 6 categories**

Each elemental category has a "ward" node connecting to its root via a single waypoint. Update each:

**fire** — cinder_ward → fire_spark:
```yaml
      fire_spark:
      - x: -3
        y: 0
```

**water** — glacial_barrier → water_first_tide:
```yaml
      water_first_tide:
      - x: -3
        y: 0
```

**earth** — crystal_resonance → earth_stone_memory:
```yaml
      earth_stone_memory:
      - x: -3
        y: 0
```

**air** — skyward_barrier → air_open_breath:
```yaml
      air_open_breath:
      - x: -3
        y: 0
```

**light** — restoration_ward → light_first_lantern:
```yaml
      light_first_lantern:
      - x: -3
        y: 0
```

**dark** — abyssal_ward → dark_quiet_shadow:
```yaml
      dark_quiet_shadow:
      - x: -3
        y: 0
```

- [ ] **Step 2: Apply Pattern D update to all 6 categories**

Each elemental category has a "conduit" node connecting to its "discipline" node. Update each with waypoints: [(1,6), (1,3)]

- [ ] **Step 3: Apply Pattern E update to all 6 categories**

Each elemental category has a "grand_ward" equivalent connecting to its "barrier" equivalent. Update each with waypoints: [(-8,4), (-5,4)]

- [ ] **Step 4: Apply Pattern F update to all 6 categories**

Each elemental category has a "mastery" node connecting to its "pyroclasm" equivalent. Update each with waypoints: [(1,-9), (1,-3)]

- [ ] **Step 5: Apply Pattern G update to all 6 categories**

Each elemental category has a "barrier" node connecting to its "discipline" node. Update each with waypoints: [(-5,2), (0,2)]

- [ ] **Step 6: Apply Pattern H update to all 6 categories**

Each elemental category has a "barrier" node connecting to its "ward" node. Update each with waypoints: [(-5,1)]

- [ ] **Step 7: Add edge_paths for elemental edges that currently lack them**

For each of the 6 elemental categories, add edge_paths for edges that currently have none (following the same pattern as magic category in Task 5).

- [ ] **Step 8: Verify all categories parse correctly**

Run: `mvn compile -pl core`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add core/src/main/resources/research.yml
git commit -m "feat: update elemental category edge_paths for cardinal connectors"
```

---

### Task 7: Test and verify

**Files:**
- No file changes — verification only

- [ ] **Step 1: Full build**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: Start dev server and test**

Start the Minecraft server with the plugin loaded. Open the research graph menu and verify:
- [ ] All edges render with correct textures (straights along segments, corners at waypoints)
- [ ] Edge colors match state (green=completed, yellow=available, cyan=in_progress, red=locked, gray=hidden)
- [ ] No overlapping items at node positions
- [ ] Panning works correctly (edges redraw at new viewport positions)
- [ ] Category switching works (scrollbar navigation)
- [ ] All 7 categories render correctly

- [ ] **Step 3: Final commit (if any fixes needed)**

```bash
git add -A
git commit -m "fix: connector texture rendering adjustments"
```

---

## Spec Coverage Check

| Spec Section | Task |
|-------------|------|
| Texture system (30 textures, 6 types × 5 states) | Task 1 |
| TexturePackManager connector CMD loading | Task 2 |
| Direction + PieceType enums, helper methods | Task 3 |
| Rewritten drawSegment/drawEdge/drawEdgeWithWaypoints | Task 4 |
| Magic category edge_path updates | Task 5 |
| Elemental category edge_path updates | Task 6 |
| Verification | Task 7 |
