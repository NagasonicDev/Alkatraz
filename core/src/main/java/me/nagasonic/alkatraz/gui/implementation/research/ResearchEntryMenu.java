package me.nagasonic.alkatraz.gui.implementation.research;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.lang.LangManager;
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

    private static LangManager lang() { return Alkatraz.getLangManager(); }

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
        super(viewer, lang().get("menu.research_entry", "name", node.getDisplayName()), 54);
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
                    viewer.sendMessage(ColorFormat.format(lang().get("research.insufficient_points", "points", String.valueOf(cost))));
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
        String name = state == ResearchState.HIDDEN ? lang().get("research.unknown") : "&d" + node.getDisplayName();
        List<String> lore = new ArrayList<>();

        lore.add(ColorFormat.format("&7State: " + stateColor(state) + formatState(state)));

        int cost = node.getResearchPointsCost();
        if (cost > 0) {
            lore.add(ColorFormat.format("&7Cost: &b" + cost + " Research Points"));
        }

        if (!node.getDescription().isEmpty()) {
            lore.add(ColorFormat.format(""));
            for (String line : node.getDescription()) {
                lore.add(ColorFormat.format("&7" + line));
            }
        }

        if (!node.getParents().isEmpty()) {
            lore.add(ColorFormat.format(""));
            lore.add(ColorFormat.format(lang().get("research.entry_requirements") + ":"));
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
        List<String> lore = new ArrayList<>();

        if (node.getObjectives().isEmpty()) {
            lore.add(ColorFormat.format(lang().get("research.no_tasks")));
        } else {
            for (ResearchObjective objective : node.getObjectives()) {
                int progress = ResearchService.getObjectiveProgress(viewer, node, objective);
                String mark = progress >= objective.getAmount() ? "&a[Done] " : "&e- ";
                lore.add(ColorFormat.format(mark + objective.getDisplayName()));
                lore.add(ColorFormat.format("&8  " + progress + "/" + objective.getAmount()));
            }
        }

        return ItemBuilder.of(material)
                .rawName(ColorFormat.format(lang().get("research.entry_tasks")))
                .rawLore(lore)
                .build();
    }

    private ItemStack rewardsActionItem(ResearchState state) {
        List<String> lore = new ArrayList<>();

        if (!node.getRewards().isEmpty()) {
            lore.add(ColorFormat.format(lang().get("research.entry_rewards") + ":"));
            for (ResearchReward reward : node.getRewards()) {
                lore.add(ColorFormat.format("&7" + rewardText(reward)));
            }
        }

        if (!node.getUnlocks().isEmpty()) {
            if (!lore.isEmpty()) lore.add(ColorFormat.format(""));
            lore.add(ColorFormat.format(lang().get("research.entry_unlocks") + ":"));
            for (String unlock : node.getUnlocks()) {
                lore.add(ColorFormat.format("&7" + unlock));
            }
        }

        if (!lore.isEmpty()) lore.add(ColorFormat.format(""));
        String actionName;
        Material actionMat;
        String actionTag = null;

        switch (state) {
            case AVAILABLE -> {
                actionMat = Material.WRITABLE_BOOK;
                actionName = lang().get("research.entry_start");
                int cost = node.getResearchPointsCost();
                if (cost > 0) {
                    int balance = ProfileManager.getProfile(viewer, MagicProfile.class).getResearchPoints();
                    boolean canAfford = balance >= cost;
                    lore.add(ColorFormat.format("&7Cost: &b" + cost + " RP &7| Balance: &f" + balance));
                    if (!canAfford) {
                        lore.add(ColorFormat.format(lang().get("research.insufficient_points", "points", String.valueOf(cost))));
                    }
                }
                lore.add(ColorFormat.format("&7Click to begin studying."));
                actionTag = "start";
            }
            case IN_PROGRESS -> {
                actionMat = Material.EXPERIENCE_BOTTLE;
                actionName = lang().get("research.entry_complete");
                if (ResearchService.objectivesComplete(viewer, node)) {
                    lore.add(ColorFormat.format("&7Click to record findings and claim rewards."));
                } else {
                    lore.add(ColorFormat.format("&7Complete all research tasks first."));
                }
                actionTag = "complete";
            }
            case COMPLETED -> {
                actionMat = Material.LIME_DYE;
                actionName = lang().get("research.entry_completed");
                lore.add(ColorFormat.format("&7This research is finished."));
            }
            case LOCKED -> {
                actionMat = Material.BARRIER;
                actionName = lang().get("research.entry_locked");
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
                actionName = lang().get("research.entry_hidden");
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
                .name(lang().get("research.back_to_graph"))
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
