package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.recipe.MagicItemRecipeManager.RecipeData;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StringUtils;
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
        super(viewer, ColorFormat.format(Alkatraz.getLangManager().get("menu.recipe_details")), 45);
        this.recipe = recipe;
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    protected void build() {
        fillAll();

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
        rLore.add(ColorFormat.format(lang().get("recipes.requirements_header")));
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

        ItemStack back = ItemBuilder.of(Material.ARROW)
                .name(lang().get("recipes.back_to_recipes"))
                .build();
        setMenuData(back, "action", "back");
        inventory.setItem(BACK_SLOT, back);
    }

    private ItemStack makeIngredientItem(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            List<Material> materials = mc.getChoices();
            if (!materials.isEmpty()) {
                return ItemBuilder.of(materials.get(0))
                        .name("&f" + StringUtils.prettifyKey(materials.get(0).getKey().getKey()))
                        .build();
            }
        } else if (choice instanceof RecipeChoice.ExactChoice ec) {
            List<ItemStack> stacks = ec.getChoices();
            if (!stacks.isEmpty()) {
                return stacks.get(0).clone();
            }
        }
        return ItemBuilder.of(Material.BARRIER)
                .name(lang().get("recipes.unknown"))
                .build();
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
}
