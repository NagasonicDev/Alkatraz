package me.nagasonic.alkatraz.gui.implementation.research;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.implementation.WandTableSelectionMenu;
import me.nagasonic.alkatraz.lang.LangManager;
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

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    private static final int GRAPH_ROWS = 4;
    private static final int GRAPH_COLUMNS = 7;
    private static final int GRAPH_LEFT = 1;
    private static final int GRAPH_TOP = 1;

    private static final int GRAPH_CENTRE_COL = GRAPH_LEFT + GRAPH_COLUMNS / 2;
    private static final int GRAPH_CENTRE_ROW = GRAPH_TOP + GRAPH_ROWS / 2;

    private static final int SLOT_PAN_BACK = 45;
    private static final int SLOT_PAN_NW = 46;
    private static final int SLOT_PAN_S = 48;
    private static final int SLOT_PAN_N = 49;
    private static final int SLOT_PAN_NE = 50;
    private static final int SLOT_RESEARCH_POINTS = 52;

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
        super(viewer, lang().get("menu.research_library"), 54);
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
        for (int slot : SLOTS_PAN_LEFT) {
            inventory.setItem(slot, panButton(Material.ARROW, lang().get("research.pan_left")));
        }
        for (int slot : SLOTS_PAN_RIGHT) {
            inventory.setItem(slot, panButton(Material.ARROW, lang().get("research.pan_right")));
        }
        inventory.setItem(SLOT_PAN_NW, panButton(Material.ARROW, lang().get("research.pan_up_left")));
        inventory.setItem(SLOT_PAN_S, panButton(Material.ARROW, lang().get("research.pan_down")));
        inventory.setItem(SLOT_PAN_N, panButton(Material.ARROW, lang().get("research.pan_up")));
        inventory.setItem(SLOT_PAN_NE, panButton(Material.ARROW, lang().get("research.pan_up_right")));
        inventory.setItem(SLOT_PAN_BACK, button(Material.ARROW, lang().get("research.back_to_arcane"), "back_table"));
        int points = ProfileManager.getProfile(viewer, MagicProfile.class).getResearchPoints();
        inventory.setItem(SLOT_RESEARCH_POINTS, ItemBuilder.of(Material.PAPER)
                .rawName(ColorFormat.format(lang().get("research.research_points", "points", String.valueOf(points))))
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

        String name = state == ResearchState.HIDDEN ? lang().get("research.unknown") : stateColor(state) + node.getDisplayName();
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format(stateColor(state) + formatState(state)));
        if (state == ResearchState.COMPLETED) {
            lore.add(ColorFormat.format(lang().get("research.link_completed")));
        }
        if (state != ResearchState.HIDDEN) {
            if (!node.getObjectives().isEmpty()) {
                lore.add(ColorFormat.format("&eResearch Tasks: &f" + node.getObjectives().size()));
            }
            lore.add(ColorFormat.format(lang().get("research.click_inspect")));
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
