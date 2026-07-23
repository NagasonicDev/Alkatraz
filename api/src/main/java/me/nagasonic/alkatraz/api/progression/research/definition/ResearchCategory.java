package me.nagasonic.alkatraz.api.progression.research.definition;

import org.bukkit.Material;

/**
 * Defines a visual category grouping for research nodes in the research GUI.
 */
public class ResearchCategory {

    private final String id;
    private final String displayName;
    private final Material icon;

    /**
     * Constructs a new research category.
     *
     * @param id the unique identifier for this category
     * @param displayName the display name shown in the GUI
     * @param icon the Minecraft material used as the icon
     */
    public ResearchCategory(String id, String displayName, Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    /**
     * Returns the unique identifier for this category.
     *
     * @return the category ID
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
     * Returns the Minecraft material used as the category icon.
     *
     * @return the icon material
     */
    public Material getIcon() {
        return icon;
    }
}
