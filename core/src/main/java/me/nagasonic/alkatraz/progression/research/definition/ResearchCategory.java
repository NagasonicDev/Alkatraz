package me.nagasonic.alkatraz.progression.research.definition;

import org.bukkit.Material;

public class ResearchCategory {

    private final String id;
    private final String displayName;
    private final Material icon;

    public ResearchCategory(String id, String displayName, Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }
}
