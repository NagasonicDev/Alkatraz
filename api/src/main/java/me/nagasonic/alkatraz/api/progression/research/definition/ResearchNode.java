package me.nagasonic.alkatraz.api.progression.research.definition;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Defines a single node in the research tree, including its position,
 * prerequisites, objectives, rewards, and display properties.
 */
public class ResearchNode {

    private final String id;
    private final String displayName;
    private final List<String> description;
    private final String category;
    private final Material icon;
    private final int x;
    private final int y;
    private final List<String> parents;
    private final List<String> unlocks;
    private final List<ResearchObjective> objectives;
    private final List<ResearchReward> rewards;
    private final boolean hiddenUntilAvailable;
    private final int researchPointsCost;
    private final Map<String, List<int[]>> edgePaths;

    /**
     * Constructs a new research node.
     *
     * @param id the unique identifier for this node
     * @param displayName the display name shown in the GUI
     * @param description the multi-line description text
     * @param category the ID of the {@link ResearchCategory} this node belongs to
     * @param icon the Minecraft material used as the node icon
     * @param x the X position on the research tree grid
     * @param y the Y position on the research tree grid
     * @param parents the IDs of prerequisite research nodes
     * @param unlocks the IDs of research nodes unlocked by completing this one
     * @param objectives the objectives that must be completed
     * @param rewards the rewards granted upon completion
     * @param hiddenUntilAvailable whether this node is hidden until all prerequisites are met
     * @param researchPointsCost the research point cost to begin this research
     * @param edgePaths custom waypoints for edge paths from each parent, keyed by parent ID.
     *                   Each value is a list of {@code int[]} waypoints as {@code {x, y}}.
     *                   If a parent has no entry here, the path is auto-computed.
     */
    public ResearchNode(
            String id,
            String displayName,
            List<String> description,
            String category,
            Material icon,
            int x,
            int y,
            List<String> parents,
            List<String> unlocks,
            List<ResearchObjective> objectives,
            List<ResearchReward> rewards,
            boolean hiddenUntilAvailable,
            int researchPointsCost,
            Map<String, List<int[]>> edgePaths
    ) {
        this.id = id;
        this.displayName = displayName;
        this.description = List.copyOf(description);
        this.category = category;
        this.icon = icon;
        this.x = x;
        this.y = y;
        this.parents = List.copyOf(parents);
        this.unlocks = List.copyOf(unlocks);
        this.objectives = List.copyOf(objectives);
        this.rewards = List.copyOf(rewards);
        this.hiddenUntilAvailable = hiddenUntilAvailable;
        this.researchPointsCost = researchPointsCost;
        this.edgePaths = Map.copyOf(edgePaths.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.copyOf(e.getValue().stream()
                                .map(int[]::clone)
                                .toList())
                )));
    }

    /**
     * Returns the unique identifier for this node.
     *
     * @return the node ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the display name shown in the research GUI.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the multi-line description text for this node.
     *
     * @return an unmodifiable list of description lines
     */
    public List<String> getDescription() {
        return description;
    }

    /**
     * Returns the ID of the category this node belongs to.
     *
     * @return the category ID
     */
    public String getCategory() {
        return category;
    }

    /**
     * Returns the Minecraft material used as the node icon.
     *
     * @return the icon material
     */
    public Material getIcon() {
        return icon;
    }

    /**
     * Returns the X position on the research tree grid.
     *
     * @return the X coordinate
     */
    public int getX() {
        return x;
    }

    /**
     * Returns the Y position on the research tree grid.
     *
     * @return the Y coordinate
     */
    public int getY() {
        return y;
    }

    /**
     * Returns the IDs of prerequisite research nodes.
     *
     * @return an unmodifiable list of parent node IDs
     */
    public List<String> getParents() {
        return parents;
    }

    /**
     * Returns the IDs of research nodes unlocked by completing this node.
     *
     * @return an unmodifiable list of unlocked node IDs
     */
    public List<String> getUnlocks() {
        return unlocks;
    }

    /**
     * Returns the objectives that must be completed to finish this research.
     *
     * @return an unmodifiable list of objectives
     */
    public List<ResearchObjective> getObjectives() {
        return objectives;
    }

    /**
     * Returns the rewards granted upon completing this research.
     *
     * @return an unmodifiable list of rewards
     */
    public List<ResearchReward> getRewards() {
        return rewards;
    }

    /**
     * Returns whether this node is hidden until all prerequisites are met.
     *
     * @return {@code true} if hidden until available
     */
    public boolean isHiddenUntilAvailable() {
        return hiddenUntilAvailable;
    }

    /**
     * Returns the research point cost to begin this research.
     *
     * @return the cost in research points
     */
    public int getResearchPointsCost() {
        return researchPointsCost;
    }

    /**
     * Returns custom waypoints for edge paths from each parent.
     * Keys are parent node IDs, values are lists of {@code int[]} waypoints as {@code {x, y}}.
     * If a parent has no entry, the path is auto-computed via diagonal stepping.
     *
     * @return an unmodifiable map of parent ID to waypoints
     */
    public Map<String, List<int[]>> getEdgePaths() {
        return edgePaths;
    }
}
