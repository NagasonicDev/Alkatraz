package me.nagasonic.alkatraz.loot;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Predicate;

/**
 * System for injecting custom items into Minecraft's loot tables
 * 
 * Features:
 * - Works with ALL vanilla loot tables (chests, mobs, fishing, etc.)
 * - Filter by loot table key (e.g., "desert_pyramid", "zombie")
 * - Filter by location/world
 * - Weighted item pools
 * - Multiple items per injection
 * - Easy to extend for any item type
 * 
 * Usage:
 * LootInjector.builder()
 *     .filter(ctx -> ctx.getLootTable().getKey().toString().contains("desert_pyramid"))
 *     .addItem(myItem, 0.5) // 50% chance
 *     .register();
 */
public class LootInjector implements Listener {
    
    private static final List<LootInjector> REGISTERED_INJECTORS = new ArrayList<>();
    
    private final Predicate<LootTable> filter;
    private final List<WeightedItem> items;
    private final int maxItems;
    private final boolean replace; // If true, replaces loot instead of adding to it
    
    public LootInjector(Predicate<LootTable> filter, List<WeightedItem> items,
                        int maxItems, boolean replace) {
        this.filter = filter;
        this.items = items;
        this.maxItems = maxItems;
        this.replace = replace;
    }

    public static void register(Plugin plugin){
        plugin.getServer().getPluginManager().registerEvents(new LootListener(), plugin);
    }

    public static class LootListener implements Listener {
        /**
         * Main event handler for loot generation
         */
        @EventHandler(priority = EventPriority.HIGH)
        public static void onLootGenerate(LootGenerateEvent event) {
            LootTable table = event.getLootTable();

            // Check each registered injector
            for (LootInjector injector : REGISTERED_INJECTORS) {
                if (injector.filter.test(table)) {
                    // This injector applies to this loot table

                    if (injector.replace) {
                        // Replace entire loot
                        event.getLoot().clear();
                    }

                    // Add items from this injector
                    List<ItemStack> selectedItems = injector.selectItems();
                    event.getLoot().addAll(selectedItems);
                }
            }
        }
    }
    
    /**
     * Weighted item for loot generation
     */
    public static class WeightedItem {
        private final ItemStack item;
        private final double weight;
        
        public WeightedItem(ItemStack item, double weight) {
            this.item = item.clone();
            this.weight = weight;
        }
        
        public ItemStack getItem() {
            return item.clone();
        }
        
        public double getWeight() {
            return weight;
        }
    }
    
    /**
     * Builder for creating loot injectors
     */
    public static class Builder {
        private Predicate<LootTable> filter = tbl -> true; // Default: all loot tables
        private final List<WeightedItem> items = new ArrayList<>();
        private int maxItems = 1;
        private boolean replace = false;
        
        /**
         * Sets a filter for which loot tables to inject into
         */
        public Builder filter(Predicate<LootTable> filter) {
            this.filter = filter;
            return this;
        }
        
        /**
         * Filters by loot table key (e.g., "chests/desert_pyramid")
         */
        public Builder forLootTable(String... tableKeys) {
            this.filter = ctx -> {
                String tableKey = ctx.getKey().toString();
                for (String key : tableKeys) {
                    if (tableKey.contains(key)) {
                        return true;
                    }
                }
                return false;
            };
            return this;
        }
        
        /**
         * Adds an item with 100% weight (always selected if this injector triggers)
         */
        public Builder addItem(ItemStack item) {
            return addItem(item, 1.0);
        }
        
        /**
         * Adds an item with specified weight
         * Weight represents relative probability (not percentage)
         * 
         * Example:
         * - Item A: weight 100
         * - Item B: weight 50
         * Result: Item A has 66.7% chance, Item B has 33.3% chance
         */
        public Builder addItem(ItemStack item, double weight) {
            this.items.add(new WeightedItem(item, weight));
            return this;
        }
        
        /**
         * Adds multiple items with equal weight
         */
        public Builder addItems(double weight, ItemStack... items) {
            for (ItemStack item : items) {
                addItem(item, weight);
            }
            return this;
        }
        
        /**
         * Sets maximum number of items to inject per loot generation
         */
        public Builder maxItems(int max) {
            this.maxItems = max;
            return this;
        }
        
        /**
         * If true, replaces entire loot instead of adding to it
         */
        public Builder replace(boolean replace) {
            this.replace = replace;
            return this;
        }
        
        /**
         * Registers this loot injector
         */
        public LootInjector register() {
            if (items.isEmpty()) {
                throw new IllegalStateException("Must add at least one item!");
            }
            
            LootInjector injector = new LootInjector(filter, items, maxItems, replace);
            REGISTERED_INJECTORS.add(injector);
            return injector;
        }
    }
    
    /**
     * Creates a new builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Selects items based on weights
     */
    private List<ItemStack> selectItems() {
        List<ItemStack> selected = new ArrayList<>();
        Random random = new Random();
        
        for (int i = 0; i < maxItems; i++) {
            // Calculate total weight
            double totalWeight = 0;
            for (WeightedItem wi : items) {
                totalWeight += wi.getWeight();
            }
            
            // Select random item based on weight
            double randomValue = random.nextDouble() * totalWeight;
            double currentWeight = 0;
            
            for (WeightedItem wi : items) {
                currentWeight += wi.getWeight();
                if (randomValue <= currentWeight) {
                    selected.add(wi.getItem());
                    break;
                }
            }
        }
        
        return selected;
    }
    
    /**
     * Unregisters this injector
     */
    public void unregister() {
        REGISTERED_INJECTORS.remove(this);
    }
    
    /**
     * Unregisters all injectors
     */
    public static void unregisterAll() {
        REGISTERED_INJECTORS.clear();
    }
    
    /**
     * Gets all registered injectors
     */
    public static List<LootInjector> getRegisteredInjectors() {
        return new ArrayList<>(REGISTERED_INJECTORS);
    }
}
