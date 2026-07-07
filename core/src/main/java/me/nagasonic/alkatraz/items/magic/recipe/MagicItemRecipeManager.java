package me.nagasonic.alkatraz.items.magic.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MagicItemRecipeManager {

    public static final Map<NamespacedKey, RecipeData> RECIPES = new HashMap<>();

    private MagicItemRecipeManager() {}

    public static void registerRecipes() {
        for (Map.Entry<NamespacedKey, RecipeData> entry : RECIPES.entrySet()) {
            NamespacedKey key = entry.getKey();
            RecipeData data = entry.getValue();
            if (data.shape.length == 0) continue;

            ShapedRecipe recipe = new ShapedRecipe(key, data.result);
            recipe.shape(data.shape);
            for (Map.Entry<Character, RecipeChoice> ingredient : data.ingredients.entrySet()) {
                recipe.setIngredient(ingredient.getKey(), ingredient.getValue());
            }
            Bukkit.addRecipe(recipe);
            Alkatraz.logInfo("Registered recipe: " + key);
        }
    }

    public static void registerEngravingRecipe(EngravingDefinition definition, ConfigurationSection config) {
        ConfigurationSection recipeSection = config.getConfigurationSection("recipe");
        if (recipeSection == null) return;

        NamespacedKey key = definition.getKey();
        ItemStack result = MagicItemStack.createEngravingItem(definition);
        List<String> shapeLines = recipeSection.getStringList("shape");
        if (shapeLines.isEmpty()) {
            Alkatraz.logWarning("Empty recipe shape for " + key);
            return;
        }

        ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
        if (ingredientsSection == null) {
            Alkatraz.logWarning("Missing ingredients for recipe " + key);
            return;
        }

        Map<Character, RecipeChoice> ingredients = new HashMap<>();
        for (String ingredientKey : ingredientsSection.getKeys(false)) {
            if (ingredientKey.length() != 1) continue;
            char c = ingredientKey.charAt(0);
            String materialName = ingredientsSection.getString(ingredientKey);
            if (materialName == null) continue;
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                Alkatraz.logWarning("Unknown material '" + materialName + "' in recipe " + key);
                continue;
            }
            ingredients.put(c, new RecipeChoice.MaterialChoice(material));
        }

        List<Requirement> requirements = new ArrayList<>();
        ConfigurationSection reqSection = recipeSection.getConfigurationSection("requirements");
        if (reqSection != null) {
            for (String reqKey : reqSection.getKeys(false)) {
                ConfigurationSection singleReq = reqSection.getConfigurationSection(reqKey);
                if (singleReq != null) {
                    Requirement requirement = parseRequirement(singleReq);
                    if (requirement != null) {
                        requirements.add(requirement);
                    }
                }
            }
        }

        RECIPES.put(key, new RecipeData(key, result, shapeLines.toArray(new String[0]), ingredients, requirements));
    }

    public static void registerItemRecipe(ConfigurationSection config) {
        String defKeyStr = config.getString("definition");
        if (defKeyStr == null || defKeyStr.isBlank()) {
            Alkatraz.logWarning("Item recipe missing 'definition' key");
            return;
        }

        NamespacedKey defKey = MagicKeys.parse(defKeyStr).orElse(null);
        if (defKey == null) {
            Alkatraz.logWarning("Invalid definition key in recipe: " + defKeyStr);
            return;
        }

        ItemDefinition definition = MagicItemRegistries.ITEM_DEFINITIONS.get(defKey).orElse(null);
        if (definition == null) {
            Alkatraz.logWarning("Unknown item definition '" + defKeyStr + "' for recipe");
            return;
        }

        NamespacedKey recipeKey = defKey;
        MagicItemInstance instance = MagicItemInstance.createDefault(defKey);
        ItemStack result = MagicItemStack.create(definition, instance);

        List<String> shapeLines = config.getStringList("shape");
        if (shapeLines.isEmpty()) {
            Alkatraz.logWarning("Empty recipe shape for " + defKeyStr);
            return;
        }

        ConfigurationSection ingredientsSection = config.getConfigurationSection("ingredients");
        if (ingredientsSection == null) {
            Alkatraz.logWarning("Missing ingredients for recipe " + defKeyStr);
            return;
        }

        Map<Character, RecipeChoice> ingredients = new HashMap<>();
        for (String ingredientKey : ingredientsSection.getKeys(false)) {
            if (ingredientKey.length() != 1) continue;
            char c = ingredientKey.charAt(0);
            String value = ingredientsSection.getString(ingredientKey);
            if (value == null) continue;

            Material material = Material.matchMaterial(value);
            if (material != null) {
                ingredients.put(c, new RecipeChoice.MaterialChoice(material));
            } else {
                NamespacedKey itemKey = MagicKeys.alkatraz(value.toLowerCase(Locale.ROOT));
                Optional<ItemDefinition> itemDef = MagicItemRegistries.ITEM_DEFINITIONS.get(itemKey);
                if (itemDef.isPresent()) {
                    ingredients.put(c, new RecipeChoice.MaterialChoice(itemDef.get().visual().material()));
                } else {
                    Alkatraz.logWarning("Unknown ingredient '" + value + "' in recipe " + defKeyStr);
                }
            }
        }

        List<Requirement> requirements = new ArrayList<>();
        List<Map<?, ?>> reqList = config.getMapList("requirements");
        for (Map<?, ?> reqMap : reqList) {
            Requirement req = parseRequirementFromMap(reqMap);
            if (req != null) {
                requirements.add(req);
            }
        }

        RECIPES.put(recipeKey, new RecipeData(recipeKey, result, shapeLines.toArray(new String[0]), ingredients, requirements));
        Alkatraz.logInfo("Loaded recipe: " + defKeyStr);
    }

    public static List<Requirement> getRequirements(NamespacedKey recipeKey) {
        RecipeData data = RECIPES.get(recipeKey);
        if (data == null) return List.of();
        return data.requirements;
    }

    private static Requirement parseRequirement(ConfigurationSection section) {
        String type = section.getString("type");
        if (type == null) return null;
        return switch (type) {
            case "permission" -> {
                String permission = section.getString("permission");
                if (permission == null) yield null;
                yield new Requirement() {
                    @Override
                    public boolean isMet(Player player) {
                        return player.hasPermission(permission);
                    }

                    @Override
                    public String getDescription() {
                        return "Requires permission: " + permission;
                    }
                };
            }
            default -> null;
        };
    }

    private static Requirement parseRequirementFromMap(Map<?, ?> map) {
        Object typeObj = map.get("type");
        if (!(typeObj instanceof String type)) return null;
        return switch (type) {
            case "permission" -> {
                Object permObj = map.get("permission");
                if (!(permObj instanceof String permission)) yield null;
                yield new Requirement() {
                    @Override
                    public boolean isMet(Player player) {
                        return player.hasPermission(permission);
                    }

                    @Override
                    public String getDescription() {
                        return "Requires permission: " + permission;
                    }
                };
            }
            case "number_stat" -> {
                Object statObj = map.get("stat");
                Object minObj = map.get("minimum");
                Object descObj = map.get("description");
                if (!(statObj instanceof String stat) || !(minObj instanceof Number min)) yield null;
                String description = descObj instanceof String d ? d : "Requirement not met";
                yield new Requirement() {
                    @Override
                    public boolean isMet(Player player) {
                        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
                        if (profile == null) return false;
                        if ("circleLevel".equals(stat)) {
                            return profile.getCircleLevel() >= min.intValue();
                        }
                        return false;
                    }

                    @Override
                    public String getDescription() {
                        return description;
                    }
                };
            }
            default -> null;
        };
    }

    public record RecipeData(NamespacedKey key, ItemStack result, String[] shape,
                             Map<Character, RecipeChoice> ingredients, List<Requirement> requirements) {
        // Records automatically generate public accessors for all components
        // No additional code needed
    }
}
