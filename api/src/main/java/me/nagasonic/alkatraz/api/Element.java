package me.nagasonic.alkatraz.api;

public enum Element {
    FIRE("Fire", "#ff8c00"),
    WATER("Water", "&9"),
    EARTH("Earth", "#A0522D"),
    AIR("Air", "&f"),
    LIGHT("Light", "#ffff87"),
    DARK("Dark", "&8"),
    NONE("None", "&7");

    private final String name;
    private final String color;

    Element(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return color + name;
    }

    public String getColor() {
        return color;
    }
}
