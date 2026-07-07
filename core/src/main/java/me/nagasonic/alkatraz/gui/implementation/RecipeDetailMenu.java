package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.recipe.MagicItemRecipeManager.RecipeData;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RecipeDetailMenu extends Menu {

    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT_SLOT = 24;
    private static final int BACK_SLOT = 40;

    private final RecipeData recipe;

    public RecipeDetailMenu(Player viewer, RecipeData recipe) {
        super(viewer, ColorFormat.format("&6Recipe Details"), 45);
        this.recipe = recipe;
    }

    @Override
    protected void build() {
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, Utils.getBlank());
        }

        int idx = 0;
        for (String row : recipe.shape()) {
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch != ' ') {
                    RecipeChoice choice = recipe.ingredients().get(ch);
                    inventory.setItem(GRID[idx], makeIngredientItem(choice));
                }
                idx++;
            }
            while (idx % 3 != 0) {
                idx++;
            }
        }

        ItemStack result = recipe.result().clone();
        ItemMeta rMeta = result.getItemMeta();
        List<String> rLore = new ArrayList<>();
        if (rMeta.hasLore()) rLore.addAll(rMeta.getLore());
        rLore.add("");
        rLore.add(ColorFormat.format("&7&m---&r &cRequirements &7&m---"));
        if (recipe.requirements().isEmpty()) {
            rLore.add(ColorFormat.format(" &7None"));
        } else {
            for (Requirement req : recipe.requirements()) {
                rLore.add(ColorFormat.format(" &7\u2022 " + req.getDescription()));
            }
        }
        rMeta.setLore(rLore);
        result.setItemMeta(rMeta);
        inventory.setItem(RESULT_SLOT, result);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.setDisplayName(ColorFormat.format("&f\u2190 Back to Recipes"));
        back.setItemMeta(bMeta);
        setMenuData(back, "action", "back");
        inventory.setItem(BACK_SLOT, back);
    }

    private ItemStack makeIngredientItem(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            List<Material> materials = mc.getChoices();
            if (!materials.isEmpty()) {
                ItemStack item = new ItemStack(materials.get(0));
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ColorFormat.format("&f" + prettifyKey(materials.get(0).getKey().getKey())));
                item.setItemMeta(meta);
                return item;
            }
        }
        ItemStack fallback = new ItemStack(Material.BARRIER);
        ItemMeta meta = fallback.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&cUnknown"));
        fallback.setItemMeta(meta);
        return fallback;
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        String action = getStringData(clicked, "action");
        if ("back".equals(action)) {
            new RecipeBookMenu(viewer).open();
        }
        return true;
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
