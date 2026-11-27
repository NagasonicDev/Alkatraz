package me.nagasonic.alkatraz.gui.implementation.research;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.implementation.WandTableSelectionMenu;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.api.progression.research.ResearchState;
import me.nagasonic.alkatraz.api.progression.research.definition.ResearchCategory;
import me.nagasonic.alkatraz.api.progression.research.definition.ResearchNode;
import me.nagasonic.alkatraz.progression.research.ResearchService;
import me.nagasonic.alkatraz.texturepack.TexturePackManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ResearchGraphMenu extends Menu {

    private enum PieceType {
        STRAIGHT_H, STRAIGHT_V,
        CORNER_NE, CORNER_SE, CORNER_SW, CORNER_NW
    }

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    private static final PieceType[] PIECES = PieceType.values();
    private static final String[] STATE_KEYS = {"hidden", "locked", "completed", "available", "in_progress"};
    private static final String[][] CONNECTOR_KEYS = new String[PIECES.length][STATE_KEYS.length];

    static {
        for (int i = 0; i < PIECES.length; i++) {
            String pieceKey = PIECES[i].name().toLowerCase();
            for (int j = 0; j < STATE_KEYS.length; j++) {
                CONNECTOR_KEYS[i][j] = pieceKey + "_" + STATE_KEYS[j];
            }
        }
    }

    private static final Map<String, ItemStack> NODE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> CONNECTOR_CACHE = new ConcurrentHashMap<>();
    private static final int NODE_CACHE_LIMIT = 4096;
    private static int connectorToken = Integer.MIN_VALUE;
    private static volatile List<ResearchCategory> sortedCategoryCache;
    private static volatile int sortedCategoryToken = Integer.MIN_VALUE;

    /**
     * Drops every graph-derived item cache. Called on plugin disable so a
     * classloader-preserving reload never serves items built from a previous
     * research graph or texture pack.
     */
    public static void clearCaches() {
        NODE_CACHE.clear();
        CONNECTOR_CACHE.clear();
        sortedCategoryCache = null;
        sortedCategoryToken = Integer.MIN_VALUE;
        connectorToken = Integer.MIN_VALUE;
    }

    /**
     * Fingerprint of every custom-model-data value the connector items embed.
     * {@link TexturePackManager#reload()} swaps those values without touching the
     * research graph, so a mismatch here invalidates just the connector cache.
     */
    private static int connectorFingerprint() {
        int token = 0;
        for (String[] row : CONNECTOR_KEYS) {
            for (String key : row) {
                token = token * 31 + TexturePackManager.getConnectorCMD(key);
            }
        }
        return token;
    }

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
    private final boolean[] occupied = new boolean[54];

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
        return TexturePackManager.getMenuTitle("research", lang().get("menu.research_library"));
    }

    @Override
    protected void build() {
        int token = connectorFingerprint();
        if (token != connectorToken) {
            connectorToken = token;
            CONNECTOR_CACHE.clear();
        }
        Arrays.fill(occupied, false);
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
        inventory.setItem(45, button(Material.BARRIER, lang().get("research.back_to_arcane"), "back_table"));

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

    /**
     * Collects every cell used by an edge first, then picks each connector
     * texture from the path cells around it: opposite neighbours make a
     * straight run, perpendicular neighbours make a corner.
     */
    private void drawEdges() {
        List<ResearchNode> nodes = ResearchService.getNodes(category);
        Set<Long> nodeCells = new HashSet<>();
        for (ResearchNode node : nodes) nodeCells.add(cellKey(node.getX(), node.getY()));

        Set<Long> pathCells = new HashSet<>();
        Map<Long, String> connectorStates = new HashMap<>();
        for (ResearchNode child : nodes) {
            for (String parentId : child.getParents()) {
                ResearchService.getNode(parentId).ifPresent(parent -> {
                    ResearchState parentState = ResearchService.getState(viewer, parent);
                    if (parentState == ResearchState.HIDDEN) return;

                    List<int[]> path = new ArrayList<>();
                    path.add(new int[]{parent.getX(), parent.getY()});
                    List<int[]> waypoints = child.getEdgePaths() != null
                            ? child.getEdgePaths().get(parentId) : null;
                    if (waypoints != null) path.addAll(waypoints);
                    path.add(new int[]{child.getX(), child.getY()});

                    String stateKey = getStateKey(parentState, ResearchService.getState(viewer, child));
                    for (int i = 0; i < path.size() - 1; i++) {
                        addPath(path.get(i), path.get(i + 1), pathCells, connectorStates, nodeCells, stateKey);
                    }
                });
            }
        }

        for (Map.Entry<Long, String> entry : connectorStates.entrySet()) {
            long cell = entry.getKey();
            int x = (int) (cell >> 32);
            int y = (int) cell;
            int mask = 0;
            if (pathCells.contains(cellKey(x, y - 1))) mask |= 1;
            if (pathCells.contains(cellKey(x, y + 1))) mask |= 2;
            if (pathCells.contains(cellKey(x + 1, y))) mask |= 4;
            if (pathCells.contains(cellKey(x - 1, y))) mask |= 8;

            PieceType piece = pieceFor(mask);
            if (piece == null) continue;
            Optional<Integer> slot = slotFor(x, y);
            if (slot.isEmpty() || !isBlankSlot(slot.get())) continue;
            inventory.setItem(slot.get(), createConnectorItem(piece, entry.getValue()));
            occupied[slot.get()] = true;
        }
    }

    private static void addPath(int[] from, int[] to, Set<Long> pathCells,
                                Map<Long, String> connectorStates, Set<Long> nodeCells, String stateKey) {
        int x = from[0], y = from[1];
        addCell(x, y, pathCells, connectorStates, nodeCells, stateKey);
        while (x != to[0]) {
            x += Integer.signum(to[0] - x);
            addCell(x, y, pathCells, connectorStates, nodeCells, stateKey);
        }
        while (y != to[1]) {
            y += Integer.signum(to[1] - y);
            addCell(x, y, pathCells, connectorStates, nodeCells, stateKey);
        }
    }

    private static void addCell(int x, int y, Set<Long> pathCells,
                                Map<Long, String> connectorStates, Set<Long> nodeCells, String stateKey) {
        long key = cellKey(x, y);
        pathCells.add(key);
        if (!nodeCells.contains(key)) connectorStates.putIfAbsent(key, stateKey);
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    private ItemStack createNodeItem(ResearchNode node, ResearchState state) {
        Material material = switch (state) {
            case HIDDEN -> Material.RED_DYE;
            case LOCKED -> Material.RED_CONCRETE;
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
        int nodeTexture = TexturePackManager.getResearchNodeCMD(node.getId());
        String cacheKey = node.getId() + "|" + state + "|" + nodeTexture;
        ItemStack cached = NODE_CACHE.get(cacheKey);
        if (cached != null) return cached;

        ItemBuilder builder = ItemBuilder.of(material);
        if (nodeTexture > 0) builder.customModelData(nodeTexture);
        ItemStack item = builder
                .rawName(ColorFormat.format(name))
                .rawLore(lore)
                .build();
        if (state != ResearchState.HIDDEN) {
            setMenuData(item, "action", "research");
            setMenuData(item, "research_id", node.getId());
        }
        if (NODE_CACHE.size() >= NODE_CACHE_LIMIT) {
            var it = NODE_CACHE.keySet().iterator();
            if (it.hasNext()) NODE_CACHE.remove(it.next());
        }
        NODE_CACHE.put(cacheKey, item);
        return item;
    }



    private ItemStack panArrow(String nameKey) {
        String direction = nameKey.replace("research.pan_", "");
        int customModelData = TexturePackManager.getResearchPanArrowCMD(direction);
        ItemBuilder builder = ItemBuilder.of(Material.ARROW);
        if (customModelData > 0) builder.customModelData(customModelData);
        return builder.rawName(ColorFormat.format(lang().get(nameKey))).build();
    }

    private ItemStack button(Material material, String name, String action) {
        ItemBuilder builder = ItemBuilder.of(material).rawName(ColorFormat.format(name));
        if (material == Material.BARRIER) builder.guiModel("back_button");
        ItemStack item = builder.build();
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
        List<ResearchCategory> current = ResearchService.getCategories();
        int token = current.size();
        for (ResearchCategory cat : current) {
            token = token * 31 + cat.getId().hashCode();
            token = token * 31 + cat.getDisplayName().hashCode();
        }
        List<ResearchCategory> cached = sortedCategoryCache;
        if (cached != null && sortedCategoryToken == token) return cached;
        List<ResearchCategory> sorted = current.stream()
                .sorted(Comparator.comparing(ResearchCategory::getDisplayName))
                .toList();
        sortedCategoryCache = sorted;
        sortedCategoryToken = token;
        return sorted;
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

    private static PieceType pieceFor(int mask) {
        boolean n = (mask & 1) != 0, s = (mask & 2) != 0, e = (mask & 4) != 0, w = (mask & 8) != 0;
        if (n && s) return PieceType.STRAIGHT_V;
        if (e && w) return PieceType.STRAIGHT_H;
        if (n && e) return PieceType.CORNER_NE;
        if (e && s) return PieceType.CORNER_SE;
        if (s && w) return PieceType.CORNER_SW;
        if (w && n) return PieceType.CORNER_NW;
        if (n || s) return PieceType.STRAIGHT_V;
        if (e || w) return PieceType.STRAIGHT_H;
        return null;
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
        String cacheKey = piece + "|" + stateKey;
        ItemStack cached = CONNECTOR_CACHE.get(cacheKey);
        if (cached != null) return cached.clone();
        Material mat = switch (stateKey) {
            case "completed" -> Material.PURPLE_STAINED_GLASS_PANE;
            case "available" -> Material.PINK_STAINED_GLASS_PANE;
            default -> Material.RED_DYE;
        };
        int cmd = TexturePackManager.getConnectorCMD(piece.name().toLowerCase() + "_" + stateKey);
        ItemStack item = ItemBuilder.of(mat).name(" ").glint("in_progress".equals(stateKey)).customModelData(cmd).build();
        CONNECTOR_CACHE.put(cacheKey, item);
        return item.clone();
    }

    private boolean isBlankSlot(int slot) {
        return !occupied[slot];
    }
}
