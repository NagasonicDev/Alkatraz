package me.nagasonic.alkatraz.api;

/**
 * Represents the seven magical elements available in Alkatraz.
 * Each element has a display name and a color code used for chat formatting.
 */
public enum Element {
    /** Fire element, displayed in orange ({@code #ff8c00}). */
    FIRE("Fire", "#ff8c00"),
    /** Water element, displayed in dark blue ({@code &9}). */
    WATER("Water", "&9"),
    /** Earth element, displayed in sienna ({@code #A0522D}). */
    EARTH("Earth", "#A0522D"),
    /** Air element, displayed in white ({@code &f}). */
    AIR("Air", "&f"),
    /** Light element, displayed in light yellow ({@code #ffff87}). */
    LIGHT("Light", "#ffff87"),
    /** Dark element, displayed in dark gray ({@code &8}). */
    DARK("Dark", "&8"),
    /** None element, displayed in gray ({@code &7}). Represents the absence of an element. */
    NONE("None", "&7");

    private final String name;
    private final String color;

    /**
     * Constructs an element with the given display name and color code.
     *
     * @param name  the human-readable display name
     * @param color the color code prefix (hex or Minecraft ampersand code)
     */
    Element(String name, String color) {
        this.name = name;
        this.color = color;
    }

    /**
     * Returns the color-coded display name of this element.
     *
     * @return the element name prefixed with its color code
     */
    public String getName() {
        return color + name;
    }

    /**
     * Returns the raw color code for this element.
     *
     * @return the color code string (hex or ampersand format)
     */
    public String getColor() {
        return color;
    }
}
