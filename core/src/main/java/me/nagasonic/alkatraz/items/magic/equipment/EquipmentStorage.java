package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentStatService;
import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EquipmentStorage {

    private static final String STORAGE_FOLDER = "playerdata";
    private static final long FLUSH_DELAY_TICKS = 40L;
    private static final Map<UUID, Map<String, ItemStack>> cache = new ConcurrentHashMap<>();

    /**
     * Players whose in-memory equipment map has unsaved changes, mapped to the time the
     * pending flush was scheduled for. Serialising and writing the YAML file is a blocking
     * disk operation, so accessory changes only mark the player dirty and coalesce into a
     * single write. {@link #unload(Player)} still saves synchronously and is the crash-safety
     * net.
     */
    private static final Map<UUID, Long> DIRTY = new ConcurrentHashMap<>();

    private EquipmentStorage() {}

    public static Optional<ItemStack> getItem(Player player, EquipmentSlot slot) {
        return getItem(player.getUniqueId(), slot);
    }

    public static Optional<ItemStack> getItem(UUID uuid, EquipmentSlot slot) {
        Map<String, ItemStack> playerItems = loaded(uuid);
        if (playerItems == null) return Optional.empty();
        ItemStack found = playerItems.get(slot.getKey().getKey());
        return Optional.ofNullable(found == null ? null : found.clone());
    }

    /** Null-returning variant of {@link #getItem(Player, EquipmentSlot)} that avoids an Optional allocation. */
    public static ItemStack getItemOrNull(Player player, EquipmentSlot slot) {
        return getItemOrNull(player.getUniqueId(), slot);
    }

    public static ItemStack getItemOrNull(UUID uuid, EquipmentSlot slot) {
        Map<String, ItemStack> playerItems = loaded(uuid);
        if (playerItems == null) return null;
        ItemStack found = playerItems.get(slot.getKey().getKey());
        return found == null ? null : found.clone();
    }

    private static void markDirty(UUID uuid) {
        if (uuid == null) return;
        if (DIRTY.putIfAbsent(uuid, System.nanoTime()) != null) {
            return;
        }
        try {
            Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
                if (DIRTY.remove(uuid) == null) {
                    return;
                }
                Map<String, ItemStack> playerItems = cache.get(uuid);
                if (playerItems != null) {
                    save(uuid, playerItems);
                }
            }, FLUSH_DELAY_TICKS);
        } catch (RuntimeException e) {
            // Scheduler unavailable (shutdown or reload); fall back to an immediate write.
            Map<String, ItemStack> playerItems = cache.get(uuid);
            if (playerItems != null) {
                save(uuid, playerItems);
            }
        }
    }

    public static void setItem(Player player, EquipmentSlot slot, ItemStack item) {
        Map<String, ItemStack> playerItems = cache.computeIfAbsent(player.getUniqueId(), EquipmentStorage::load);
        ItemStack oldItem = playerItems.get(slot.getKey().getKey());
        boolean changed = false;
        if (item == null || item.getType().isAir()) {
            if (oldItem != null) changed = true;
            playerItems.remove(slot.getKey().getKey());
        } else {
            playerItems.put(slot.getKey().getKey(), item.clone());
            if (oldItem == null || !areItemsSimilar(oldItem, item)) changed = true;
        }
        if (item != null && !item.getType().isAir() && item.hasItemMeta()
                && me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.isMagicItem(item)) {
            EquipmentService.markMagicEquipped();
        }
        if (changed) {
            Alkatraz.logDebug("Equipment change for " + player.getName() + " [" + slot.getKey().getKey() + "]");
            markDirty(player.getUniqueId());
        }
        EquipmentService.invalidate(player.getUniqueId());
        EquipmentStatService service = EquipmentStatService.getInstance();
        service.syncEquipmentStats(player);
        if (oldItem != null && !areItemsSimilar(oldItem, item)) {
            fireOnUnequipEvent(player, slot, oldItem);
        }
        if (item != null && !item.getType().isAir() && (oldItem == null || !areItemsSimilar(oldItem, item))) {
            fireOnEquipEvent(player, slot, item);
        }
    }

    private static boolean areItemsSimilar(ItemStack a, ItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.isSimilar(b);
    }

    private static void fireOnUnequipEvent(Player player, EquipmentSlot slot, ItemStack item) {
        try {
            if (item == null || !item.hasItemMeta()) return;
            java.util.Optional<me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance> optionalInstance = me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.readInstance(item);
            if (optionalInstance.isPresent()) {
                me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance instance = optionalInstance.get();
                java.util.Optional<me.nagasonic.alkatraz.api.magic.definition.ItemDefinition> optionalDefinition = me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries.ITEM_DEFINITIONS.get(instance.definitionKey());
                if (optionalDefinition.isPresent()) {
                    me.nagasonic.alkatraz.api.magic.definition.ItemDefinition definition = optionalDefinition.get();
                    me.nagasonic.alkatraz.api.magic.trigger.TriggerContext ctx = new me.nagasonic.alkatraz.api.magic.trigger.TriggerContext(player, null, null, null, null, java.util.Map.of());
                    me.nagasonic.alkatraz.api.magic.trigger.TriggerContext scoped = ctx.withSource(instance, slot);
                    me.nagasonic.alkatraz.items.magic.MagicItemServices.get().dispatchTrigger(
                            new me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent(me.nagasonic.alkatraz.api.magic.registry.MagicKeys.alkatraz("on_unequip"), scoped));
                }
            }
        } catch (Exception e) {
            Alkatraz.logWarning("Error firing on_unequip event: " + e.getMessage());
        }
    }

    private static void fireOnEquipEvent(Player player, EquipmentSlot slot, ItemStack item) {
        try {
            if (item == null || !item.hasItemMeta()) return;
            java.util.Optional<me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance> optionalInstance = me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.readInstance(item);
            if (optionalInstance.isPresent()) {
                me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance instance = optionalInstance.get();
                java.util.Optional<me.nagasonic.alkatraz.api.magic.definition.ItemDefinition> optionalDefinition = me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries.ITEM_DEFINITIONS.get(instance.definitionKey());
                if (optionalDefinition.isPresent()) {
                    me.nagasonic.alkatraz.api.magic.definition.ItemDefinition definition = optionalDefinition.get();
                    me.nagasonic.alkatraz.api.magic.trigger.TriggerContext ctx = new me.nagasonic.alkatraz.api.magic.trigger.TriggerContext(player, null, null, null, null, java.util.Map.of());
                    me.nagasonic.alkatraz.api.magic.trigger.TriggerContext scoped = ctx.withSource(instance, slot);
                    me.nagasonic.alkatraz.items.magic.MagicItemServices.get().dispatchTrigger(
                            new me.nagasonic.alkatraz.api.magic.trigger.event.EquipTriggerEvent(scoped));
                }
            }
        } catch (Exception e) {
            Alkatraz.logWarning("Error firing on_equip event: " + e.getMessage());
        }
    }

    public static void removeItem(Player player, EquipmentSlot slot) {
        setItem(player, slot, null);
    }

    public static void save(Player player) {
        save(player.getUniqueId());
    }

    public static void save(UUID uuid) {
        Map<String, ItemStack> playerItems = cache.get(uuid);
        if (playerItems != null) {
            save(uuid, playerItems);
        }
    }

    /**
     * Returns the cached equipment map for an online player, loading it from disk only
     * when the player is currently online. Offline lookups never touch disk or cache.
     */
    private static Map<String, ItemStack> loaded(UUID uuid) {
        Map<String, ItemStack> existing = cache.get(uuid);
        if (existing != null) return existing;
        if (Bukkit.getPlayer(uuid) == null) return null;
        return cache.computeIfAbsent(uuid, EquipmentStorage::load);
    }

    /** Explicit load for offline/admin paths; mirrors ProfileManager semantics. */
    public static Map<String, ItemStack> getOrLoad(UUID uuid) {
        return cache.computeIfAbsent(uuid, EquipmentStorage::load);
    }
    public static void unload(Player player) {
        save(player);
        DIRTY.remove(player.getUniqueId());
        cache.remove(player.getUniqueId());
    }

    /** Drops a UUID's cached equipment without saving; for transient/offline loads. */
    public static void forget(UUID uuid) {
        DIRTY.remove(uuid);
        cache.remove(uuid);
    }

    private static Map<String, ItemStack> load(UUID uuid) {
        File file = getFile(uuid);
        if (!file.exists()) {
            return new HashMap<>();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<String, ItemStack> items = new HashMap<>();
        for (String key : config.getKeys(false)) {
            Object raw = config.get(key);
            try {
                if (raw instanceof ItemStack stack) {
                    items.put(key, stack.clone());
                } else if (raw instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    ItemStack deserialized = ItemStack.deserialize((Map<String, Object>) map);
                    if (deserialized != null) {
                        items.put(key, deserialized.clone());
                    }
                }
            } catch (Exception e) {
                Alkatraz.logWarning("Failed to load equipment entry '" + key + "' for " + uuid + ": " + e.getMessage());
            }
        }
        return items;
    }

    private static void save(UUID uuid, Map<String, ItemStack> items) {
        File file = getFile(uuid);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, ItemStack> entry : items.entrySet()) {
            if (entry.getValue() != null) {
                config.set(entry.getKey(), entry.getValue().serialize());
            }
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            config.save(tmp);
            try {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.io.IOException e) {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Alkatraz.logWarning("Failed to save equipment for " + uuid + ": " + e.getMessage());
            tmp.delete();
        }
    }

    private static File getFile(UUID uuid) {
        return new File(Alkatraz.getInstance().getDataFolder().getParentFile(),
                "Alkatraz/" + STORAGE_FOLDER + "/" + uuid + "/equipment.yml");
    }
}