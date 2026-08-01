package me.nagasonic.alkatraz.items.magic.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.configuration.requirement.RequirementFactory;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.util.MaterialCompat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RecipeLoader {

    private RecipeLoader() {}

    public static AlkatrazRecipe load(ConfigurationSection config) {
        String idStr = config.getString("definition", config.getString("id", ""));
        if (idStr.isBlank()) {
            Alkatraz.logWarning("Recipe missing 'definition'/'id' key");
            return null;
        }
        NamespacedKey key = MagicKeys.parse(idStr).orElse(null);
        if (key == null) {
            Alkatraz.logWarning("Invalid definition key in recipe: " + idStr);
            return null;
        }
        ItemStack result = buildResult(config, idStr);
        if (result == null) {
            Alkatraz.logWarning("Unknown result for recipe " + key);
            return null;
        }
        return load(config, key, result);
    }

    public static AlkatrazRecipe load(ConfigurationSection config, NamespacedKey key, ItemStack result) {
        String typeStr = config.getString("type", "shaped");
        RecipeType type = parseType(typeStr);

        AlkatrazRecipe.Builder builder = AlkatrazRecipe.builder()
                .key(key)
                .type(type)
                .result(result)
                .resultAmount(result.getAmount())
                .requirements(parseRequirements(config))
                .permissions(config.getStringList("permissions"))
                .hiddenWhenLocked(config.getBoolean("hidden_when_locked", false))
                .overrideVanilla(config.getBoolean("override_vanilla", false))
                .experience(config.getDouble("experience", 0))
                .cookingTime(config.getInt("cooking_time", 0))
                .displayName(config.getString("display_name"));

        ConfigurationSection unlockSection = config.getConfigurationSection("unlock");
        if (unlockSection != null) {
            builder.unlockMessage(unlockSection.getString("message", null));
        }

        switch (type) {
            case SHAPED -> {
                List<String> shapeLines = config.getStringList("shape");
                if (shapeLines.isEmpty()) {
                    Alkatraz.logWarning("Empty recipe shape for " + key);
                    return null;
                }
                ConfigurationSection ingredientsSection = config.getConfigurationSection("ingredients");
                if (ingredientsSection == null) {
                    Alkatraz.logWarning("Missing ingredients for recipe " + key);
                    return null;
                }
                Map<Character, Ingredient> ingredients = new HashMap<>();
                for (String ingredientKey : ingredientsSection.getKeys(false)) {
                    if (ingredientKey.length() != 1) continue;
                    char c = ingredientKey.charAt(0);
                    Ingredient ingredient = parseIngredient(ingredientsSection.get(ingredientKey));
                    if (ingredient != null) {
                        ingredients.put(c, ingredient);
                    }
                }
                builder.shape(shapeLines.toArray(new String[0])).ingredientMap(ingredients);
            }
            case SHAPELESS -> {
                List<Ingredient> ingredients = new ArrayList<>();
                for (Object value : config.getList("ingredients", List.of())) {
                    Ingredient ingredient = parseIngredient(value);
                    if (ingredient != null) ingredients.add(ingredient);
                }
                if (ingredients.isEmpty()) {
                    Alkatraz.logWarning("Empty ingredients for shapeless recipe " + key);
                    return null;
                }
                builder.ingredients(ingredients);
            }
            default -> applyStationPayload(builder, type, config);
        }

        return builder.build();
    }

    private static void applyStationPayload(AlkatrazRecipe.Builder builder, RecipeType type, ConfigurationSection config) {
        switch (type) {
            case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE -> {
                double exp = config.getDouble("experience", 0);
                int time = config.getInt("cooking_time", 0);
                ConfigurationSection section = config.getConfigurationSection(type.name().toLowerCase(Locale.ROOT));
                if (section != null) {
                    builder.input(resolveResultItem(section.getString("input", "")));
                    exp = section.getDouble("experience", exp);
                    time = section.getInt("cooking_time", time);
                } else {
                    builder.input(resolveResultItem(config.getString("input", "")));
                }
                builder.experience(exp).cookingTime(time);
            }
            case BREWING -> {
                ConfigurationSection section = config.getConfigurationSection("brewing");
                if (section != null) {
                    builder.input(resolveResultItem(section.getString("input", "")));
                    builder.addition(resolveResultItem(section.getString("ingredient", "")));
                }
            }
            case SMITHING -> {
                ConfigurationSection section = config.getConfigurationSection("smithing");
                if (section != null) {
                    builder.base(resolveResultItem(section.getString("base", "")));
                    builder.addition(resolveResultItem(section.getString("addition", "")));
                }
            }
            case ANVIL -> {
                ConfigurationSection section = config.getConfigurationSection("anvil");
                if (section != null) {
                    builder.base(resolveResultItem(section.getString("base", "")));
                    builder.addition(resolveResultItem(section.getString("addition", "")));
                }
            }
            case STONECUTTER -> {
                ConfigurationSection section = config.getConfigurationSection("stonecutter");
                if (section != null) {
                    builder.input(resolveResultItem(section.getString("input", "")));
                }
            }
            default -> {}
        }
    }

    private static RecipeType parseType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) return RecipeType.SHAPED;
        try {
            return RecipeType.valueOf(typeStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Alkatraz.logWarning("Unknown recipe type '" + typeStr + "', defaulting to SHAPED");
            return RecipeType.SHAPED;
        }
    }

    private static ItemStack buildResult(ConfigurationSection config, String idStr) {
        ConfigurationSection resultSection = config.getConfigurationSection("result");
        if (resultSection != null) {
            ItemStack stack = resolveResultItem(resultSection.getString("item", ""));
            if (stack == null) return null;
            stack.setAmount(resultSection.getInt("amount", 1));
            return stack;
        }
        NamespacedKey defKey = MagicKeys.parse(idStr).orElse(null);
        if (defKey == null) return null;
        ItemDefinition definition = MagicItemRegistries.ITEM_DEFINITIONS.get(defKey).orElse(null);
        if (definition == null) return null;
        return MagicItemStack.create(definition, MagicItemInstance.createDefault(defKey));
    }

    private static ItemStack resolveResultItem(String itemStr) {
        if (itemStr == null || itemStr.isBlank()) return null;
        Material material = MaterialCompat.resolve(itemStr);
        if (material != null) return new ItemStack(material);
        NamespacedKey itemKey = itemStr.contains(":")
                ? MagicKeys.parse(itemStr.toLowerCase(Locale.ROOT)).orElse(null)
                : MagicKeys.alkatraz(itemStr.toLowerCase(Locale.ROOT));
        if (itemKey == null) return null;
        Optional<ItemDefinition> itemDef = MagicItemRegistries.ITEM_DEFINITIONS.get(itemKey);
        if (itemDef.isPresent()) {
            return MagicItemStack.create(itemDef.get(), MagicItemInstance.createDefault(itemKey));
        }
        return null;
    }

    private static Ingredient parseIngredient(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("material")) {
                Material material = MaterialCompat.resolve(String.valueOf(map.get("material")));
                if (material != null) return new MaterialIngredient(material);
            }
            if (map.containsKey("type")) {
                return new TypeIngredient(String.valueOf(map.get("type")));
            }
            if (map.containsKey("tag")) {
                Tag<Material> tag = resolveTag(String.valueOf(map.get("tag")));
                if (tag != null) return new TagIngredient(tag);
            }
            if (map.containsKey("item")) {
                return resolveMagicItemIngredient(String.valueOf(map.get("item")));
            }
            return null;
        }
        if (value instanceof String string) {
            if (string.startsWith("type:")) return new TypeIngredient(string.substring(5));
            if (string.startsWith("tag:")) {
                Tag<Material> tag = resolveTag(string.substring(4));
                if (tag != null) return new TagIngredient(tag);
                return null;
            }
            Material material = MaterialCompat.resolve(string);
            if (material != null) return new MaterialIngredient(material);
            return resolveMagicItemIngredient(string);
        }
        return null;
    }

    private static Ingredient resolveMagicItemIngredient(String value) {
        NamespacedKey itemKey = value.contains(":")
                ? MagicKeys.parse(value.toLowerCase(Locale.ROOT)).orElse(null)
                : MagicKeys.alkatraz(value.toLowerCase(Locale.ROOT));
        if (itemKey == null) {
            Alkatraz.logWarning("Invalid key '" + value + "' in recipe");
            return null;
        }
        Optional<ItemDefinition> itemDef = MagicItemRegistries.ITEM_DEFINITIONS.get(itemKey);
        if (itemDef.isPresent()) {
            ItemStack exactStack = MagicItemStack.create(itemDef.get(), MagicItemInstance.createDefault(itemDef.get().getKey()));
            return new ExactItemIngredient(exactStack);
        }
        Alkatraz.logWarning("Unknown ingredient '" + value + "' in recipe");
        return null;
    }

    private static Tag<Material> resolveTag(String tagName) {
        NamespacedKey key = MagicKeys.parse(tagName).orElse(null);
        if (key == null) return null;
        return Bukkit.getTag("minecraft:item", key, Material.class);
    }

    private static List<Requirement> parseRequirements(ConfigurationSection config) {
        List<Requirement> requirements = new ArrayList<>();
        if (config.isList("requirements")) {
            for (Map<?, ?> reqMap : config.getMapList("requirements")) {
                ConfigurationSection section = RequirementFactory.toSection(reqMap);
                if (RequirementFactory.isRegistered(section.getString("type", ""))) {
                    requirements.add(RequirementFactory.create(null, section));
                }
            }
        } else {
            ConfigurationSection reqSection = config.getConfigurationSection("requirements");
            if (reqSection != null) {
                for (String reqKey : reqSection.getKeys(false)) {
                    ConfigurationSection singleReq = reqSection.getConfigurationSection(reqKey);
                    if (singleReq != null && RequirementFactory.isRegistered(singleReq.getString("type", ""))) {
                        requirements.add(RequirementFactory.create(null, singleReq));
                    }
                }
            }
        }
        return requirements;
    }
}
