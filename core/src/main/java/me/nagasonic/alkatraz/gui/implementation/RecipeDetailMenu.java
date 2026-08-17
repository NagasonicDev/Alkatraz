package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.implementation.recipe.RecipesPermissions;
import me.nagasonic.alkatraz.gui.implementation.recipe.RecipeCreateMenu;
import me.nagasonic.alkatraz.gui.implementation.recipe.RecipeDeleteConfirmMenu;
import me.nagasonic.alkatraz.gui.implementation.recipe.RecipeEditMenu;
import me.nagasonic.alkatraz.gui.implementation.recipe.RecipeListMenu;
import me.nagasonic.alkatraz.gui.implementation.editor.EditorSession;
import me.nagasonic.alkatraz.gui.implementation.editor.ItemDetailMenu;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeCategory;
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
    private static final int BACK_SLOT = 49;
    private static final int UNLOCK_SLOT = 47;
    private static final int EDIT_SLOT = 40;
    private static final int DUPLICATE_SLOT = 41;
    private static final int DELETE_SLOT = 42;

    private final AlkatrazRecipe recipe;
    private final RecipeCategory category;

    public RecipeDetailMenu(Player viewer, AlkatrazRecipe recipe) {
        super(viewer, ColorFormat.format(Alkatraz.getLangManager().get("menu.recipe_details")), 54);
        this.recipe = recipe;
        this.category = RecipeCategory.of(recipe.getType());
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    protected void build() {
        fillAll();
        if (!RecipesPermissions.canSee(viewer, recipe)) {
            ItemStack hidden = ItemBuilder.of(Material.BARRIER)
                    .name(lang().get("recipes.hidden"))
                    .lore(lang().get("recipes.hidden_lore"))
                    .build();
            setMenuData(hidden, "action", "back");
            inventory.setItem(RESULT_SLOT, hidden);
            addBackButton();
            return;
        }
        placeIngredients();
        placeResult();

        boolean unlocked = isUnlocked(viewer);
        if (!unlocked && allRequirementsMet(viewer)) {
            ItemStack unlock = ItemBuilder.of(Material.GREEN_DYE)
                    .name(lang().get("recipes.unlock_button"))
                    .glint(true)
                    .build();
            setMenuData(unlock, "action", "unlock");
            inventory.setItem(UNLOCK_SLOT, unlock);
        }

        if (RecipesPermissions.canEdit(viewer, category)) {
            ItemStack edit = ItemBuilder.of(Material.GREEN_DYE)
                    .name(lang().get("recipes.detail_edit"))
                    .lore(lang().get("recipes.detail_edit_lore"))
                    .build();
            setMenuData(edit, "action", "edit");
            inventory.setItem(EDIT_SLOT, edit);
        }
        if (RecipesPermissions.canCreate(viewer)) {
            ItemStack duplicate = Alkatraz.getGuiItemRegistry().getItem("duplicate_button").clone();
            ItemMeta dupMeta = duplicate.getItemMeta();
            if (dupMeta != null) {
                dupMeta.setLore(List.of(ColorFormat.format(lang().get("recipes.detail_duplicate_lore"))));
                duplicate.setItemMeta(dupMeta);
            }
            setMenuData(duplicate, "action", "duplicate");
            inventory.setItem(DUPLICATE_SLOT, duplicate);
        }
        if (RecipesPermissions.canDelete(viewer)) {
            ItemStack delete = ItemBuilder.of(Material.RED_CONCRETE)
                    .name(lang().get("recipes.detail_delete"))
                    .lore(lang().get("recipes.detail_delete_lore"))
                    .build();
            setMenuData(delete, "action", "delete");
            inventory.setItem(DELETE_SLOT, delete);
        }

        addBackButton();
    }

    private void addBackButton() {
        ItemStack back = Alkatraz.getGuiItemRegistry().getItem("back_button").clone();
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(lang().get("recipes.back_to_category",
                    "category", lang().get("recipes.categories." + category.getId())));
            back.setItemMeta(backMeta);
        }
        setMenuData(back, "action", "back");
        inventory.setItem(BACK_SLOT, back);
    }

    private void placeIngredients() {
        switch (category) {
            case CRAFTING -> {
                for (int slot : GRID) inventory.setItem(slot, null);
                if (recipe.getShape() == null) {
                    placeIngredientList(19, 20, 21);
                } else {
                    int idx = 0;
                    for (String row : recipe.getShape()) {
                        for (int c = 0; c < row.length(); c++) {
                            char ch = row.charAt(c);
                            if (ch != ' ' && recipe.getIngredientMap().get(ch) != null) {
                                inventory.setItem(GRID[idx], makeIngredientItem(recipe.getIngredientMap().get(ch).toChoice()));
                            }
                            idx++;
                        }
                        while (idx % 3 != 0) idx++;
                    }
                }
            }
            case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE -> {
                if (recipe.getInput() != null) {
                    inventory.setItem(20, displayStack(recipe.getInput(), "recipes.edit.input"));
                }
            }
            case BREWING -> {
                if (recipe.getInput() != null) inventory.setItem(19, displayStack(recipe.getInput(), "recipes.edit.input"));
                if (recipe.getAddition() != null) inventory.setItem(20, displayStack(recipe.getAddition(), "recipes.edit.addition"));
            }
            case SMITHING -> {
                if (recipe.getBase() != null) inventory.setItem(20, displayStack(recipe.getBase(), "recipes.edit.base"));
                if (recipe.getAddition() != null) inventory.setItem(21, displayStack(recipe.getAddition(), "recipes.edit.addition"));
            }
            case STONECUTTER -> {
                if (recipe.getInput() != null) inventory.setItem(20, displayStack(recipe.getInput(), "recipes.edit.input"));
            }
            default -> {}
        }
    }

    private void placeIngredientList(int... slots) {
        int i = 0;
        for (me.nagasonic.alkatraz.items.magic.recipe.Ingredient ingredient : recipe.getIngredients()) {
            if (i >= slots.length) break;
            inventory.setItem(slots[i], makeIngredientItem(ingredient.toChoice()));
            i++;
        }
    }

    private ItemStack displayStack(ItemStack stack, String nameKey) {
        ItemStack display = stack.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ColorFormat.format(lang().get(nameKey)));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private void placeResult() {
        ItemStack result = recipe.getResult().clone();
        ItemMeta rMeta = result.getItemMeta();
        List<String> rLore = new ArrayList<>();
        if (rMeta != null) {
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
        }
        setMenuData(result, "action", "edit_item");
        inventory.setItem(RESULT_SLOT, result);
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
        if (action == null) return true;
        switch (action) {
            case "unlock" -> {
                UnlockManager.unlock(viewer, recipe.getKey().toString());
                refresh();
            }
            case "edit" -> {
                if (!RecipesPermissions.canEdit(viewer, category)) {
                    viewer.sendMessage(lang().get("recipes.no_perm_edit"));
                    return true;
                }
                new RecipeEditMenu(viewer, recipe).open();
            }
            case "edit_item" -> {
                if (!RecipesPermissions.canEdit(viewer, category)) {
                    return true;
                }
                MagicItemStack.readDefinition(recipe.getResult()).ifPresentOrElse(definition -> {
                    String key = definition.getKey().getKey();
                    new EditorSession(viewer, key);
                    new ItemDetailMenu(viewer, definition, key, recipe).open();
                }, () -> viewer.sendMessage(lang().get("recipes.no_editor")));
            }
            case "duplicate" -> {
                if (!RecipesPermissions.canCreate(viewer)) {
                    viewer.sendMessage(lang().get("recipes.no_perm_create"));
                    return true;
                }
                new RecipeCreateMenu(viewer, recipe).open();
            }
            case "delete" -> {
                if (!RecipesPermissions.canDelete(viewer)) {
                    viewer.sendMessage(lang().get("recipes.no_perm_delete"));
                    return true;
                }
                new RecipeDeleteConfirmMenu(viewer, recipe).open();
            }
            case "back" -> new RecipeListMenu(viewer, category).open();
            default -> {}
        }
        return true;
    }
}
