package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
import me.nagasonic.alkatraz.texturepack.TexturePackManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StringUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class RecipeBookMenu extends PagedMenu<AlkatrazRecipe> {

    private static final int[] RECIPE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int FILTER_ALL_SLOT = 46;
    private static final int FILTER_LOCKED_SLOT = 48;
    private static final int FILTER_UNLOCKED_SLOT = 50;

    private Filter filter = Filter.ALL;

    private enum Filter {
        ALL, LOCKED, UNLOCKED
    }

    public RecipeBookMenu(Player viewer) {
        super(viewer, getResourceTitle(), 54, getFilteredRecipes(viewer, Filter.ALL), 28);
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

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private static boolean isUnlocked(Player viewer, AlkatrazRecipe recipe) {
        return UnlockManager.isUnlocked(viewer, recipe.getKey().toString());
    }

    private static List<AlkatrazRecipe> getFilteredRecipes(Player viewer, Filter filter) {
        return RecipeRegistry.getAll().stream()
                .filter(r -> matches(viewer, filter, r))
                .sorted(Comparator.comparing(r -> r.getKey().getKey()))
                .collect(Collectors.toList());
    }

    private static boolean matches(Player viewer, Filter filter, AlkatrazRecipe recipe) {
        boolean unlocked = isUnlocked(viewer, recipe);
        switch (filter) {
            case LOCKED:
                return !unlocked && !recipe.isHiddenWhenLocked();
            case UNLOCKED:
                return unlocked;
            case ALL:
            default:
                return unlocked || !recipe.isHiddenWhenLocked();
        }
    }

    private static String filterLangKey(Filter f) {
        switch (f) {
            case LOCKED: return "recipes.filter_locked";
            case UNLOCKED: return "recipes.filter_unlocked";
            case ALL:
            default: return "recipes.filter_all";
        }
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

        addFilterButton(Material.BOOK, Filter.ALL, FILTER_ALL_SLOT);
        addFilterButton(Material.YELLOW_DYE, Filter.LOCKED, FILTER_LOCKED_SLOT);
        addFilterButton(Material.LIME_DYE, Filter.UNLOCKED, FILTER_UNLOCKED_SLOT);
    }

    private void addFilterButton(Material material, Filter f, int slot) {
        boolean active = filter == f;
        ItemStack button = ItemBuilder.of(material)
                .rawName(ColorFormat.format((active ? "&e\u00bb " : "") + lang().get(filterLangKey(f))))
                .glint(active)
                .build();
        setMenuData(button, "action", "filter_" + f.name().toLowerCase());
        inventory.setItem(slot, button);
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
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        String action = getStringData(clicked, "action");
        if ("filter_all".equals(action)) { setFilter(Filter.ALL); return true; }
        if ("filter_locked".equals(action)) { setFilter(Filter.LOCKED); return true; }
        if ("filter_unlocked".equals(action)) { setFilter(Filter.UNLOCKED); return true; }
        return super.handleClick(event, clicked);
    }

    private void setFilter(Filter newFilter) {
        if (this.filter == newFilter) return;
        this.filter = newFilter;
        this.allItems = getFilteredRecipes(viewer, newFilter);
        this.totalPages = (int) Math.ceil((double) allItems.size() / itemsPerPage);
        this.currentPage = 1;
        refresh();
    }

    @Override
    protected ItemStack createDisplayItem(AlkatrazRecipe recipe, int index) {
        ItemStack item = recipe.getResult().clone();
        boolean unlocked = isUnlocked(viewer, recipe);

        List<String> lore = new ArrayList<>();

        lore.add(ColorFormat.format(lang().get("recipes.ingredients_header")));
        java.util.Set<Character> seen = new java.util.HashSet<>();
        for (String row : recipe.getShape()) {
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c != ' ' && seen.add(c)) {
                    RecipeChoice choice = recipe.getIngredientMap().get(c).toChoice();
                    lore.add(ColorFormat.format(" &7" + c + " &8= &f" + getIngredientName(choice)));
                }
            }
        }

        if (!unlocked) {
            lore.add("");
            lore.add(ColorFormat.format(lang().get("recipes.requirements_header")));
            for (Requirement req : recipe.getRequirements()) {
                lore.add(ColorFormat.format(" &7- " + req.getDescription(viewer)));
            }
            lore.add("");
            lore.add(ColorFormat.format(lang().get("recipes.progress_header")));
            for (Requirement req : recipe.getRequirements()) {
                lore.add(ColorFormat.format(progressBar(req.getProgress(viewer), 10)));
            }
            lore.add("");
            lore.add(ColorFormat.format(lang().get("recipes.locked")));
        } else {
            lore.add("");
            lore.add(ColorFormat.format(lang().get("recipes.unlocked")));
        }

        lore.add("");
        lore.add(ColorFormat.format(lang().get("recipes.click_details")));

        item = ItemBuilder.of(item)
                .rawName(ColorFormat.format("&f" + getItemDisplayName(recipe)))
                .rawLore(lore)
                .glint(!unlocked)
                .build();

        return item;
    }

    @Override
    protected void handleContentClick(AlkatrazRecipe recipe, InventoryClickEvent event) {
        new RecipeDetailMenu(viewer, recipe).open();
    }

    private String getItemDisplayName(AlkatrazRecipe recipe) {
        ItemStack result = recipe.getResult();
        if (result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
            return result.getItemMeta().getDisplayName();
        }
        return StringUtils.prettifyKey(recipe.getKey().getKey());
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
