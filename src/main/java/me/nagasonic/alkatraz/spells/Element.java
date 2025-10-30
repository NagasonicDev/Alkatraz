package me.nagasonic.alkatraz.spells;

public enum Element {
    FIRE("#ff8c00Fire"),
    WATER("&9Water"),
    EARTH("#A0522DEarth"),
    AIR("&fAir"),
    LIGHT("#ffff87Light"),
    DARK("&8Dark"),
    NULL("&7Null");

    private String name;

    Element(String name){
        this.name = name;
    }

    public String getName() {
        return name;
    }


}
