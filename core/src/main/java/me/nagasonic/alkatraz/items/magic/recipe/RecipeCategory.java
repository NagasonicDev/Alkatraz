package me.nagasonic.alkatraz.items.magic.recipe;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum RecipeCategory {

    CRAFTING("crafting", Material.CRAFTING_TABLE, RecipeType.SHAPED, RecipeType.SHAPELESS),
    FURNACE("furnace", Material.FURNACE, RecipeType.FURNACE),
    BLAST_FURNACE("blast_furnace", Material.BLAST_FURNACE, RecipeType.BLAST_FURNACE),
    SMOKER("smoker", Material.SMOKER, RecipeType.SMOKER),
    CAMPFIRE("campfire", Material.CAMPFIRE, RecipeType.CAMPFIRE),
    BREWING("brewing", Material.BREWING_STAND, RecipeType.BREWING),
    SMITHING("smithing", Material.SMITHING_TABLE, RecipeType.SMITHING),
    STONECUTTER("stonecutter", Material.STONECUTTER, RecipeType.STONECUTTER),
    OTHER("other", Material.CHEST, RecipeType.ANVIL, RecipeType.CUSTOM);

    private final String id;
    private final Material icon;
    private final Set<RecipeType> types;

    RecipeCategory(String id, Material icon, RecipeType... types) {
        this.id = id;
        this.icon = icon;
        this.types = EnumSet.copyOf(Arrays.asList(types));
    }

    public String getId() { return id; }

    public Material getIcon() { return icon; }

    public Set<RecipeType> getTypes() { return types; }

    public boolean contains(RecipeType type) { return types.contains(type); }

    public static RecipeCategory of(RecipeType type) {
        for (RecipeCategory category : values()) {
            if (category.contains(type)) return category;
        }
        return OTHER;
    }

    public static RecipeCategory byId(String id) {
        for (RecipeCategory category : values()) {
            if (category.id.equalsIgnoreCase(id)) return category;
        }
        return null;
    }
}
