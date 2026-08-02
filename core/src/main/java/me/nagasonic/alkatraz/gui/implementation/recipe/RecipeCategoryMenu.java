package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeCategory;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RecipeCategoryMenu extends Menu {

    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int SEARCH_SLOT = 40;
    private static final int CREATE_SLOT = 42;

    public RecipeCategoryMenu(Player viewer) {
        super(viewer, ColorFormat.format(Alkatraz.getLangManager().get("menu.recipe_categories")), 45);
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    protected void build() {
        fillAll();

        RecipeCategory[] categories = RecipeCategory.values();
        for (int i = 0; i < categories.length && i < CATEGORY_SLOTS.length; i++) {
            RecipeCategory category = categories[i];
            inventory.setItem(CATEGORY_SLOTS[i], createCategoryItem(category));
        }

        ItemStack search = Alkatraz.getGuiItemRegistry().getItem("search_button").clone();
        ItemMeta searchMeta = search.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setLore(colorLore(lang().get("recipes.search_lore").split("\n")));
            search.setItemMeta(searchMeta);
        }
        setMenuData(search, "action", "search");
        inventory.setItem(SEARCH_SLOT, search);

        if (RecipesPermissions.canCreate(viewer)) {
            ItemStack create = Alkatraz.getGuiItemRegistry().getItem("create_button").clone();
            ItemMeta createMeta = create.getItemMeta();
            if (createMeta != null) {
                createMeta.setLore(List.of(lang().get("recipes.category_lore", "count", "0")));
                create.setItemMeta(createMeta);
            }
            setMenuData(create, "action", "create");
            inventory.setItem(CREATE_SLOT, create);
        }
    }

    private ItemStack createCategoryItem(RecipeCategory category) {
        int count = 0;
        for (RecipeType type : category.getTypes()) {
            count += RecipeRegistry.getByStation(type).size();
        }
        boolean empty = count == 0;

        ItemStack item = ItemBuilder.of(category.getIcon())
                .name(lang().get("recipes.categories." + category.getId()))
                .lore(lang().get(empty ? "recipes.category_empty" : "recipes.category_lore", "count", String.valueOf(count)))
                .lore(lang().get("recipes.click_details"))
                .build();
        if (empty) {
            item.setAmount(1);
        }
        setMenuData(item, "action", "category_" + category.getId());
        return item;
    }

    private static List<String> colorLore(String[] lines) {
        List<String> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(ColorFormat.format(line));
        }
        return lore;
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        String action = getStringData(clicked, "action");
        if (action == null) return true;
        if (action.equals("search")) {
            RecipeChatHandler.prompt(viewer, lang().get("recipes.search.prompt"), (p, query) -> {
                if (query.equalsIgnoreCase("cancel")) {
                    p.sendMessage(lang().get("recipes.edit.chat_cancelled"));
                    return;
                }
                new RecipeListMenu(p, searchRecipes(query), query).open();
            });
            return true;
        }
        if (action.equals("create")) {
            if (!RecipesPermissions.canCreate(viewer)) {
                viewer.sendMessage(lang().get("recipes.no_perm_create"));
                return true;
            }
            new RecipeCreateMenu(viewer).open();
            return true;
        }
        if (action.startsWith("category_")) {
            RecipeCategory category = RecipeCategory.byId(action.substring("category_".length()));
            if (category != null) {
                new RecipeListMenu(viewer, category).open();
            }
            return true;
        }
        return true;
    }

    private static List<me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe> searchRecipes(String query) {
        String q = query.toLowerCase();
        List<me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe> results = new ArrayList<>();
        for (me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe recipe : RecipeRegistry.getAll()) {
            String id = recipe.getKey().getKey().toLowerCase();
            String display = recipe.getDisplayName() != null ? recipe.getDisplayName().toLowerCase() : "";
            String resultName = recipe.getResult().hasItemMeta() && recipe.getResult().getItemMeta().hasDisplayName()
                    ? recipe.getResult().getItemMeta().getDisplayName().toLowerCase()
                    : recipe.getResult().getType().name().toLowerCase();
            if (id.contains(q) || display.contains(q) || resultName.contains(q)) {
                results.add(recipe);
            }
        }
        return results;
    }
}
