package me.nagasonic.alkatraz.items.magic.recipe.adapter;

import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.StonecutterInventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CraftingEventRouter implements Listener {

    private static final Map<RecipeType, CraftingTypeAdapter> ADAPTERS = new EnumMap<>(RecipeType.class);
    private static final List<CustomCraftingAdapter> CUSTOM_ADAPTERS = new ArrayList<>();

    public CraftingEventRouter() {}

    public static void register(CraftingTypeAdapter adapter) {
        register(adapter, adapter.type());
    }

    public static void register(CraftingTypeAdapter adapter, RecipeType... types) {
        for (RecipeType type : types) {
            ADAPTERS.put(type, adapter);
        }
    }

    public static void register(CustomCraftingAdapter adapter) {
        CUSTOM_ADAPTERS.add(adapter);
    }

    public static void registerDefaultAdapters() {
        register(new ShapedCraftingAdapter());
        register(new ShapelessCraftingAdapter());
        CookingCraftingAdapter cooking = new CookingCraftingAdapter();
        register(cooking, RecipeType.FURNACE, RecipeType.BLAST_FURNACE, RecipeType.SMOKER, RecipeType.CAMPFIRE);
        register(new SmithingCraftingAdapter());
        register(new StonecutterCraftingAdapter());
        register(new BrewingCraftingAdapter());
        register(new AnvilCraftingAdapter());
    }

    public static void unregisterAll() {
        ADAPTERS.clear();
        CUSTOM_ADAPTERS.clear();
    }

    public static CraftingTypeAdapter getAdapter(RecipeType type) {
        return ADAPTERS.get(type);
    }

    public static void registerNative(AlkatrazRecipe recipe) {
        CraftingTypeAdapter adapter = ADAPTERS.get(recipe.getType());
        if (adapter != null) {
            adapter.registerNative(recipe);
        }
    }

    public static boolean onPrepare(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed)) return false;
        AlkatrazRecipe recipe = RecipeRegistry.get(keyed.getKey());
        if (recipe == null) return false;
        CraftingTypeAdapter adapter = ADAPTERS.get(recipe.getType());
        if (adapter == null) return false;
        adapter.onPrepare(event);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFurnaceStart(FurnaceStartSmeltEvent event) {
        AlkatrazRecipe recipe = RecipeRegistry.get(event.getRecipe().getKey());
        if (recipe == null) return;
        CraftingTypeAdapter adapter = ADAPTERS.get(recipe.getType());
        if (adapter == null) return;
        adapter.onFurnaceStart(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        CraftingTypeAdapter adapter = ADAPTERS.get(RecipeType.SMITHING);
        if (adapter != null) {
            adapter.onSmith(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStonecutterClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof StonecutterInventory)) return;
        CraftingTypeAdapter adapter = ADAPTERS.get(RecipeType.STONECUTTER);
        if (adapter != null) {
            adapter.onStonecutterClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBrew(BrewEvent event) {
        CraftingTypeAdapter adapter = ADAPTERS.get(RecipeType.BREWING);
        if (adapter != null) {
            adapter.onBrew(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        CraftingTypeAdapter adapter = ADAPTERS.get(RecipeType.ANVIL);
        if (adapter != null) {
            adapter.onAnvil(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCustomClick(InventoryClickEvent event) {
        if (CUSTOM_ADAPTERS.isEmpty()) return;
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        for (CustomCraftingAdapter adapter : CUSTOM_ADAPTERS) {
            adapter.handleClick(event, player);
        }
    }
}
