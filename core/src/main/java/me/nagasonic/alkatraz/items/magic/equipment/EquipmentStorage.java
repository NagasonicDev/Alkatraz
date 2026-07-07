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
    private static final Map<UUID, Map<String, ItemStack>> cache = new ConcurrentHashMap<>();

    private EquipmentStorage() {}

    public static Optional<ItemStack> getItem(Player player, EquipmentSlot slot) {
        return getItem(player.getUniqueId(), slot);
    }

    public static Optional<ItemStack> getItem(UUID uuid, EquipmentSlot slot) {
        Map<String, ItemStack> playerItems = cache.get(uuid);
        if (playerItems == null) {
            playerItems = load(uuid);
            cache.put(uuid, playerItems);
        }
        return Optional.ofNullable(playerItems.get(slot.getKey().getKey()));
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
        if (changed) {
            Alkatraz.logDebug("Equipment change for " + player.getName() + " [" + slot.getKey().getKey() + "]");
        }
        save(player.getUniqueId(), playerItems);
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
            me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack magicItemStack = null;
            if (item != null && item.hasItemMeta() && me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.isMagicItem(item)) {
                java.util.Optional<me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance> optionalInstance = me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.readInstance(item);
                if (optionalInstance.isPresent()) {
                    me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance instance = optionalInstance.get();
                    java.util.Optional<me.nagasonic.alkatraz.api.magic.definition.ItemDefinition> optionalDefinition = me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.readDefinition(item);
                    if (optionalDefinition.isPresent()) {
                        me.nagasonic.alkatraz.api.magic.definition.ItemDefinition definition = optionalDefinition.get();
                        me.nagasonic.alkatraz.api.magic.trigger.TriggerContext ctx = new me.nagasonic.alkatraz.api.magic.trigger.TriggerContext(player, null, null, null, null, java.util.Map.of());
                        me.nagasonic.alkatraz.api.magic.trigger.TriggerContext scoped = ctx.withSource(instance, slot);
                        me.nagasonic.alkatraz.items.magic.MagicItemServices.get().dispatchTrigger(
                                new me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent(me.nagasonic.alkatraz.api.magic.registry.MagicKeys.alkatraz("on_unequip"), scoped));
                    }
                }
            }
        } catch (Exception e) {
            Alkatraz.logWarning("Error firing on_unequip event: " + e.getMessage());
        }
    }

    private static void fireOnEquipEvent(Player player, EquipmentSlot slot, ItemStack item) {
        try {
            me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack magicItemStack = null;
            if (item != null && item.hasItemMeta() && me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.isMagicItem(item)) {
                java.util.Optional<me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance> optionalInstance = me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.readInstance(item);
                if (optionalInstance.isPresent()) {
                    me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance instance = optionalInstance.get();
                    java.util.Optional<me.nagasonic.alkatraz.api.magic.definition.ItemDefinition> optionalDefinition = me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack.readDefinition(item);
                    if (optionalDefinition.isPresent()) {
                        me.nagasonic.alkatraz.api.magic.definition.ItemDefinition definition = optionalDefinition.get();
                        me.nagasonic.alkatraz.api.magic.trigger.TriggerContext ctx = new me.nagasonic.alkatraz.api.magic.trigger.TriggerContext(player, null, null, null, null, java.util.Map.of());
                        me.nagasonic.alkatraz.api.magic.trigger.TriggerContext scoped = ctx.withSource(instance, slot);
                        me.nagasonic.alkatraz.items.magic.MagicItemServices.get().dispatchTrigger(
                                new me.nagasonic.alkatraz.api.magic.trigger.event.EquipTriggerEvent(scoped));
                    }
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
        Map<String, ItemStack> playerItems = cache.get(player.getUniqueId());
        if (playerItems != null) {
            save(player.getUniqueId(), playerItems);
        }
    }

    public static void unload(Player player) {
        save(player);
        cache.remove(player.getUniqueId());
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
            if (raw instanceof ItemStack stack) {
                items.put(key, stack);
            } else if (raw instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                ItemStack deserialized = ItemStack.deserialize((Map<String, Object>) map);
                if (deserialized != null) {
                    items.put(key, deserialized);
                }
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
        try {
            config.save(file);
        } catch (IOException e) {
            Alkatraz.logWarning("Failed to save equipment for " + uuid + ": " + e.getMessage());
        }
    }

    private static File getFile(UUID uuid) {
        return new File(Alkatraz.getInstance().getDataFolder().getParentFile(),
                "Alkatraz/" + STORAGE_FOLDER + "/" + uuid + "/equipment.yml");
    }
}