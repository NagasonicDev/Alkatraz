package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum RecipeField {

    RESULT("result.item", "recipes.edit.result", Widget.ITEM, RecipeType.values()),
    RESULT_AMOUNT("result.amount", "recipes.edit.result_amount", Widget.INT, RecipeType.values()),
    DISPLAY_NAME("display_name", "recipes.edit.display_name", Widget.STRING, RecipeType.values()),
    OVERRIDE_VANILLA("override_vanilla", "recipes.edit.override_vanilla", Widget.BOOLEAN, RecipeType.values()),
    HIDDEN_WHEN_LOCKED("hidden_when_locked", "recipes.edit.hidden_when_locked", Widget.BOOLEAN, RecipeType.values()),
    UNLOCK_MESSAGE("unlock.message", "recipes.edit.unlock_message", Widget.STRING, RecipeType.values()),
    REQUIREMENTS("requirements", "recipes.edit.requirements", Widget.LIST, RecipeType.values()),
    PERMISSIONS("permissions", "recipes.edit.permissions", Widget.LIST, RecipeType.values()),

    SHAPE("shape", "recipes.edit.shape", Widget.SHAPE, RecipeType.SHAPED),
    SHAPELESS_INGREDIENTS("ingredients", "recipes.edit.ingredients", Widget.INGREDIENTS, RecipeType.SHAPELESS),

    INPUT("input", "recipes.edit.input", Widget.ITEM,
            RecipeType.FURNACE, RecipeType.BLAST_FURNACE, RecipeType.SMOKER, RecipeType.CAMPFIRE,
            RecipeType.BREWING, RecipeType.STONECUTTER),
    BASE("base", "recipes.edit.base", Widget.ITEM, RecipeType.SMITHING, RecipeType.ANVIL),
    ADDITION("addition", "recipes.edit.addition", Widget.ITEM,
            RecipeType.SMITHING, RecipeType.ANVIL, RecipeType.BREWING),

    EXPERIENCE("experience", "recipes.edit.experience", Widget.DOUBLE,
            RecipeType.FURNACE, RecipeType.BLAST_FURNACE, RecipeType.SMOKER, RecipeType.CAMPFIRE),
    COOKING_TIME("cooking_time", "recipes.edit.cooking_time", Widget.INT,
            RecipeType.FURNACE, RecipeType.BLAST_FURNACE, RecipeType.SMOKER, RecipeType.CAMPFIRE);

    private final String configKey;
    private final String langKey;
    private final Widget widget;
    private final Set<RecipeType> appliesTo;

    RecipeField(String configKey, String langKey, Widget widget, RecipeType... appliesTo) {
        this.configKey = configKey;
        this.langKey = langKey;
        this.widget = widget;
        this.appliesTo = EnumSet.copyOf(Arrays.asList(appliesTo));
    }

    public String configPath(RecipeType type) {
        if (widget == Widget.ITEM && this != RESULT) {
            return type.name().toLowerCase(Locale.ROOT) + "." + configKey;
        }
        return configKey;
    }

    public Widget widget() { return widget; }
    public String langKey() { return langKey; }
    public Set<RecipeType> appliesTo() { return appliesTo; }

    public static List<RecipeField> forType(RecipeType type) {
        List<RecipeField> fields = new ArrayList<>();
        for (RecipeField field : values()) {
            if (field.appliesTo.contains(type)) {
                fields.add(field);
            }
        }
        return fields;
    }

    public enum Widget {
        BOOLEAN, INT, DOUBLE, STRING, ITEM, LIST, SHAPE, INGREDIENTS
    }
}
