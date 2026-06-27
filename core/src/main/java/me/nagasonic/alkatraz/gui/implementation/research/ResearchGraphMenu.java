package me.nagasonic.alkatraz.gui.implementation.research;

import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.progression.research.ResearchService;
import me.nagasonic.alkatraz.progression.research.ResearchState;
import me.nagasonic.alkatraz.progression.research.definition.ResearchCategory;
import me.nagasonic.alkatraz.progression.research.definition.ResearchNode;
import me.nagasonic.alkatraz.util.ColorFormat;
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

    private static final int GRAPH_ROWS = 5;
    private static final int GRAPH_COLUMNS = 9;

    private String category;
    private int offsetX;
    private int offsetY;

    public ResearchGraphMenu(Player viewer) {
        this(viewer, firstCategory(), 0, 0);
    }

    public ResearchGraphMenu(Player viewer, String category, int offsetX, int offsetY) {
        super(viewer, ColorFormat.format("&5Research Library"), 54);
        this.category = category;
        this.offsetX = Math.max(0, offsetX);
        this.offsetY = Math.max(0, offsetY);
    }

    @Override
    protected void build() {
        inventory.clear();
        List<ResearchNode> nodes = ResearchService.getNodes(category);
        drawEdges(nodes);
        drawNodes(nodes);
        drawControls();
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        String action = getStringData(clicked, "action");
        if ("pan_left".equals(action)) {
            offsetX = Math.max(0, offsetX - 1);
            refresh();
            return true;
        }
        if ("pan_right".equals(action)) {
            offsetX++;
            refresh();
            return true;
        }
        if ("pan_up".equals(action)) {
            offsetY = Math.max(0, offsetY - 1);
            refresh();
            return true;
        }
        if ("pan_down".equals(action)) {
            offsetY++;
            refresh();
            return true;
        }
        if ("category".equals(action)) {
            new ResearchCategoriesMenu(viewer).open();
            return true;
        }
        if ("research".equals(action)) {
            String id = getStringData(clicked, "research_id");
            ResearchService.getNode(id).ifPresent(node -> new ResearchEntryMenu(viewer, node, category, offsetX, offsetY).open());
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
                if (inventory.getItem(slot) == null) {
                    inventory.setItem(slot, createEdgeItem(parent, child));
                }
            });
            if (x != cx) x += dx;
            if (y != cy) y += dy;
        }
    }

    private void drawControls() {
        inventory.setItem(45, button(Material.ARROW, "&fLeft", "pan_left"));
        inventory.setItem(46, button(Material.ARROW, "&fRight", "pan_right"));
        inventory.setItem(47, button(Material.ARROW, "&fUp", "pan_up"));
        inventory.setItem(48, button(Material.ARROW, "&fDown", "pan_down"));
        inventory.setItem(49, infoItem());

        inventory.setItem(53, button(Material.BOOKSHELF, "&dCategories", "category"));
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
            lore.add(ColorFormat.format("&8DAG position: " + node.getX() + ", " + node.getY()));
            lore.add(ColorFormat.format("&eClick to inspect"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        setMenuData(item, "action", "research");
        setMenuData(item, "research_id", node.getId());
        return item;
    }

    private ItemStack createEdgeItem(ResearchNode parent, ResearchNode child) {
        ItemStack item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&5Arcane Link"));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7" + parent.getDisplayName()));
        lore.add(ColorFormat.format("&8-> &7" + child.getDisplayName()));
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
        lore.add(ColorFormat.format("&7View: &f" + offsetX + ", " + offsetY));
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
        int viewX = x - offsetX;
        int viewY = y - offsetY;
        if (viewX < 0 || viewX >= GRAPH_COLUMNS || viewY < 0 || viewY >= GRAPH_ROWS) {
            return Optional.empty();
        }
        return Optional.of(viewY * GRAPH_COLUMNS + viewX);
    }

    private String formatState(ResearchState state) {
        return state.name().toLowerCase().replace('_', ' ');
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
        return ResearchService.getCategories().stream()
                .min(Comparator.comparing(ResearchCategory::getDisplayName))
                .map(ResearchCategory::getId)
                .orElse("general");
    }
}
