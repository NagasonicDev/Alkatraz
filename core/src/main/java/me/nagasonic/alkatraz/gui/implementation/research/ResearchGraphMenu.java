package me.nagasonic.alkatraz.gui.implementation.research;

import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.implementation.WandTableSelectionMenu;
import me.nagasonic.alkatraz.progression.research.ResearchService;
import me.nagasonic.alkatraz.progression.research.ResearchState;
import me.nagasonic.alkatraz.progression.research.definition.ResearchCategory;
import me.nagasonic.alkatraz.progression.research.definition.ResearchNode;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ResearchGraphMenu extends Menu {

    // Graph occupies rows 1-4 (2nd-5th), columns 1-7 — a 4-row x 7-col rectangle.
    private static final int GRAPH_ROWS    = 4;
    private static final int GRAPH_COLUMNS = 7;
    private static final int GRAPH_LEFT    = 1;
    private static final int GRAPH_TOP     = 1;

    private static final int GRAPH_CENTRE_COL = GRAPH_LEFT + GRAPH_COLUMNS / 2; // col 4
    private static final int GRAPH_CENTRE_ROW = GRAPH_TOP  + GRAPH_ROWS    / 2; // row 3

    private static final int SLOT_PAN_N  = GRAPH_CENTRE_COL;                    //  4
    private static final int SLOT_PAN_S  = 5 * 9 + GRAPH_CENTRE_COL;           // 49
    private static final int SLOT_PAN_W  = GRAPH_CENTRE_ROW * 9;                // 27
    private static final int SLOT_PAN_E  = GRAPH_CENTRE_ROW * 9 + 8;           // 35
    private static final int SLOT_PAN_NW = 0;
    private static final int SLOT_PAN_NE = 8;
    private static final int SLOT_INFO       = 45;
    private static final int SLOT_BACK_TABLE = 48;
    private static final int SLOT_CATEGORIES = 53;

    private static final int MAX_LEFT  = 10;
    private static final int MAX_RIGHT = 10;
    private static final int MAX_UP    = 10;
    private static final int MAX_DOWN  = 10;

    private String category;
    /** Graph-space coordinate that maps to the centre slot of the viewport. */
    private int viewCenterX;
    private int viewCenterY;

    public ResearchGraphMenu(Player viewer) {
        this(viewer, firstCategory(), 0, 0);
    }

    public ResearchGraphMenu(Player viewer, String category, int viewCenterX, int viewCenterY) {
        super(viewer, ColorFormat.format("&5Research Library"), 54);
        this.category    = category;
        this.viewCenterX = clampX(viewCenterX);
        this.viewCenterY = clampY(viewCenterY);
    }

    @Override
    protected void build() {
        // Fill every slot with a blank, then overwrite with graph content and controls.
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, Utils.getBlank());
        }
        List<ResearchNode> nodes = ResearchService.getNodes(category);
        drawEdges(nodes);
        drawNodes(nodes);
        drawControls();
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        int s = event.getSlot();

        // Pan using slot position — same pattern as SkillTreeMenu.
        // Left/right edges move X; top edge moves Y; corners move both.
        boolean panLeft  = (s == SLOT_PAN_W  || s == SLOT_PAN_NW);
        boolean panRight = (s == SLOT_PAN_E  || s == SLOT_PAN_NE);
        boolean panUp    = (s == SLOT_PAN_N  || s == SLOT_PAN_NW || s == SLOT_PAN_NE);
        boolean panDown  = (s == SLOT_PAN_S);

        if (panLeft || panRight || panUp || panDown) {
            if (panLeft)  viewCenterX = clampX(viewCenterX - 1);
            if (panRight) viewCenterX = clampX(viewCenterX + 1);
            if (panUp)    viewCenterY = clampY(viewCenterY - 1);
            if (panDown)  viewCenterY = clampY(viewCenterY + 1);
            refresh();
            return true;
        }

        String action = getStringData(clicked, "action");
        if ("back_table".equals(action)) {
            new WandTableSelectionMenu(viewer).open();
            return true;
        }
        if ("category".equals(action)) {
            new ResearchCategoriesMenu(viewer).open();
            return true;
        }
        if ("research".equals(action)) {
            String id = getStringData(clicked, "research_id");
            ResearchService.getNode(id).ifPresent(node -> new ResearchEntryMenu(viewer, node, category, viewCenterX, viewCenterY).open());
            return true;
        }

        return true;
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
                        || existing.isSimilar(Utils.getBlank());
                if (isBlank) {
                    inventory.setItem(slot, createEdgeItem(parent, child));
                }
            });
            if (x != cx) x += dx;
            if (y != cy) y += dy;
        }
    }

    private void drawControls() {
        inventory.setItem(SLOT_PAN_N,  button(Material.ARROW,     "&fPan Up",        "pan_n"));
        inventory.setItem(SLOT_PAN_S,  button(Material.ARROW,     "&fPan Down",      "pan_s"));
        inventory.setItem(SLOT_PAN_W,  button(Material.ARROW,     "&fPan Left",      "pan_w"));
        inventory.setItem(SLOT_PAN_E,  button(Material.ARROW,     "&fPan Right",     "pan_e"));
        inventory.setItem(SLOT_PAN_NW, button(Material.ARROW,     "&fPan Up-Left",   "pan_nw"));
        inventory.setItem(SLOT_PAN_NE, button(Material.ARROW,     "&fPan Up-Right",  "pan_ne"));
        inventory.setItem(SLOT_INFO,          infoItem());
        inventory.setItem(SLOT_BACK_TABLE,    button(Material.ARROW, "&fBack to Arcane Table", "back_table"));
        inventory.setItem(SLOT_CATEGORIES,    button(Material.BOOKSHELF, "&dCategories", "category"));
    }

    private ItemStack createNodeItem(ResearchNode node, ResearchState state) {
        Material material = switch (state) {
            case HIDDEN -> Material.GRAY_DYE;
            case LOCKED -> Material.RED_STAINED_GLASS;
            case AVAILABLE -> node.getIcon();
            case IN_PROGRESS -> Material.WRITABLE_BOOK;
            case COMPLETED -> Material.ENCHANTED_BOOK;
        };

        String name = state == ResearchState.HIDDEN ? "&8Unknown Research" : stateColor(state) + node.getDisplayName();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(name));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format(stateColor(state) + formatState(state)));
        if (state != ResearchState.HIDDEN) {
            for (String line : node.getDescription()) {
                lore.add(ColorFormat.format("&7" + line));
            }
            if (!node.getObjectives().isEmpty()) {
                lore.add(ColorFormat.format("&7"));
                lore.add(ColorFormat.format("&eResearch Tasks: &f" + node.getObjectives().size()));
            }
            lore.add(ColorFormat.format("&eClick to inspect"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        if (state != ResearchState.HIDDEN) {
            setMenuData(item, "action", "research");
            setMenuData(item, "research_id", node.getId());
        }
        return item;
    }

    private ItemStack createEdgeItem(ResearchNode parent, ResearchNode child) {
        ItemStack item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&5Arcane Link"));
        List<String> lore = new ArrayList<>();
        ResearchState parentState = ResearchService.getState(viewer, parent);
        ResearchState childState = ResearchService.getState(viewer, child);
        lore.add(ColorFormat.format("&7" + visibleName(parent, parentState)));
        lore.add(ColorFormat.format("&8-> &7" + visibleName(child, childState)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack infoItem() {
        ItemStack item = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&dResearch Graph"));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7Category: &f" + category));
        lore.add(ColorFormat.format("&7View: &f" + viewCenterX + ", " + viewCenterY));
        lore.add(ColorFormat.format("&8Nodes can have multiple parents."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(name));
        item.setItemMeta(meta);
        setMenuData(item, "action", action);
        return item;
    }

    private Optional<Integer> slotFor(int x, int y) {
        // Half-extents of the viewport (integer division intentional for even/odd sizes).
        int halfCols = GRAPH_COLUMNS / 2;
        int halfRows = GRAPH_ROWS    / 2;

        // Position of (x, y) relative to the camera centre.
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

    private String visibleName(ResearchNode node, ResearchState state) {
        return state == ResearchState.HIDDEN ? "Unknown Research" : node.getDisplayName();
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

    private static String firstCategory() {
        if (ResearchService.getCategories().stream().anyMatch(category -> "magic".equals(category.getId()))) {
            return "magic";
        }
        return ResearchService.getCategories().stream()
                .min(Comparator.comparing(ResearchCategory::getDisplayName))
                .map(ResearchCategory::getId)
                .orElse("general");
    }
}