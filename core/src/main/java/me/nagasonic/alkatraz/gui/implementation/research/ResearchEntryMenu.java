package me.nagasonic.alkatraz.gui.implementation.research;

import me.nagasonic.alkatraz.gui.Menu;
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
        inventory.setItem(19, requirementsItem(state));
        inventory.setItem(22, tasksItem());
        inventory.setItem(25, rewardsItem());
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
            lore.add(ColorFormat.format("&aNo prior research required."));
        } else {
            for (String parentId : node.getParents()) {
                ResearchService.getNode(parentId).ifPresent(parent -> {
                    ResearchState parentState = ResearchService.getState(viewer, parent);
                    String mark = parentState == ResearchState.COMPLETED ? "&a[Done] " : "&c[Missing] ";
                    lore.add(ColorFormat.format(mark + stateColor(parentState) + parent.getDisplayName()));
                });
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack tasksItem() {
        ItemStack item = new ItemStack(ResearchService.objectivesComplete(viewer, node) ? Material.FILLED_MAP : Material.MAP);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&bResearch Tasks"));
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
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rewardsItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&aRewards"));
        List<String> lore = new ArrayList<>();
        if (node.getRewards().isEmpty()) {
            lore.add(ColorFormat.format("&7No configured rewards."));
        } else {
            for (ResearchReward reward : node.getRewards()) {
                lore.add(ColorFormat.format("&7" + rewardText(reward)));
            }
        }
        if (node.getUnlocks().isEmpty()) {
            lore.add(ColorFormat.format("&7"));
            lore.add(ColorFormat.format("&7No connected unlock text."));
        } else {
            lore.add(ColorFormat.format("&7"));
            lore.add(ColorFormat.format("&dUnlocks:"));
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
            if (ResearchService.objectivesComplete(viewer, node)) {
                lore.add(ColorFormat.format("&7Record your findings and claim rewards."));
            } else {
                lore.add(ColorFormat.format("&7Complete all research tasks first."));
            }
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
