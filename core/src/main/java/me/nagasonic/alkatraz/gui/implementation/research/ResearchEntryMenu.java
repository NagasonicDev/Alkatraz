package me.nagasonic.alkatraz.gui.implementation.research;

import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.progression.research.ResearchService;
import me.nagasonic.alkatraz.progression.research.ResearchState;
import me.nagasonic.alkatraz.progression.research.definition.ResearchNode;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ResearchEntryMenu extends Menu {

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
        inventory.setItem(4, summaryItem(state));
        inventory.setItem(20, requirementsItem(state));
        inventory.setItem(24, unlocksItem());
        inventory.setItem(31, actionItem(state));
        inventory.setItem(45, backItem());
        addLinkedResearch();
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
            ResearchService.start(viewer, node);
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
            ResearchService.getNode(id).ifPresent(next -> new ResearchEntryMenu(viewer, next, category, offsetX, offsetY).open());
            return true;
        }
        return true;
    }

    private ItemStack summaryItem(ResearchState state) {
        ItemStack item = new ItemStack(state == ResearchState.COMPLETED ? Material.ENCHANTED_BOOK : node.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&d" + node.getDisplayName()));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7State: " + stateColor(state) + formatState(state)));
        lore.add(ColorFormat.format("&7Category: &f" + node.getCategory()));
        lore.add(ColorFormat.format("&7"));
        for (String line : node.getDescription()) {
            lore.add(ColorFormat.format("&7" + line));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack requirementsItem(ResearchState state) {
        ItemStack item = new ItemStack(state == ResearchState.LOCKED ? Material.REDSTONE_TORCH : Material.TORCH);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&eRequirements"));
        List<String> lore = new ArrayList<>();
        if (node.getParents().isEmpty()) {
            lore.add(ColorFormat.format("&7No prior research required."));
        } else {
            for (String parentId : node.getParents()) {
                ResearchService.getNode(parentId).ifPresent(parent -> {
                    ResearchState parentState = ResearchService.getState(viewer, parent);
                    lore.add(ColorFormat.format(stateColor(parentState) + parent.getDisplayName()));
                });
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack unlocksItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&aUnlocks"));
        List<String> lore = new ArrayList<>();
        if (node.getUnlocks().isEmpty()) {
            lore.add(ColorFormat.format("&7No configured unlock text."));
        } else {
            for (String unlock : node.getUnlocks()) {
                lore.add(ColorFormat.format("&7" + unlock));
            }
        }
        List<ResearchNode> children = ResearchService.getChildren(node.getId());
        if (!children.isEmpty()) {
            lore.add(ColorFormat.format("&7"));
            lore.add(ColorFormat.format("&dConnected research:"));
            for (ResearchNode child : children) {
                lore.add(ColorFormat.format("&8-> &7" + child.getDisplayName()));
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack actionItem(ResearchState state) {
        Material material = switch (state) {
            case AVAILABLE -> Material.WRITABLE_BOOK;
            case IN_PROGRESS -> Material.EXPERIENCE_BOTTLE;
            case COMPLETED -> Material.LIME_DYE;
            default -> Material.BARRIER;
        };
        String name = switch (state) {
            case AVAILABLE -> "&eStart Research";
            case IN_PROGRESS -> "&bComplete Research";
            case COMPLETED -> "&aCompleted";
            case LOCKED -> "&cLocked";
            case HIDDEN -> "&8Hidden";
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(name));
        List<String> lore = new ArrayList<>();
        if (state == ResearchState.AVAILABLE) {
            lore.add(ColorFormat.format("&7Begin studying this research."));
        } else if (state == ResearchState.IN_PROGRESS) {
            lore.add(ColorFormat.format("&7Record your findings."));
        } else {
            lore.add(ColorFormat.format("&7No action available."));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        if (state == ResearchState.AVAILABLE) {
            setMenuData(item, "action", "start");
        } else if (state == ResearchState.IN_PROGRESS) {
            setMenuData(item, "action", "complete");
        }
        return item;
    }

    private ItemStack backItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&fBack to Graph"));
        item.setItemMeta(meta);
        setMenuData(item, "action", "back");
        return item;
    }

    private void addLinkedResearch() {
        int slot = 37;
        for (String parentId : node.getParents()) {
            if (slot > 43) break;
            int finalSlot = slot;
            ResearchService.getNode(parentId).ifPresent(parent -> inventory.setItem(finalSlot, linkedItem(parent, "&7Requires")));
            slot++;
        }
        for (ResearchNode child : ResearchService.getChildren(node.getId())) {
            if (slot > 43) break;
            inventory.setItem(slot++, linkedItem(child, "&8->"));
        }
    }

    private ItemStack linkedItem(ResearchNode linked, String prefix) {
        ResearchState state = ResearchService.getState(viewer, linked);
        ItemStack item = new ItemStack(state == ResearchState.COMPLETED ? Material.ENCHANTED_BOOK : linked.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format(prefix + " " + linked.getDisplayName()));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7State: " + stateColor(state) + formatState(state)));
        lore.add(ColorFormat.format("&eClick to inspect"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        setMenuData(item, "action", "linked_research");
        setMenuData(item, "research_id", linked.getId());
        return item;
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
}
