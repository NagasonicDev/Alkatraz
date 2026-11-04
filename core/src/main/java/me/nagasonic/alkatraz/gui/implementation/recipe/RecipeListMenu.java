package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.gui.ItemBuilder;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.gui.implementation.RecipeDetailMenu;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeCategory;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StringUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RecipeListMenu extends PagedMenu<AlkatrazRecipe> {

    private final RecipeCategory category;
    private final String searchQuery;
    private SortMode sortMode = SortMode.KEY;
    private Set<String> unlockedKeys = Set.of();

    private static final int SORT_SLOT = 47;
    private static final int CLEAR_SLOT = 51;

    private enum SortMode {
        ALPHABETICAL("recipes.sort.alphabetical"),
        RESULT("recipes.sort.result"),
        KEY("recipes.sort.key"),
        RECENT("recipes.sort.recent");

        private final String langKey;

        SortMode(String langKey) {
            this.langKey = langKey;
        }

        SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public RecipeListMenu(Player viewer, RecipeCategory category) {
        super(viewer, packTitle("recipe_list", ColorFormat.format(
                Alkatraz.getLangManager().get("menu.recipe_categories"))), 54, new ArrayList<>(), 27);
        this.category = category;
        this.searchQuery = null;
        this.contentSlots = getDefaultContentSlots();
        this.allItems = collectRecipes();
        this.totalPages = (int) Math.ceil((double) allItems.size() / itemsPerPage);
    }

    public RecipeListMenu(Player viewer, List<AlkatrazRecipe> searchResults, String query) {
        super(viewer, packTitle("recipe_list", ColorFormat.format(
                Alkatraz.getLangManager().get("menu.recipe_categories"))), 54, searchResults, 27);
        this.category = null;
        this.searchQuery = query;
        this.contentSlots = getDefaultContentSlots();
    }

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private List<AlkatrazRecipe> collectRecipes() {
        List<AlkatrazRecipe> recipes = new ArrayList<>();
        for (AlkatrazRecipe recipe : RecipeRegistry.getAll()) {
            if (category.contains(recipe.getType()) && RecipesPermissions.canSee(viewer, recipe)) {
                recipes.add(recipe);
            }
        }
        return recipes;
    }

    private static final Map<UUID, UnlockSnapshot> UNLOCK_CACHE = new ConcurrentHashMap<>();
    private static final long UNLOCK_CACHE_TTL_MS = 1000L;

    private record UnlockSnapshot(Set<String> keys, long stamp) {}

    /**
     * Drops one player's cached unlock snapshot. The TTL only refreshes the values,
     * so the entry itself has to be evicted on quit or it is retained for the whole
     * process lifetime.
     */
    public static void clearUnlockCache(UUID uuid) {
        UNLOCK_CACHE.remove(uuid);
    }

    /** Drops every cached unlock snapshot (plugin disable). */
    public static void clearAllUnlockCaches() {
        UNLOCK_CACHE.clear();
    }

    private static Set<String> unlockedKeys(Player viewer, List<AlkatrazRecipe> recipes) {
        UUID uuid = viewer.getUniqueId();
        long now = System.currentTimeMillis();
        UnlockSnapshot cached = UNLOCK_CACHE.get(uuid);
        UnlockSnapshot snapshot;
        if (cached == null) {
            snapshot = new UnlockSnapshot(ConcurrentHashMap.newKeySet(), now);
            UNLOCK_CACHE.put(uuid, snapshot);
        } else if (now - cached.stamp() >= UNLOCK_CACHE_TTL_MS) {
            snapshot = new UnlockSnapshot(cached.keys(), now);
            UNLOCK_CACHE.put(uuid, snapshot);
        } else {
            snapshot = cached;
        }
        for (AlkatrazRecipe recipe : recipes) {
            if (recipe.getRequirements().isEmpty()) continue;
            String key = recipe.getKey().toString();
            if (snapshot.keys().contains(key)) continue;
            if (UnlockManager.isUnlocked(viewer, key)) {
                snapshot.keys().add(key);
            }
        }
        return snapshot.keys();
    }

    private List<AlkatrazRecipe> currentPageItems() {
        int start = Math.max(0, (currentPage - 1) * itemsPerPage);
        int end = Math.min(start + itemsPerPage, allItems.size());
        if (start >= end) return List.of();
        return allItems.subList(start, end);
    }

    @Override
    protected void addDecorations() {
        this.unlockedKeys = unlockedKeys(viewer, currentPageItems());
        fillAll();
        if (searchQuery == null) {
            ItemStack sort = Alkatraz.getGuiItemRegistry().getItem("sort_button").clone();
            ItemMeta sortMeta = sort.getItemMeta();
            sortMeta.setDisplayName(lang().get("recipes.sort_toggle", "sort", lang().get(sortMode.langKey)));
            sortMeta.setLore(List.of(ColorFormat.format(lang().get("recipes.sort_toggle_lore"))));
            sort.setItemMeta(sortMeta);
            setMenuData(sort, "action", "sort");
            inventory.setItem(SORT_SLOT, sort);
        } else {
            ItemStack clear = ItemBuilder.of(Material.BARRIER)
                    .name(lang().get("recipes.search.clear"))
                    .guiModel("cancel_button")
                    .build();
            setMenuData(clear, "action", "clear_search");
            inventory.setItem(CLEAR_SLOT, clear);
        }
    }

    @Override
    protected ItemStack createDisplayItem(AlkatrazRecipe recipe, int index) {
        ItemStack result = recipe.getResult().clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;
        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.getLore());
        lore.add("");
        lore.add(ColorFormat.format("&7" + recipe.getKey().getKey()));
        lore.add(ColorFormat.format("&7" + recipe.getType().name().toLowerCase()));
        if (recipe.getRequirements().isEmpty()) {
            lore.add(ColorFormat.format(lang().get("recipes.unlocked")));
        } else if (unlockedKeys.contains(recipe.getKey().toString())) {
            lore.add(ColorFormat.format(lang().get("recipes.unlocked")));
        } else {
            lore.add(ColorFormat.format(lang().get("recipes.locked")));
        }
        lore.add("");
        lore.add(ColorFormat.format(lang().get("recipes.click_details")));
        lore.add(ColorFormat.format(lang().get("recipes.click_edit")));
        lore.add(ColorFormat.format(lang().get("recipes.click_delete")));
        meta.setLore(lore);
        result.setItemMeta(meta);
        return result;
    }

    @Override
    protected void handleContentClick(AlkatrazRecipe recipe, InventoryClickEvent event) {
        if (event.isShiftClick()) {
            if (!RecipesPermissions.canDelete(viewer)) {
                viewer.sendMessage(lang().get("recipes.no_perm_delete"));
                return;
            }
            new RecipeDeleteConfirmMenu(viewer, recipe).open();
            return;
        }
        if (event.isRightClick()) {
            RecipeCategory cat = RecipeCategory.of(recipe.getType());
            if (!RecipesPermissions.canEdit(viewer, cat)) {
                viewer.sendMessage(lang().get("recipes.no_perm_edit"));
                return;
            }
            new RecipeEditMenu(viewer, recipe).open();
            return;
        }
        new RecipeDetailMenu(viewer, recipe).open();
    }

    @Override
    protected void addBackButton() {
        ItemStack back = Alkatraz.getGuiItemRegistry().getItem("back_button").clone();
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            String name = searchQuery != null
                    ? lang().get("recipes.back_to_categories")
                    : lang().get("recipes.back_to_category", "category", lang().get("recipes.categories." + category.getId()));
            backMeta.setDisplayName(name);
            back.setItemMeta(backMeta);
        }
        setMenuData(back, "action", "back");
        inventory.setItem(backButtonSlot, back);
    }

    @Override
    protected void handleBackClick() {
        new RecipeCategoryMenu(viewer).open();
    }

    @Override
    protected boolean handleClick(InventoryClickEvent event, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return true;
        String action = getStringData(clicked, "action");
        if ("sort".equals(action)) {
            sortMode = sortMode.next();
            applySort();
            refresh();
            return true;
        }
        if ("clear_search".equals(action)) {
            new RecipeCategoryMenu(viewer).open();
            return true;
        }
        return super.handleClick(event, clicked);
    }

    private void applySort() {
        switch (sortMode) {
            case ALPHABETICAL -> allItems.sort(Comparator.comparing(r ->
                    r.getDisplayName() != null ? r.getDisplayName() : r.getKey().getKey()));
            case RESULT -> allItems.sort(Comparator.comparing(r -> r.getResult().getType().name()));
            case KEY -> allItems.sort(Comparator.comparing(r -> r.getKey().getKey()));
            case RECENT -> allItems.sort(Comparator.comparingLong(
                    (AlkatrazRecipe r) -> RecipeRegistry.lastModified(r.getKey())).reversed());
        }
    }
}
