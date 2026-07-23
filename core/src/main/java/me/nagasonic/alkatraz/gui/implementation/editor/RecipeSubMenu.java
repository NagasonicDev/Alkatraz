package me.nagasonic.alkatraz.gui.implementation.editor;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.recipe.MagicItemRecipeManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.Arrays;

public class RecipeSubMenu extends Menu {

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    private static final int[] GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int DEF_SLOT = 32;
    private static final int SAVE_SLOT = 38;
    private static final int BACK_SLOT = 42;

    private final ItemDetailMenu parent;
    private final EditorSession session;
    private Map<Character, String> ingredients;
    private String[] recipeShape;

    public RecipeSubMenu(Player viewer, ItemDetailMenu parent) {
        super(viewer, ColorFormat.format("&8Edit Recipe"), 45);
        this.parent = parent;
        this.session = EditorSession.get(viewer.getUniqueId());
        this.ingredients = new LinkedHashMap<>();
        loadRecipeFromManager();
    }

    private void loadRecipeFromManager() {
        // Get the recipe from the MagicItemRecipeManager using the item key
        String defKey = session.defKey();
        if (defKey == null) return;
        
        // Convert the item key to a NamespacedKey
        NamespacedKey recipeKey = MagicKeys.alkatraz(defKey);
        
        // Get the recipe data from the manager
        MagicItemRecipeManager.RecipeData recipeData = MagicItemRecipeManager.RECIPES.get(recipeKey);
        
        if (recipeData != null) {
            // Load the shape
            this.recipeShape = recipeData.shape();
            
            // Load the ingredients
            for (Map.Entry<Character, RecipeChoice> entry : recipeData.ingredients().entrySet()) {
                if (entry.getValue() instanceof RecipeChoice.MaterialChoice materialChoice) {
                    Material material = materialChoice.getChoices().stream().findFirst().orElse(Material.STONE);
                    ingredients.put(entry.getKey(), material.name());
                }
            }
        } else {
            // Fallback to loading from config if recipe not found in manager
            parseIngredientsFromConfig();
        }
    }

    private void parseIngredientsFromConfig() {
        ConfigurationSection ingSec = session.config().getConfigurationSection("recipe.ingredients");
        if (ingSec != null) {
            for (String key : ingSec.getKeys(false)) {
                if (key.length() == 1) {
                    ingredients.put(key.charAt(0), ingSec.getString(key));
                }
            }
        }
        
        // Load shape from config
        List<String> shapeList = session.config().getStringList("recipe.shape");
        if (!shapeList.isEmpty()) {
            this.recipeShape = shapeList.toArray(new String[0]);
        } else {
            // Default empty shape
            this.recipeShape = new String[0];
        }
    }

    @Override
    protected void build() {
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, createBackgroundPane());
        }

        // Use the recipeShape field instead of loading from config
        List<String> shape = recipeShape != null ? Arrays.asList(recipeShape) : new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            int row = i / 3;
            int col = i % 3;
            char c = ' ';
            if (row < shape.size() && col < shape.get(row).length()) {
                c = shape.get(row).charAt(col);
            }
            inventory.setItem(GRID_SLOTS[i], createGridSlot(c));
        }

        inventory.setItem(DEF_SLOT, createDefinitionDisplay());
        inventory.setItem(SAVE_SLOT, createSaveButton());
        inventory.setItem(BACK_SLOT, createBackButton());
    }

    private ItemStack createBackgroundPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&7"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGridSlot(char c) {
        ItemStack item;
        if (c == ' ') {
            item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(lang().get("editor.recipe_empty"));
                List<String> lore = new ArrayList<>();
                lore.add(ColorFormat.format("&7Click to set ingredient"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        } else {
            String matName = ingredients.getOrDefault(c, "?");
            Material mat = Material.getMaterial(matName.toUpperCase());
            if (mat == null || mat == Material.AIR) mat = Material.STONE;
            item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(lang().get("editor.recipe_char", "char", String.valueOf(c)));
                List<String> lore = new ArrayList<>();
                lore.add(ColorFormat.format("&7Material: &f" + matName));
                lore.add("");
                lore.add(ColorFormat.format("&eLeft-click to change material"));
                lore.add(ColorFormat.format("&cRight-click to clear this slot"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
        setMenuData(item, "grid_char", String.valueOf(c));
        return item;
    }

    private ItemStack createDefinitionDisplay() {
        String defKey = session.defKey();
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(lang().get("editor.recipe_item", "key", defKey));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&7This is the result of the recipe."));
            lore.add("");
            lore.add(ColorFormat.format("&eCurrent ingredients:"));
            for (Map.Entry<Character, String> e : ingredients.entrySet()) {
                lore.add(ColorFormat.format("&7  '" + e.getKey() + "' &f= " + e.getValue()));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSaveButton() {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(lang().get("editor.save_recipe"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorFormat.format("&7Save recipe to config file"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        setMenuData(item, "action", "save_recipe");
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(lang().get("common.back"));
            item.setItemMeta(meta);
        }
        setMenuData(item, "action", "back");
        return item;
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            handleBack();
            return true;
        }

        if (slot == SAVE_SLOT) {
            handleSave();
            return true;
        }

        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (slot == GRID_SLOTS[i]) {
                handleGridClick(i, event.isRightClick());
                return true;
            }
        }

        return true;
    }

    private void handleGridClick(int gridIndex, boolean rightClick) {
        // Use the recipeShape array instead of loading from config
        List<String> shape = new ArrayList<>(recipeShape != null ? Arrays.asList(recipeShape) : new ArrayList<>());
        int row = gridIndex / 3;
        int col = gridIndex % 3;

        if (rightClick) {
            if (row < shape.size()) {
                String line = shape.get(row);
                if (col < line.length() && line.charAt(col) != ' ') {
                    char c = line.charAt(col);
                    StringBuilder sb = new StringBuilder(line);
                    sb.setCharAt(col, ' ');
                    shape.set(row, sb.toString());
                    // Update both the field and the config
                    recipeShape = shape.toArray(new String[0]);
                    session.config().set("recipe.shape", shape);
                    parent.markNeedsSave();
                    refresh();
                }
            }
            return;
        }

        char currentChar = ' ';
        if (row < shape.size() && col < shape.get(row).length()) {
            currentChar = shape.get(row).charAt(col);
        }

        if (currentChar == ' ') {
            char newChar = findNextChar();
            if (newChar == 0) {
                viewer.sendMessage(ColorFormat.format("&cToo many ingredient types! Remove some first."));
                return;
            }
            while (row >= shape.size()) {
                shape.add("   ");
            }
            StringBuilder sb = new StringBuilder(shape.get(row));
            sb.setCharAt(col, newChar);
            shape.set(row, sb.toString());
            // Update both the field and the config
            recipeShape = shape.toArray(new String[0]);
            session.config().set("recipe.shape", shape);
            int maxLen = shape.stream().mapToInt(String::length).max().orElse(3);
            for (int r = 0; r < shape.size(); r++) {
                while (shape.get(r).length() < maxLen) {
                    shape.set(r, shape.get(r) + " ");
                }
            }
            recipeShape = shape.toArray(new String[0]);
            session.config().set("recipe.shape", shape);
            final char assignedChar = newChar;
            viewer.closeInventory();
            session.setPendingChatAction("recipe_ingredient:" + assignedChar);
            EditorChatHandler.prompt(viewer,
                    "Enter material name for ingredient '" + assignedChar + "' (e.g. STONE, GOLD_INGOT):",
                    parent, () -> new RecipeSubMenu(viewer, parent).open());
        } else {
            viewer.closeInventory();
            session.setPendingChatAction("recipe_ingredient:" + currentChar);
            EditorChatHandler.prompt(viewer,
                    "Enter material name for ingredient '" + currentChar + "' (e.g. STONE, GOLD_INGOT):",
                    parent, () -> new RecipeSubMenu(viewer, parent).open());
        }
    }

    private char findNextChar() {
        // Use the recipeShape field instead of loading from config
        List<String> shape = recipeShape != null ? Arrays.asList(recipeShape) : new ArrayList<>();
        Set<Character> used = new HashSet<>();
        if (ingredients != null) {
            used.addAll(ingredients.keySet());
        }
        for (String line : shape) {
            for (char c : line.toCharArray()) {
                if (c != ' ') used.add(c);
            }
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            if (!used.contains(c)) return c;
        }
        return 0;
    }

    private void handleSave() {
        session.save();
        parent.markNeedsSave();
        viewer.sendMessage(ColorFormat.format("&aRecipe saved."));
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        refresh();
    }

    private void handleBack() {
        parent.markNeedsSave();
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        new ItemDetailMenu(viewer, parent.definition, parent.defKey).open();
    }
}
