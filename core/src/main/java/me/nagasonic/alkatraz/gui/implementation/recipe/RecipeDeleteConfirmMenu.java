package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;

public class RecipeDeleteConfirmMenu extends Menu {

    private static final int PROMPT_SLOT = 13;
    private static final int CONFIRM_SLOT = 21;
    private static final int CANCEL_SLOT = 23;

    private final AlkatrazRecipe recipe;

    public RecipeDeleteConfirmMenu(Player viewer, AlkatrazRecipe recipe) {
        super(viewer, ColorFormat.format(Alkatraz.getLangManager().get("recipes.delete.title")), 45);
        this.recipe = recipe;
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    protected void build() {
        fillAll();

        ItemStack prompt = ItemBuilder.of(Material.PAPER)
                .name(lang().get("recipes.delete.title"))
                .lore(lang().get("recipes.delete.prompt", "key", recipe.getKey().toString()).split("\n"))
                .build();
        inventory.setItem(PROMPT_SLOT, prompt);

        ItemStack confirm = Alkatraz.getGuiItemRegistry().getItem("confirm_button").clone();
        ItemMeta confirmMeta = confirm.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(lang().get("recipes.delete.confirm"));
            confirm.setItemMeta(confirmMeta);
        }
        setMenuData(confirm, "action", "confirm");
        inventory.setItem(CONFIRM_SLOT, confirm);

        ItemStack cancel = Alkatraz.getGuiItemRegistry().getItem("cancel_button").clone();
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(lang().get("recipes.delete.cancel"));
            cancel.setItemMeta(cancelMeta);
        }
        setMenuData(cancel, "action", "cancel");
        inventory.setItem(CANCEL_SLOT, cancel);
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        String action = getStringData(clicked, "action");
        if ("confirm".equals(action)) {
            File file = RecipeRegistry.fileOf(recipe.getKey());
            if (file != null && !file.delete()) {
                viewer.sendMessage(ColorFormat.format("&cFailed to delete the recipe file."));
                return true;
            }
            RecipeEditorSession.remove(viewer.getUniqueId());
            RecipeRegistry.reload();
            UnlockManager.invalidateAll();
            viewer.sendMessage(lang().get("recipes.deleted", "key", recipe.getKey().toString()));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            new RecipeCategoryMenu(viewer).open();
            return true;
        }
        if ("cancel".equals(action)) {
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            new me.nagasonic.alkatraz.gui.implementation.RecipeDetailMenu(viewer, recipe).open();
            return true;
        }
        return true;
    }
}
