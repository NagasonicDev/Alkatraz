package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
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
    private static final int UNLOCK_SLOT = 38;

    private final AlkatrazRecipe recipe;

    public RecipeDetailMenu(Player viewer, AlkatrazRecipe recipe) {
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
        for (String row : recipe.getShape()) {
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch != ' ') {
                    RecipeChoice choice = recipe.getIngredientMap().get(ch).toChoice();
                    inventory.setItem(GRID[idx], makeIngredientItem(choice));
                }
                idx++;
            }
            while (idx % 3 != 0) {
                idx++;
            }
        }

        ItemStack result = recipe.getResult().clone();
        ItemMeta rMeta = result.getItemMeta();
        List<String> rLore = new ArrayList<>();
        if (rMeta.hasLore()) rLore.addAll(rMeta.getLore());
        rLore.add("");
        boolean unlocked = isUnlocked(viewer);
        if (unlocked) {
            rLore.add(ColorFormat.format(lang().get("recipes.unlocked")));
        } else {
            rLore.add(ColorFormat.format(lang().get("recipes.requirements_header")));
            for (Requirement req : recipe.getRequirements()) {
                boolean met = req.isMet(viewer);
                String checkmark = met ? lang().get("progression.requirement_met") : lang().get("progression.requirement_unmet");
                rLore.add(ColorFormat.format((met ? "&a" : "&c") + "  " + checkmark + " &7" + req.getDescription(viewer)));
            }
            rLore.add("");
            rLore.add(ColorFormat.format(lang().get("recipes.progress_header")));
            for (Requirement req : recipe.getRequirements()) {
                rLore.add(ColorFormat.format(progressBar(req.getProgress(viewer), 10)));
            }
            rLore.add("");
            rLore.add(ColorFormat.format(lang().get("recipes.locked")));
        }
        rMeta.setLore(rLore);
        result.setItemMeta(rMeta);
        inventory.setItem(RESULT_SLOT, result);

        if (!unlocked && allRequirementsMet(viewer)) {
            ItemStack unlock = ItemBuilder.of(Material.GREEN_DYE)
                    .name(lang().get("recipes.unlock_button"))
                    .glint(true)
                    .build();
            setMenuData(unlock, "action", "unlock");
            inventory.setItem(UNLOCK_SLOT, unlock);
        }

        ItemStack back = ItemBuilder.of(Material.ARROW)
                .name(lang().get("recipes.back_to_recipes"))
                .build();
        setMenuData(back, "action", "back");
        inventory.setItem(BACK_SLOT, back);
    }

    private boolean isUnlocked(Player viewer) {
        return UnlockManager.isUnlocked(viewer, recipe.getKey().toString());
    }

    private boolean allRequirementsMet(Player viewer) {
        for (Requirement req : recipe.getRequirements()) {
            if (!req.isMet(viewer)) return false;
        }
        return true;
    }

    private static String progressBar(int percent, int segments) {
        int filled = Math.max(0, Math.min(segments, (int) Math.round(percent / 100.0 * segments)));
        StringBuilder sb = new StringBuilder("&7[&b");
        for (int i = 0; i < segments; i++) {
            sb.append(i < filled ? "\u2588" : "\u2591");
        }
        sb.append("&7] &f").append(percent).append('%');
        return sb.toString();
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
        if ("unlock".equals(action)) {
            UnlockManager.unlock(viewer, recipe.getKey().toString());
            refresh();
            return true;
        }
        if ("back".equals(action)) {
            new RecipeBookMenu(viewer).open();
        }
        return true;
    }
}
