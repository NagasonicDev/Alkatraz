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
import me.nagasonic.alkatraz.texturepack.TexturePackManager;
import me.nagasonic.alkatraz.progression.research.definition.ResearchNode;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ResearchGraphMenu extends Menu {

    private enum Direction { N, S, E, W }

    private enum PieceType {
        STRAIGHT_H, STRAIGHT_V,
        CORNER_NE, CORNER_SE, CORNER_SW, CORNER_NW
    }

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    private static final int SLOT_PAN_NW = 0;
    private static final int SLOT_PAN_N = 4;
    private static final int SLOT_PAN_NE = 8;
    private static final int SLOT_PAN_W = 18;
    private static final int SLOT_PAN_E = 26;
    private static final int SLOT_PAN_SW = 36;
    private static final int SLOT_PAN_S = 40;
    private static final int SLOT_PAN_SE = 44;

    private static final int SCROLLBAR_SIZE = 9;
    private static final int SCROLLBAR_CENTER = 49;

    private static final int MAX_PAN = 10;

    private String category;
    private int viewCenterX;
    private int viewCenterY;

    public ResearchGraphMenu(Player viewer) {
        this(viewer, firstCategory(), 0, 0);
    }

    public ResearchGraphMenu(Player viewer, String category, int viewCenterX, int viewCenterY) {
        super(viewer, getResourceTitle(), 54);
        this.category = category;
        this.viewCenterX = clamp(viewCenterX);
        this.viewCenterY = clamp(viewCenterY);
    }

    private static String getResourceTitle() {
        String code = Alkatraz.getTexturePackManager().getMenuTitleCode("research");
        if (code == null || code.isEmpty() || !TexturePackManager.isResourcePackEnabled()) {
            return lang().get("menu.research_library");
        }
        return code;
    }

    @Override
    protected void build() {
        fillAll();
        drawEdges();
        drawNodes();
        drawNavigationArrows();
        drawScrollbar();
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        int s = event.getSlot();

        if (s >= 45 && s < 54) {
            handleScrollbarClick(s);
            return true;
        }

        if (s == SLOT_PAN_NW) {
            viewCenterX = clamp(viewCenterX - 1);
            viewCenterY = clamp(viewCenterY - 1);
            refresh();
            return true;
        }
        if (s == SLOT_PAN_N) {
            viewCenterY = clamp(viewCenterY - 1);
            refresh();
            return true;
        }
        if (s == SLOT_PAN_NE) {
            viewCenterX = clamp(viewCenterX + 1);
            viewCenterY = clamp(viewCenterY - 1);
            refresh();
            return true;
        }
        if (s == SLOT_PAN_W) {
            viewCenterX = clamp(viewCenterX - 1);
            refresh();
            return true;
        }
        if (s == SLOT_PAN_E) {
            viewCenterX = clamp(viewCenterX + 1);
            refresh();
            return true;
        }
        if (s == SLOT_PAN_SW) {
            viewCenterX = clamp(viewCenterX - 1);
            viewCenterY = clamp(viewCenterY + 1);
            refresh();
            return true;
        }
        if (s == SLOT_PAN_S) {
            viewCenterY = clamp(viewCenterY + 1);
            refresh();
            return true;
        }
        if (s == SLOT_PAN_SE) {
            viewCenterX = clamp(viewCenterX + 1);
            viewCenterY = clamp(viewCenterY + 1);
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

    private void handleScrollbarClick(int slot) {
        if (slot == 45) {
            new WandTableSelectionMenu(viewer).open();
            return;
        }
        List<ResearchCategory> cats = sortedCategories();
        int activeIndex = findActiveCategoryIndex(cats);
        int offset = slot - SCROLLBAR_CENTER;
        int targetIndex = activeIndex + offset;
        if (targetIndex >= 0 && targetIndex < cats.size()) {
            String newCategory = cats.get(targetIndex).getId();
            if (!newCategory.equals(category)) {
                new ResearchGraphMenu(viewer, newCategory, 0, 0).open();
            }
        }
    }

    private void drawNavigationArrows() {
        inventory.setItem(SLOT_PAN_NW, panArrow("research.pan_up_left"));
        inventory.setItem(SLOT_PAN_N, panArrow("research.pan_up"));
        inventory.setItem(SLOT_PAN_NE, panArrow("research.pan_up_right"));
        inventory.setItem(SLOT_PAN_W, panArrow("research.pan_left"));
        inventory.setItem(SLOT_PAN_E, panArrow("research.pan_right"));
        inventory.setItem(SLOT_PAN_SW, panArrow("research.pan_down_left"));
        inventory.setItem(SLOT_PAN_S, panArrow("research.pan_down"));
        inventory.setItem(SLOT_PAN_SE, panArrow("research.pan_down_right"));
    }

    private void drawScrollbar() {
        inventory.setItem(45, button(Material.ARROW, lang().get("research.back_to_arcane"), "back_table"));

        List<ResearchCategory> cats = sortedCategories();
        int activeIndex = findActiveCategoryIndex(cats);

        for (int i = 0; i < SCROLLBAR_SIZE; i++) {
            int slot = 45 + i;
            if (slot == 45 || slot == 53) continue;
            int catIndex = activeIndex + (i - 4);
            if (catIndex >= 0 && catIndex < cats.size()) {
                ResearchCategory cat = cats.get(catIndex);
                boolean active = cat.getId().equals(category);
                Material mat = active ? Material.ENCHANTED_BOOK : cat.getIcon();
                String name = ColorFormat.format(active ? "&d" + cat.getDisplayName() : "&7" + cat.getDisplayName());
                ItemStack item = ItemBuilder.of(mat)
                        .rawName(name)
                        .glint(active)
                        .build();
                inventory.setItem(slot, item);
            }
        }

        int points = ProfileManager.getProfile(viewer, MagicProfile.class).getResearchPoints();
        inventory.setItem(53, ItemBuilder.of(Material.PAPER)
                .rawName(ColorFormat.format(lang().get("research.research_points", "points", String.valueOf(points))))
                .build());
    }

    private void drawNodes() {
        List<ResearchNode> nodes = ResearchService.getNodes(category);
        for (ResearchNode node : nodes) {
            Optional<Integer> slot = slotFor(node.getX(), node.getY());
            if (slot.isEmpty()) continue;
            ResearchState state = ResearchService.getState(viewer, node);
            inventory.setItem(slot.get(), createNodeItem(node, state));
        }
    }

    private void drawEdges() {
        List<ResearchNode> nodes = ResearchService.getNodes(category);
        for (ResearchNode child : nodes) {
            for (String parentId : child.getParents()) {
                ResearchService.getNode(parentId).ifPresent(parent -> drawEdge(parent, child));
            }
        }
    }

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

    private void drawStraightSegment(int fromX, int fromY, int toX, int toY, String stateKey) {
        if (fromX == toX && fromY == toY) return;

        Direction dir = getDirection(fromX, fromY, toX, toY);
        PieceType piece = (dir == Direction.E || dir == Direction.W)
            ? PieceType.STRAIGHT_H : PieceType.STRAIGHT_V;

        int x = fromX;
        int y = fromY;
        while (x != toX || y != toY) {
            if (x < toX) x++; else if (x > toX) x--;
            if (y < toY) y++; else if (y > toY) y--;

            slotFor(x, y).ifPresent(slot -> {
                if (isBlankSlot(slot)) {
                    inventory.setItem(slot, createConnectorItem(piece, stateKey));
                }
            });
        }
    }

    private void placeCornerAt(int x, int y, Direction in, Direction out, String stateKey) {
        PieceType piece = getPieceType(in, out);
        slotFor(x, y).ifPresent(slot -> {
            if (isBlankSlot(slot)) {
                inventory.setItem(slot, createConnectorItem(piece, stateKey));
            }
        });
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



    private ItemStack panArrow(String nameKey) {
        return ItemBuilder.of(Material.ARROW).rawName(ColorFormat.format(lang().get(nameKey))).build();
    }

    private ItemStack button(Material material, String name, String action) {
        ItemStack item = ItemBuilder.of(material).rawName(ColorFormat.format(name)).build();
        setMenuData(item, "action", action);
        return item;
    }

    private Optional<Integer> slotFor(int x, int y) {
        int viewX = x - viewCenterX;
        int viewY = y - viewCenterY;

        if (viewX < -4 || viewX > 4 || viewY < -2 || viewY > 2) {
            return Optional.empty();
        }

        int col = 4 + viewX;
        int row = 2 + viewY;
        return Optional.of(row * 9 + col);
    }

    private static int clamp(int value) {
        return Math.max(-MAX_PAN, Math.min(MAX_PAN, value));
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

    private int findActiveCategoryIndex(List<ResearchCategory> cats) {
        for (int i = 0; i < cats.size(); i++) {
            if (cats.get(i).getId().equals(category)) return i;
        }
        return 0;
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

    private Direction getDirection(int fromX, int fromY, int toX, int toY) {
        int dx = Integer.compare(toX, fromX);
        int dy = Integer.compare(toY, fromY);
        if (dx == 1) return Direction.E;
        if (dx == -1) return Direction.W;
        if (dy == 1) return Direction.S;
        return Direction.N;
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

    private ItemStack createConnectorItem(PieceType piece, String stateKey) {
        Material mat = switch (stateKey) {
            case "completed" -> Material.PURPLE_STAINED_GLASS_PANE;
            case "available" -> Material.PINK_STAINED_GLASS_PANE;
            default -> Material.RED_STAINED_GLASS_PANE;
        };
        int cmd = TexturePackManager.getConnectorCMD(piece.name().toLowerCase() + "_" + stateKey);
        return ItemBuilder.of(mat).name(" ").glint("in_progress".equals(stateKey)).customModelData(cmd).build();
    }

    private boolean isBlankSlot(int slot) {
        ItemStack existing = inventory.getItem(slot);
        if (existing == null || existing.getType() == Material.AIR) return true;
        return existing.isSimilar(Alkatraz.getGuiItemRegistry().getItem("blank"));
    }
}
