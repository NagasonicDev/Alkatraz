package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.items.magic.recipe.MagicItemRecipeManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class RecipeBookMenu extends PagedMenu<MagicItemRecipeManager.RecipeData> {

    private static final int[] RECIPE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public RecipeBookMenu(Player viewer) {
        super(viewer, ColorFormat.format("&6Recipe Book"), 54, getSortedRecipes(), 28);
        this.contentSlots = RECIPE_SLOTS;
        this.nextPageSlot = 53;
        this.previousPageSlot = 45;
        this.backButtonSlot = 49;
    }

    private static List<MagicItemRecipeManager.RecipeData> getSortedRecipes() {
        return MagicItemRecipeManager.RECIPES.values().stream()
                .sorted(Comparator.comparing(r -> r.key().getKey()))
                .collect(Collectors.toList());
    }

    @Override
    protected void addDecorations() {
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, Utils.getBlank());
        }

        ItemStack info = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&6&lRecipe Book"));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7Browse all available"));
        lore.add(ColorFormat.format("&7magic item recipes."));
        meta.setLore(lore);
        info.setItemMeta(meta);
        inventory.setItem(4, info);
    }

    @Override
    protected void addBackButton() {
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta m = close.getItemMeta();
        m.setDisplayName(ColorFormat.format("&cClose"));
        close.setItemMeta(m);
        setMenuData(close, "action", "back");
        inventory.setItem(backButtonSlot, close);
    }

    @Override
    protected void handleBackClick() {
        close();
    }

    @Override
    protected ItemStack createDisplayItem(MagicItemRecipeManager.RecipeData recipe, int index) {
        ItemStack item = recipe.result().clone();
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ColorFormat.format("&f" + getItemDisplayName(recipe)));

        List<String> lore = new ArrayList<>();

        lore.add(ColorFormat.format("&7&m---&r &6Ingredients &7&m---"));
        java.util.Set<Character> seen = new java.util.HashSet<>();
        for (String row : recipe.shape()) {
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c != ' ' && seen.add(c)) {
                    RecipeChoice choice = recipe.ingredients().get(c);
                    lore.add(ColorFormat.format(" &7" + c + " &8= &f" + getIngredientName(choice)));
                }
            }
        }

        if (!recipe.requirements().isEmpty()) {
            lore.add("");
            lore.add(ColorFormat.format("&7&m---&r &cRequirements &7&m---"));
            for (me.nagasonic.alkatraz.configuration.requirement.Requirement req : recipe.requirements()) {
                lore.add(ColorFormat.format(" &7- " + req.getDescription()));
            }
        }

        lore.add("");
        lore.add(ColorFormat.format("&eClick for details"));

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    @Override
    protected void handleContentClick(MagicItemRecipeManager.RecipeData recipe, InventoryClickEvent event) {
        new RecipeDetailMenu(viewer, recipe).open();
    }

    private String getItemDisplayName(MagicItemRecipeManager.RecipeData recipe) {
        ItemStack result = recipe.result();
        if (result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
            return result.getItemMeta().getDisplayName();
        }
        return prettifyKey(recipe.key().getKey());
    }

    private String getIngredientName(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            List<Material> materials = mc.getChoices();
            if (!materials.isEmpty()) {
                return prettifyKey(materials.get(0).getKey().getKey());
            }
        }
        return "???";
    }

    private static String prettifyKey(String key) {
        int colon = key.indexOf(':');
        if (colon >= 0) key = key.substring(colon + 1);
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
