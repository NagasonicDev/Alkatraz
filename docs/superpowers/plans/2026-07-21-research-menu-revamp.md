# Research Menu Revamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite ResearchGraphMenu and ResearchEntryMenu for a more compact, tab-driven UI, and delete ResearchCategoriesMenu.

**Architecture:** The graph menu gains category tabs on row 0 (replacing the separate categories menu), a smaller 4×7 viewport (down from 5×7), and simplified pan controls. The entry menu compresses all info into 3 main items (summary, tasks, rewards+action) with compact linked research below.

**Tech Stack:** Java 17, Spigot API 1.19+, NBTAPI (nbtapi library), existing Menu/ItemBuilder framework.

## Global Constraints

- Inventory size: 54 slots (6 rows × 9 columns)
- Use existing `Menu` base class, `ItemBuilder` builder, `setMenuData`/`getStringData` for NBT storage
- No new dependencies or libraries
- ResearchCategory has 7 entries: magic, fire, water, earth, air, light, dark

---

## Task 1: Rewrite ResearchGraphMenu

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java`

**Interfaces:**
- Consumes: `ResearchService.getCategories()`, `ResearchService.getNodes(category)`, `ResearchService.getState()`, `ResearchService.getNode()`
- Produces: Opens `ResearchEntryMenu` when node clicked

**New Layout (54 slots):**
```
Row 0 (0-8):   Tab(0) Tab(1) Tab(2) Tab(3) Tab(4) Tab(5) Tab(6) Progress  CategoryName
Row 1 (9-17):  PanW  V(0,0) V(1,0) V(2,0) V(3,0) V(4,0) V(5,0) V(6,0)  PanE
Row 2 (18-26): PanW  V(0,1) V(1,1) V(2,1) V(3,1) V(4,1) V(5,1) V(6,1)  PanE
Row 3 (27-35): PanW  V(0,2) V(1,2) V(2,2) V(3,2) V(4,2) V(5,2) V(6,2)  PanE
Row 4 (36-44): PanW  V(0,3) V(1,3) V(2,3) V(3,3) V(4,3) V(5,3) V(6,3)  PanE
Row 5 (45-53): Back  (reserved) PanNW PanS PanN PanNE (reserved) Points  (reserved)
```

- Viewport: columns 1-7, rows 1-4 (4 rows × 7 cols = 28 slots)
- Pan W/E: slots 9, 18, 27, 36 (left edge) and 17, 26, 35, 44 (right edge)
- Pan NW: slot 46, Pan S: slot 48, Pan N: slot 49, Pan NE: slot 50
- Back: slot 45, Research Points: slot 52
- Tabs: slots 0-6 (one per category, sorted by display name)

- [ ] **Step 1: Write the complete rewritten ResearchGraphMenu.java**

```java
package me.nagasonic.alkatraz.gui.implementation.research;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.implementation.WandTableSelectionMenu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.research.ResearchService;
import me.nagasonic.alkatraz.progression.research.ResearchState;
import me.nagasonic.alkatraz.progression.research.definition.ResearchCategory;
import me.nagasonic.alkatraz.progression.research.definition.ResearchNode;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ResearchGraphMenu extends Menu {

    private static final int GRAPH_ROWS = 4;
    private static final int GRAPH_COLUMNS = 7;
    private static final int GRAPH_LEFT = 1;
    private static final int GRAPH_TOP = 1;

    private static final int GRAPH_CENTRE_COL = GRAPH_LEFT + GRAPH_COLUMNS / 2; // col 4
    private static final int GRAPH_CENTRE_ROW = GRAPH_TOP + GRAPH_ROWS / 2;   // row 2→3 (0-indexed: row 2)

    // Pan control slots (row 5 = row index 5)
    private static final int SLOT_PAN_BACK = 45;
    private static final int SLOT_PAN_NW = 46;
    private static final int SLOT_PAN_S = 48;
    private static final int SLOT_PAN_N = 49;
    private static final int SLOT_PAN_NE = 50;
    private static final int SLOT_RESEARCH_POINTS = 52;

    // Left/right pan slots = column 0 and 8 of rows 1-4
    private static final int[] SLOTS_PAN_LEFT = {9, 18, 27, 36};
    private static final int[] SLOTS_PAN_RIGHT = {17, 26, 35, 44};

    private static final int MAX_LEFT = 10;
    private static final int MAX_RIGHT = 10;
    private static final int MAX_UP = 10;
    private static final int MAX_DOWN = 10;

    private static final int TAB_SLOT_COUNT = 7;

    private String category;
    private int viewCenterX;
    private int viewCenterY;

    public ResearchGraphMenu(Player viewer) {
        this(viewer, firstCategory(), 0, 0);
    }

    public ResearchGraphMenu(Player viewer, String category, int viewCenterX, int viewCenterY) {
        super(viewer, ColorFormat.format("&5Research Library"), 54);
        this.category = category;
        this.viewCenterX = clampX(viewCenterX);
        this.viewCenterY = clampY(viewCenterY);
    }

    @Override
    protected void build() {
        fillAll();
        drawTabs();
        drawProgressSummary();
        drawCategoryName();
        List<ResearchNode> nodes = ResearchService.getNodes(category);
        drawEdges(nodes);
        drawNodes(nodes);
        drawControls();
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        int s = event.getSlot();

        // Tab clicks (slots 0-6)
        if (s >= 0 && s < TAB_SLOT_COUNT) {
            List<ResearchCategory> cats = sortedCategories();
            if (s < cats.size()) {
                String newCategory = cats.get(s).getId();
                if (!newCategory.equals(category)) {
                    new ResearchGraphMenu(viewer, newCategory, 0, 0).open();
                }
            }
            return true;
        }

        // Pan controls
        boolean panLeft = false;
        boolean panRight = false;
        for (int slot : SLOTS_PAN_LEFT) {
            if (s == slot) { panLeft = true; break; }
        }
        for (int slot : SLOTS_PAN_RIGHT) {
            if (s == slot) { panRight = true; break; }
        }
        boolean panUp = (s == SLOT_PAN_N || s == SLOT_PAN_NW || s == SLOT_PAN_NE);
        boolean panDown = (s == SLOT_PAN_S);

        if (panLeft || panRight || panUp || panDown) {
            if (panLeft) viewCenterX = clampX(viewCenterX - 1);
            if (panRight) viewCenterX = clampX(viewCenterX + 1);
            if (panUp) viewCenterY = clampY(viewCenterY - 1);
            if (panDown) viewCenterY = clampY(viewCenterY + 1);
            refresh();
            return true;
        }

        String action = getStringData(clicked, "action");
        if ("back_table".equals(action)) {
            new WandTableSelectionMenu(viewer).open();
            return true;
        }
        if ("research".equals(action)) {
            String id = getStringData(clicked, "research_id");
            ResearchService.getNode(id).ifPresent(node ->
                    new ResearchEntryMenu(viewer, node, category, viewCenterX, viewCenterY).open());
            return true;
        }

        return true;
    }

    private void drawTabs() {
        List<ResearchCategory> cats = sortedCategories();
        for (int i = 0; i < TAB_SLOT_COUNT; i++) {
            if (i >= cats.size()) break;
            ResearchCategory cat = cats.get(i);
            boolean active = cat.getId().equals(category);
            Material mat = active ? Material.ENCHANTED_BOOK : cat.getIcon();
            String name = ColorFormat.format(active ? "&d" + cat.getDisplayName() : "&7" + cat.getDisplayName());
            ItemStack item = ItemBuilder.of(mat)
                    .rawName(name)
                    .glint(active)
                    .build();
            inventory.setItem(i, item);
        }
    }

    private void drawProgressSummary() {
        List<ResearchNode> nodes = ResearchService.getNodes(category);
        int total = nodes.size();
        int completed = 0;
        for (ResearchNode node : nodes) {
            if (ResearchService.getState(viewer, node) == ResearchState.COMPLETED) {
                completed++;
            }
        }
        String progressText = total == 0 ? "&7No research" : "&a" + completed + "&7/&a" + total;
        ItemStack item = ItemBuilder.of(Material.PAPER)
                .name("&bProgress")
                .rawLore(List.of(ColorFormat.format(progressText)))
                .build();
        inventory.setItem(7, item);
    }

    private void drawCategoryName() {
        String displayName = ResearchService.getCategories().stream()
                .filter(c -> c.getId().equals(category))
                .map(ResearchCategory::getDisplayName)
                .findFirst().orElse(category);
        ItemStack item = ItemBuilder.of(Material.BOOK)
                .rawName(ColorFormat.format("&d" + displayName))
                .build();
        inventory.setItem(8, item);
    }

    private void drawNodes(List<ResearchNode> nodes) {
        for (ResearchNode node : nodes) {
            Optional<Integer> slot = slotFor(node.getX(), node.getY());
            if (slot.isEmpty()) continue;
            ResearchState state = ResearchService.getState(viewer, node);
            inventory.setItem(slot.get(), createNodeItem(node, state));
        }
    }

    private void drawEdges(List<ResearchNode> nodes) {
        for (ResearchNode child : nodes) {
            for (String parentId : child.getParents()) {
                ResearchService.getNode(parentId).ifPresent(parent -> drawEdge(parent, child));
            }
        }
    }

    private void drawEdge(ResearchNode parent, ResearchNode child) {
        if (!parent.getCategory().equals(category) || !child.getCategory().equals(category)) return;

        int px = parent.getX();
        int py = parent.getY();
        int cx = child.getX();
        int cy = child.getY();
        int dx = Integer.compare(cx, px);
        int dy = Integer.compare(cy, py);
        int x = px + dx;
        int y = py + dy;

        while (x != cx || y != cy) {
            slotFor(x, y).ifPresent(slot -> {
                ItemStack existing = inventory.getItem(slot);
                boolean isBlank = existing == null || existing.getType() == Material.AIR
                        || existing.isSimilar(Alkatraz.getGuiItemRegistry().getItem("blank"));
                if (isBlank) {
                    inventory.setItem(slot, createEdgeItem(parent, child));
                }
            });
            if (x != cx) x += dx;
            if (y != cy) y += dy;
        }
    }

    private void drawControls() {
        // Left pan
        for (int slot : SLOTS_PAN_LEFT) {
            inventory.setItem(slot, panButton(Material.ARROW, "&fPan Left"));
        }
        // Right pan
        for (int slot : SLOTS_PAN_RIGHT) {
            inventory.setItem(slot, panButton(Material.ARROW, "&fPan Right"));
        }
        // Bottom pan controls
        inventory.setItem(SLOT_PAN_NW, panButton(Material.ARROW, "&fPan Up-Left"));
        inventory.setItem(SLOT_PAN_S, panButton(Material.ARROW, "&fPan Down"));
        inventory.setItem(SLOT_PAN_N, panButton(Material.ARROW, "&fPan Up"));
        inventory.setItem(SLOT_PAN_NE, panButton(Material.ARROW, "&fPan Up-Right"));
        // Back
        inventory.setItem(SLOT_PAN_BACK, button(Material.ARROW, "&fBack to Arcane Table", "back_table"));
        // Research points
        int points = ProfileManager.getProfile(viewer, MagicProfile.class).getResearchPoints();
        inventory.setItem(SLOT_RESEARCH_POINTS, ItemBuilder.of(Material.PAPER)
                .name("&bResearch Points: &f" + points)
                .build());
    }

    private ItemStack createNodeItem(ResearchNode node, ResearchState state) {
        Material material = switch (state) {
            case HIDDEN -> Material.GRAY_DYE;
            case LOCKED -> Material.RED_DYE;
            case AVAILABLE -> node.getIcon();
            case IN_PROGRESS -> Material.WRITABLE_BOOK;
            case COMPLETED -> Material.ENCHANTED_BOOK;
        };

        String name = state == ResearchState.HIDDEN ? "&8Unknown Research" : stateColor(state) + node.getDisplayName();
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format(stateColor(state) + formatState(state)));
        if (state == ResearchState.COMPLETED) {
            lore.add(ColorFormat.format("&aCompleted"));
        }
        if (state != ResearchState.HIDDEN) {
            if (!node.getObjectives().isEmpty()) {
                lore.add(ColorFormat.format("&eResearch Tasks: &f" + node.getObjectives().size()));
            }
            lore.add(ColorFormat.format("&eClick to inspect"));
        }
        ItemStack item = ItemBuilder.of(material)
                .rawName(ColorFormat.format(name))
                .rawLore(lore)
                .build();
        if (state != ResearchState.HIDDEN) {
            setMenuData(item, "action", "research");
            setMenuData(item, "research_id", node.getId());
        }
        return item;
    }

    private ItemStack createEdgeItem(ResearchNode parent, ResearchNode child) {
        ResearchState parentState = ResearchService.getState(viewer, parent);
        ResearchState childState = ResearchService.getState(viewer, child);

        boolean parentDone = parentState == ResearchState.COMPLETED;
        boolean childDone = childState == ResearchState.COMPLETED;

        Material material;
        if (parentDone && childDone) {
            material = Material.LIME_STAINED_GLASS_PANE;
        } else {
            material = Material.GRAY_STAINED_GLASS_PANE;
        }

        return ItemBuilder.of(material).build();
    }

    private ItemStack panButton(Material material, String name) {
        return ItemBuilder.of(material).rawName(ColorFormat.format(name)).build();
    }

    private ItemStack button(Material material, String name, String action) {
        ItemStack item = ItemBuilder.of(material).rawName(ColorFormat.format(name)).build();
        setMenuData(item, "action", action);
        return item;
    }

    private Optional<Integer> slotFor(int x, int y) {
        int halfCols = GRAPH_COLUMNS / 2;
        int halfRows = GRAPH_ROWS / 2;

        int viewX = x - viewCenterX;
        int viewY = y - viewCenterY;

        if (viewX < -halfCols || viewX > halfCols || viewY < -halfRows || viewY > halfRows) {
            return Optional.empty();
        }

        int slotCol = GRAPH_CENTRE_COL + viewX;
        int slotRow = GRAPH_CENTRE_ROW + viewY;
        return Optional.of(slotRow * 9 + slotCol);
    }

    private static int clampX(int x) {
        return Math.max(-MAX_LEFT, Math.min(MAX_RIGHT, x));
    }

    private static int clampY(int y) {
        return Math.max(-MAX_UP, Math.min(MAX_DOWN, y));
    }

    private String formatState(ResearchState state) {
        String words = state.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private String stateColor(ResearchState state) {
        return switch (state) {
            case HIDDEN -> "&8";
            case LOCKED -> "&c";
            case AVAILABLE -> "&e";
            case IN_PROGRESS -> "&b";
            case COMPLETED -> "&a";
        };
    }

    private static List<ResearchCategory> sortedCategories() {
        return ResearchService.getCategories().stream()
                .sorted(Comparator.comparing(ResearchCategory::getDisplayName))
                .toList();
    }

    private static String firstCategory() {
        if (ResearchService.getCategories().stream().anyMatch(c -> "magic".equals(c.getId()))) {
            return "magic";
        }
        return sortedCategories().stream()
                .findFirst()
                .map(ResearchCategory::getId)
                .orElse("general");
    }
}
```

- [ ] **Step 2: Compile and verify no errors**

Run: `cd D:\Alkatraz\Alkatraz && mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java
git commit -m "refactor: rewrite ResearchGraphMenu with category tabs and compact viewport"
```

---

## Task 2: Rewrite ResearchEntryMenu

**Files:**
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchEntryMenu.java`

**New Layout (54 slots):**
```
Row 0 (0-8):   empty  empty  empty  Summary  empty  empty  empty  empty  empty
Row 1 (9-17):  empty  empty  empty  empty   empty  empty  empty  empty  empty
Row 2 (18-26): empty  empty  empty  empty   empty  empty  empty  empty  empty
Row 3 (27-35): empty  empty  empty  empty   empty  empty  empty  empty  empty
Row 4 (36-44): LR(0)  LR(1)  LR(2)  LR(3)   LR(4)  LR(5)  LR(6)  LR(7)  LR(8)
Row 5 (45-53): empty  empty  empty  empty   Back   empty  empty  Points empty
```

- Slot 4: Summary item (name, state, cost, description, requirements — all in lore)
- Slot 13: Tasks item (all objectives with progress)
- Slot 22: Rewards + Action item (rewards, unlocks, start/complete button)
- Slots 36-44: Up to 9 compact linked research items (icon + colored name, no lore)
- Slot 49: Back button

- [ ] **Step 4: Write the complete rewritten ResearchEntryMenu.java**

```java
package me.nagasonic.alkatraz.gui.implementation.research;

import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.research.ResearchService;
import me.nagasonic.alkatraz.progression.research.ResearchState;
import me.nagasonic.alkatraz.progression.research.definition.ResearchNode;
import me.nagasonic.alkatraz.progression.research.definition.ResearchObjective;
import me.nagasonic.alkatraz.progression.research.definition.ResearchReward;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ResearchEntryMenu extends Menu {

    private static final int SLOT_SUMMARY = 4;
    private static final int SLOT_TASKS = 13;
    private static final int SLOT_REWARDS_ACTION = 22;
    private static final int SLOT_LINKED_START = 36;
    private static final int SLOT_BACK = 49;
    private static final int MAX_LINKED = 9;

    private final ResearchNode node;
    private final String category;
    private final int offsetX;
    private final int offsetY;

    public ResearchEntryMenu(Player viewer, ResearchNode node, String category, int offsetX, int offsetY) {
        super(viewer, ColorFormat.format("&5Research: " + node.getDisplayName()), 54);
        this.node = node;
        this.category = category;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    @Override
    protected void build() {
        inventory.clear();
        ResearchState state = ResearchService.getState(viewer, node);
        inventory.setItem(SLOT_SUMMARY, summaryItem(state));
        inventory.setItem(SLOT_TASKS, tasksItem());
        inventory.setItem(SLOT_REWARDS_ACTION, rewardsActionItem(state));
        addLinkedResearch();
        inventory.setItem(SLOT_BACK, backItem());
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        String action = getStringData(clicked, "action");

        if ("back".equals(action)) {
            new ResearchGraphMenu(viewer, category, offsetX, offsetY).open();
            return true;
        }
        if ("start".equals(action)) {
            boolean started = ResearchService.start(viewer, node);
            if (!started) {
                int cost = node.getResearchPointsCost();
                if (cost > 0) {
                    viewer.sendMessage(ColorFormat.format("&cYou need " + cost + " Research Points to start this research."));
                }
            }
            refresh();
            return true;
        }
        if ("complete".equals(action)) {
            ResearchService.complete(viewer, node);
            refresh();
            return true;
        }
        if ("linked_research".equals(action)) {
            String id = getStringData(clicked, "research_id");
            ResearchService.getNode(id).ifPresent(next ->
                    new ResearchEntryMenu(viewer, next, category, offsetX, offsetY).open());
            return true;
        }
        return true;
    }

    private ItemStack summaryItem(ResearchState state) {
        Material material = state == ResearchState.COMPLETED ? Material.ENCHANTED_BOOK : node.getIcon();
        String name = state == ResearchState.HIDDEN ? "&8Unknown Research" : "&d" + node.getDisplayName();
        List<String> lore = new ArrayList<>();

        // State
        lore.add(ColorFormat.format("&7State: " + stateColor(state) + formatState(state)));

        // Cost
        int cost = node.getResearchPointsCost();
        if (cost > 0) {
            lore.add(ColorFormat.format("&7Cost: &b" + cost + " Research Points"));
        }

        // Description
        if (!node.getDescription().isEmpty()) {
            lore.add(ColorFormat.format(""));
            for (String line : node.getDescription()) {
                lore.add(ColorFormat.format("&7" + line));
            }
        }

        // Requirements
        if (!node.getParents().isEmpty()) {
            lore.add(ColorFormat.format(""));
            lore.add(ColorFormat.format("&eRequirements:"));
            for (String parentId : node.getParents()) {
                ResearchService.getNode(parentId).ifPresent(parent -> {
                    ResearchState parentState = ResearchService.getState(viewer, parent);
                    String mark = parentState == ResearchState.COMPLETED ? "&a[Done] " : "&c[Missing] ";
                    lore.add(ColorFormat.format("  " + mark + stateColor(parentState) + parent.getDisplayName()));
                });
            }
        }

        return ItemBuilder.of(material)
                .rawName(ColorFormat.format(name))
                .rawLore(lore)
                .build();
    }

    private ItemStack tasksItem() {
        boolean allComplete = ResearchService.objectivesComplete(viewer, node);
        Material material = allComplete ? Material.FILLED_MAP : Material.MAP;
        String name = "&bResearch Tasks";
        List<String> lore = new ArrayList<>();

        if (node.getObjectives().isEmpty()) {
            lore.add(ColorFormat.format("&aNo tasks required."));
        } else {
            for (ResearchObjective objective : node.getObjectives()) {
                int progress = ResearchService.getObjectiveProgress(viewer, node, objective);
                String mark = progress >= objective.getAmount() ? "&a[Done] " : "&e- ";
                lore.add(ColorFormat.format(mark + objective.getDisplayName()));
                lore.add(ColorFormat.format("&8  " + progress + "/" + objective.getAmount()));
            }
        }

        return ItemBuilder.of(material)
                .rawName(ColorFormat.format(name))
                .rawLore(lore)
                .build();
    }

    private ItemStack rewardsActionItem(ResearchState state) {
        List<String> lore = new ArrayList<>();

        // Rewards section
        if (!node.getRewards().isEmpty()) {
            lore.add(ColorFormat.format("&eRewards:"));
            for (ResearchReward reward : node.getRewards()) {
                lore.add(ColorFormat.format("&7" + rewardText(reward)));
            }
        }

        // Unlocks section
        if (!node.getUnlocks().isEmpty()) {
            if (!lore.isEmpty()) lore.add(ColorFormat.format(""));
            lore.add(ColorFormat.format("&dUnlocks:"));
            for (String unlock : node.getUnlocks()) {
                lore.add(ColorFormat.format("&7" + unlock));
            }
        }

        // Action section
        if (!lore.isEmpty()) lore.add(ColorFormat.format(""));
        String actionName;
        Material actionMat;
        String actionTag = null;

        switch (state) {
            case AVAILABLE -> {
                actionMat = Material.WRITABLE_BOOK;
                actionName = "&eStart Research";
                int cost = node.getResearchPointsCost();
                if (cost > 0) {
                    int balance = ProfileManager.getProfile(viewer, MagicProfile.class).getResearchPoints();
                    boolean canAfford = balance >= cost;
                    lore.add(ColorFormat.format("&7Cost: &b" + cost + " RP &7| Balance: &f" + balance));
                    if (!canAfford) {
                        lore.add(ColorFormat.format("&cNot enough Research Points!"));
                    }
                }
                lore.add(ColorFormat.format("&7Click to begin studying."));
                actionTag = "start";
            }
            case IN_PROGRESS -> {
                actionMat = Material.EXPERIENCE_BOTTLE;
                actionName = "&bComplete Research";
                if (ResearchService.objectivesComplete(viewer, node)) {
                    lore.add(ColorFormat.format("&7Click to record findings and claim rewards."));
                } else {
                    lore.add(ColorFormat.format("&7Complete all research tasks first."));
                }
                actionTag = "complete";
            }
            case COMPLETED -> {
                actionMat = Material.LIME_DYE;
                actionName = "&aCompleted";
                lore.add(ColorFormat.format("&7This research is finished."));
            }
            case LOCKED -> {
                actionMat = Material.BARRIER;
                actionName = "&cLocked";
                String firstName = node.getParents().stream()
                        .map(id -> ResearchService.getNode(id).map(ResearchNode::getDisplayName).orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .findFirst().orElse(null);
                if (firstName != null) {
                    lore.add(ColorFormat.format("&7Complete &c" + firstName + " &7first."));
                } else {
                    lore.add(ColorFormat.format("&7Requirements not yet met."));
                }
            }
            default -> {
                actionMat = Material.BARRIER;
                actionName = "&8Hidden";
                lore.add(ColorFormat.format("&7Complete more research to reveal this."));
            }
        }

        ItemStack item = ItemBuilder.of(actionMat)
                .rawName(ColorFormat.format(actionName))
                .rawLore(lore)
                .build();

        if (actionTag != null) {
            setMenuData(item, "action", actionTag);
        }
        return item;
    }

    private void addLinkedResearch() {
        List<String> parents = node.getParents();
        List<ResearchNode> children = ResearchService.getChildren(node.getId());

        List<ResearchNode> allLinked = new ArrayList<>();
        for (String parentId : parents) {
            ResearchService.getNode(parentId).ifPresent(allLinked::add);
        }
        for (ResearchNode child : children) {
            allLinked.add(child);
        }

        for (int i = 0; i < Math.min(allLinked.size(), MAX_LINKED); i++) {
            ResearchNode linked = allLinked.get(i);
            ResearchState state = ResearchService.getState(viewer, linked);
            boolean isParent = parents.contains(linked.getId());
            String color = isParent ? "&7" : "&8";
            String name = color + linked.getDisplayName();

            ItemStack item = ItemBuilder.of(state == ResearchState.COMPLETED ? Material.ENCHANTED_BOOK : linked.getIcon())
                    .rawName(ColorFormat.format(name))
                    .build();
            setMenuData(item, "action", "linked_research");
            setMenuData(item, "research_id", linked.getId());
            inventory.setItem(SLOT_LINKED_START + i, item);
        }
    }

    private ItemStack backItem() {
        ItemStack item = ItemBuilder.of(Material.ARROW)
                .name("&fBack to Graph")
                .build();
        setMenuData(item, "action", "back");
        return item;
    }

    private String formatState(ResearchState state) {
        String words = state.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private String rewardText(ResearchReward reward) {
        if (reward.getDisplay() != null && !reward.getDisplay().isBlank()) {
            return reward.getDisplay();
        }
        String target = reward.getTarget().replace('_', ' ');
        String value = reward.getAmount() % 1 == 0 ? String.valueOf((int) reward.getAmount()) : String.valueOf(reward.getAmount());
        return "+" + value + " " + Character.toUpperCase(target.charAt(0)) + target.substring(1);
    }

    private String stateColor(ResearchState state) {
        return switch (state) {
            case HIDDEN -> "&8";
            case LOCKED -> "&c";
            case AVAILABLE -> "&e";
            case IN_PROGRESS -> "&b";
            case COMPLETED -> "&a";
        };
    }
}
```

- [ ] **Step 5: Compile and verify no errors**

Run: `cd D:\Alkatraz\Alkatraz && mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchEntryMenu.java
git commit -m "refactor: rewrite ResearchEntryMenu with compact 3-item layout"
```

---

## Task 3: Delete ResearchCategoriesMenu and Remove References

**Files:**
- Delete: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchCategoriesMenu.java`
- Modify: `core/src/main/java/me/nagasonic/alkatraz/gui/implementation/research/ResearchGraphMenu.java` — remove any remaining imports/references to ResearchCategoriesMenu

**Interfaces:**
- Consumes: Nothing (deletion)
- Produces: Removes the category menu class; graph menu tabs replace its functionality

- [ ] **Step 7: Delete ResearchCategoriesMenu.java**

Run: `del "D:\Alkatraz\Alkatraz\core\src\main\java\me\nagasonic\alkatraz\gui\implementation\research\ResearchCategoriesMenu.java"`

- [ ] **Step 8: Grep for remaining references to ResearchCategoriesMenu**

Run: `rg "ResearchCategoriesMenu" "D:\Alkatraz\Alkatraz\core\src\main\java"`
Expected: No results (all references were removed in Task 1 rewrite)

- [ ] **Step 9: Compile and verify no errors**

Run: `cd D:\Alkatraz\Alkatraz && mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor: delete ResearchCategoriesMenu, replaced by graph tabs"
```

---

## Task 4: Final Verification

- [ ] **Step 11: Full compile check**

Run: `cd D:\Alkatraz\Alkatraz && mvn compile -q`
Expected: BUILD SUCCESS across all modules

- [ ] **Step 12: Final commit if needed**

Only if additional fixes were required during compilation.
