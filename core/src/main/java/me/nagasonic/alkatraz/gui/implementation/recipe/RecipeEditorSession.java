package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.ExactItemIngredient;
import me.nagasonic.alkatraz.items.magic.recipe.Ingredient;
import me.nagasonic.alkatraz.items.magic.recipe.MaterialIngredient;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeLoader;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import me.nagasonic.alkatraz.util.MaterialCompat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RecipeEditorSession {

    private static final Map<UUID, RecipeEditorSession> sessions = new HashMap<>();

    private final NamespacedKey key;
    private final File file;
    private final YamlConfiguration config;
    private final Map<Integer, ItemStack> dropItems = new HashMap<>();

    private RecipeEditorSession(Player player, NamespacedKey key) {
        this.key = key;
        File file = RecipeRegistry.fileOf(key);
        if (file == null) {
            file = new File(Alkatraz.getInstance().getDataFolder(), "magic/recipes/" + key.getKey() + ".yml");
        }
        this.file = file;
        this.config = YamlConfiguration.loadConfiguration(file);
        AlkatrazRecipe recipe = RecipeRegistry.get(key);
        if (recipe != null) {
            materialize(recipe);
        }
        sessions.put(player.getUniqueId(), this);
    }

    public static RecipeEditorSession start(Player player, NamespacedKey key) {
        RecipeEditorSession existing = sessions.get(player.getUniqueId());
        if (existing != null) {
            existing.returnDropItems(player);
            sessions.remove(player.getUniqueId());
        }
        return new RecipeEditorSession(player, key);
    }

    public static RecipeEditorSession get(UUID uuid) {
        return sessions.get(uuid);
    }

    public static void remove(UUID uuid) {
        sessions.remove(uuid);
    }

    private void materialize(AlkatrazRecipe recipe) {
        if (!config.contains("definition")) {
            config.set("definition", MagicKeys.format(key));
        }
        if (!config.contains("type")) {
            config.set("type", recipe.getType().name().toLowerCase(Locale.ROOT));
        }
        if (!config.contains("result.item")) {
            config.set("result.item", serializeItem(recipe.getResult()));
            config.set("result.amount", recipe.getResultAmount());
        }
        if (recipe.getType() == RecipeType.SHAPED) {
            if (!config.contains("shape") && recipe.getShape() != null) {
                config.set("shape", Arrays.asList(recipe.getShape()));
            }
            if (config.getConfigurationSection("ingredients") == null && recipe.getIngredientMap() != null) {
                Map<String, Object> ingredients = new LinkedHashMap<>();
                for (Map.Entry<Character, Ingredient> entry : recipe.getIngredientMap().entrySet()) {
                    ingredients.put(String.valueOf(entry.getKey()), serializeIngredient(entry.getValue()));
                }
                config.set("ingredients", ingredients);
            }
        } else if (recipe.getType() == RecipeType.SHAPELESS) {
            if (config.getList("ingredients") == null && !recipe.getIngredients().isEmpty()) {
                List<String> list = new ArrayList<>();
                for (Ingredient ingredient : recipe.getIngredients()) {
                    list.add(serializeIngredient(ingredient));
                }
                config.set("ingredients", list);
            }
        } else {
            materializeStationSection(recipe);
        }
    }

    private void materializeStationSection(AlkatrazRecipe recipe) {
        String section = recipe.getType().name().toLowerCase(Locale.ROOT);
        switch (recipe.getType()) {
            case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE -> {
                if (config.getConfigurationSection(section) == null && recipe.getInput() != null) {
                    config.set(section + ".input", serializeItem(recipe.getInput()));
                }
            }
            case BREWING -> {
                if (config.getConfigurationSection(section) == null) {
                    if (recipe.getInput() != null) config.set(section + ".input", serializeItem(recipe.getInput()));
                    if (recipe.getAddition() != null) config.set(section + ".ingredient", serializeItem(recipe.getAddition()));
                }
            }
            case SMITHING, ANVIL -> {
                if (config.getConfigurationSection(section) == null) {
                    if (recipe.getBase() != null) config.set(section + ".base", serializeItem(recipe.getBase()));
                    if (recipe.getAddition() != null) config.set(section + ".addition", serializeItem(recipe.getAddition()));
                }
            }
            case STONECUTTER -> {
                if (config.getConfigurationSection(section) == null && recipe.getInput() != null) {
                    config.set(section + ".input", serializeItem(recipe.getInput()));
                }
            }
            default -> {}
        }
    }

    private static String serializeIngredient(Ingredient ingredient) {
        if (ingredient instanceof MaterialIngredient materialIngredient) {
            return materialIngredient.describe();
        }
        if (ingredient instanceof ExactItemIngredient
                && ingredient.toChoice() instanceof RecipeChoice.ExactChoice exactChoice) {
            List<ItemStack> choices = exactChoice.getChoices();
            if (!choices.isEmpty()) {
                ItemStack stack = choices.get(0);
                Optional<NamespacedKey> magicKey = MagicItemStack.readDefinitionKey(stack);
                if (magicKey.isPresent()) {
                    return MagicKeys.format(magicKey.get());
                }
            }
        }
        return ingredient.describe();
    }

    public YamlConfiguration config() { return config; }
    public NamespacedKey key() { return key; }
    public File file() { return file; }

    public RecipeType type() {
        String type = config.getString("type", "shaped");
        try {
            return RecipeType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RecipeType.SHAPED;
        }
    }

    public ItemStack stagedItem(int slot) {
        return dropItems.get(slot);
    }

    public void setDropItem(int slot, ItemStack stack, String configPath) {
        if (stack == null || stack.getType().isAir()) {
            clearDropItem(slot, configPath);
            return;
        }
        dropItems.put(slot, stack.clone());
        config.set(configPath, serializeItem(stack));
    }

    public void clearDropItem(int slot, String configPath) {
        dropItems.remove(slot);
        config.set(configPath, null);
    }

    public void returnDropItems(Player player) {
        for (ItemStack stack : dropItems.values()) {
            player.getInventory().addItem(stack)
                    .values().forEach(leftover ->
                            player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        dropItems.clear();
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            Alkatraz.logSevere("Failed to save recipe " + key + ": " + e.getMessage());
        }
    }

    public String validate() {
        try {
            AlkatrazRecipe loaded = RecipeLoader.load(config);
            if (loaded == null) return "recipes.validation.invalid";
        } catch (Exception e) {
            return "recipes.validation.invalid";
        }
        return null;
    }

    public static String serializeItem(ItemStack stack) {
        if (stack == null) return null;
        Optional<NamespacedKey> magicKey = MagicItemStack.readDefinitionKey(stack);
        if (magicKey.isPresent()) {
            return MagicKeys.format(magicKey.get());
        }
        return stack.getType().name();
    }

    public static ItemStack deserializeItem(String value) {
        if (value == null || value.isBlank()) return null;
        Material material = MaterialCompat.resolve(value);
        if (material != null) return new ItemStack(material);
        NamespacedKey itemKey = MagicKeys.parse(value).orElse(null);
        if (itemKey == null) return null;
        Optional<ItemDefinition> itemDef = MagicItemRegistries.ITEM_DEFINITIONS.get(itemKey);
        if (itemDef.isPresent()) {
            return MagicItemStack.create(itemDef.get(), MagicItemInstance.createDefault(itemKey));
        }
        return null;
    }
}
