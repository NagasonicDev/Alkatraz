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
        super(viewer, lang().get("menu.research_library"), 54);
        this.category = category;
        this.viewCenterX = clamp(viewCenterX);
        this.viewCenterY = clamp(viewCenterY);
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
        boolean bothDone = parentState == ResearchState.COMPLETED && childState == ResearchState.COMPLETED;
        return ItemBuilder.of(bothDone ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE).build();
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
}
