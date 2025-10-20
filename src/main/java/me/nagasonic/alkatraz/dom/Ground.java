package me.nagasonic.alkatraz.dom;

import org.bukkit.Material;
import org.bukkit.block.Biome;

public enum Ground {
    DIRT(Material.DIRT),
    GRASS(Material.GRASS_BLOCK),
    SAND(Material.SAND),
    RED_SAND(Material.RED_SAND),
    MYCELIUM(Material.MYCELIUM);

    private Material type;
    Ground(Material type){
        this.type = type;
    }

    public Material getType(){
        return this.type;
    }

    public static Material getGround(Biome biome){
        return switch (biome){
            case DESERT -> SAND.getType();
            case BADLANDS -> RED_SAND.getType();
            case WOODED_BADLANDS -> RED_SAND.getType();
            case ERODED_BADLANDS -> RED_SAND.getType();
            case MUSHROOM_FIELDS -> MYCELIUM.getType();
            case PLAINS -> GRASS.getType();
            case MEADOW -> GRASS.getType();
            default -> DIRT.getType();
        };
    }
}
