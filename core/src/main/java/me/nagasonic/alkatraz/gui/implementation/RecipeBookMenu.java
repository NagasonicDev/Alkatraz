package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.texturepack.TexturePackManager;
import me.nagasonic.alkatraz.items.magic.recipe.MagicItemRecipeManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StringUtils;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

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
        super(viewer, getResourceTitle(), 54, getSortedRecipes(), 28);
        this.contentSlots = RECIPE_SLOTS;
        this.nextPageSlot = 53;
        this.previousPageSlot = 45;
        this.backButtonSlot = 49;
    }

    private static String getResourceTitle() {
        String code = Alkatraz.getTexturePackManager().getMenuTitleCode("recipes");
        if (code == null || code.isEmpty() || !TexturePackManager.isResourcePackEnabled()) {
            return ColorFormat.format(Alkatraz.getLangManager().get("menu.recipe_book"));
        }
        return code;
    }

    private static List<MagicItemRecipeManager.RecipeData> getSortedRecipes() {
        return MagicItemRecipeManager.RECIPES.values().stream()
                .sorted(Comparator.comparing(r -> r.key().getKey()))
                .collect(Collectors.toList());
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    protected void addDecorations() {
        fillAll();

        List<String> loreLines = new ArrayList<>();
        for (String line : lang().get("recipes.browse_lore").split("\\n")) {
            loreLines.add(ColorFormat.format(line));
        }
        ItemStack info = ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name(lang().get("recipes.title"))
                .rawLore(loreLines)
                .build();
        inventory.setItem(4, info);
    }

    @Override
    protected void addBackButton() {
        ItemStack close = ItemBuilder.of(Material.BARRIER)
                .name("&cClose")
                .build();
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

        List<String> lore = new ArrayList<>();

        lore.add(ColorFormat.format(lang().get("recipes.ingredients_header")));
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
            lore.add(ColorFormat.format(lang().get("recipes.requirements_header")));
            for (me.nagasonic.alkatraz.configuration.requirement.Requirement req : recipe.requirements()) {
                lore.add(ColorFormat.format(" &7- " + req.getDescription()));
            }
        }

        lore.add("");
        lore.add(ColorFormat.format(lang().get("recipes.click_details")));

        item = ItemBuilder.of(item)
                .rawName(ColorFormat.format("&f" + getItemDisplayName(recipe)))
                .rawLore(lore)
                .build();

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
        return StringUtils.prettifyKey(recipe.key().getKey());
    }

    private String getIngredientName(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            List<Material> materials = mc.getChoices();
            if (!materials.isEmpty()) {
                return StringUtils.prettifyKey(materials.get(0).getKey().getKey());
            }
        } else if (choice instanceof RecipeChoice.ExactChoice ec) {
            List<ItemStack> stacks = ec.getChoices();
            if (!stacks.isEmpty()) {
                ItemStack stack = stacks.get(0);
                if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
                    return stack.getItemMeta().getDisplayName();
                }
                return StringUtils.prettifyKey(stack.getType().getKey().getKey());
            }
        }
        return "???";
    }
}
