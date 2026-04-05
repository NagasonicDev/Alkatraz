package me.nagasonic.alkatraz.loot;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

public class MobLootInjector implements Listener {
    private static final List<MobLootInjector> REGISTERED_INJECTORS = new ArrayList<>();

    private final Predicate<Entity> filter;
    private final List<MobLootInjector.WeightedItem> items;
    private final int maxItems;
    private final boolean replace; // If true, replaces loot instead of adding to it

    public MobLootInjector(Predicate<Entity> filter, List<MobLootInjector.WeightedItem> items,
                        int maxItems, boolean replace) {
        this.filter = filter;
        this.items = items;
        this.maxItems = maxItems;
        this.replace = replace;
    }

    public static void register(Plugin plugin){
        plugin.getServer().getPluginManager().registerEvents(new MobLootInjector.LootListener(), plugin);
    }

    public static class LootListener implements Listener {
        /**
         * Main event handler for loot generation
         */
        @EventHandler(priority = EventPriority.HIGH)
        public static void onMobDeath(EntityDeathEvent event) {
            Entity entity = event.getEntity();
            // Check each registered injector
            for (MobLootInjector injector : REGISTERED_INJECTORS) {
                if (injector.filter.test(entity)) {
                    List<ItemStack> selectedItems = injector.selectItems();
                    event.getDrops().addAll(selectedItems);
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
        private Predicate<Entity> filter = tbl -> true; // Default: all loot tables
        private final List<MobLootInjector.WeightedItem> items = new ArrayList<>();
        private int maxItems = 1;
        private boolean replace = false;

        /**
         * Sets a filter for which loot tables to inject into
         */
        public MobLootInjector.Builder filter(Predicate<Entity> filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Filters by loot table key (e.g., "chests/desert_pyramid")
         */
        public MobLootInjector.Builder forEntity(EntityType... entities) {
            this.filter = ctx -> {
                EntityType type = ctx.getType();
                for (EntityType key : entities) {
                    if (type.equals(key)) {
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
        public MobLootInjector.Builder addItem(ItemStack item) {
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
        public MobLootInjector.Builder addItem(ItemStack item, double weight) {
            this.items.add(new MobLootInjector.WeightedItem(item, weight));
            return this;
        }

        /**
         * Adds multiple items with equal weight
         */
        public MobLootInjector.Builder addItems(double weight, ItemStack... items) {
            for (ItemStack item : items) {
                addItem(item, weight);
            }
            return this;
        }

        /**
         * Sets maximum number of items to inject per loot generation
         */
        public MobLootInjector.Builder maxItems(int max) {
            this.maxItems = max;
            return this;
        }

        /**
         * If true, replaces entire loot instead of adding to it
         */
        public MobLootInjector.Builder replace(boolean replace) {
            this.replace = replace;
            return this;
        }

        /**
         * Registers this loot injector
         */
        public MobLootInjector register() {
            if (items.isEmpty()) {
                throw new IllegalStateException("Must add at least one item!");
            }

            MobLootInjector injector = new MobLootInjector(filter, items, maxItems, replace);
            REGISTERED_INJECTORS.add(injector);
            return injector;
        }
    }

    /**
     * Creates a new builder
     */
    public static MobLootInjector.Builder builder() {
        return new MobLootInjector.Builder();
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
            for (MobLootInjector.WeightedItem wi : items) {
                totalWeight += wi.getWeight();
            }

            // Select random item based on weight
            double randomValue = random.nextDouble() * totalWeight;
            double currentWeight = 0;

            for (MobLootInjector.WeightedItem wi : items) {
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
    public static List<MobLootInjector> getRegisteredInjectors() {
        return new ArrayList<>(REGISTERED_INJECTORS);
    }
}
