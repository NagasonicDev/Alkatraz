package me.nagasonic.alkatraz.progression.research.definition;

import org.bukkit.Material;

import java.util.List;

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
    private final boolean hiddenUntilAvailable;

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
            boolean hiddenUntilAvailable
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
        this.hiddenUntilAvailable = hiddenUntilAvailable;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public Material getIcon() {
        return icon;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public List<String> getParents() {
        return parents;
    }

    public List<String> getUnlocks() {
        return unlocks;
    }

    public boolean isHiddenUntilAvailable() {
        return hiddenUntilAvailable;
    }
}
