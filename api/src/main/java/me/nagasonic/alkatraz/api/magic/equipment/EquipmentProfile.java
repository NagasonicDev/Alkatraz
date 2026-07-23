package me.nagasonic.alkatraz.api.magic.equipment;

import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A snapshot of a player's equipped items and their corresponding magic item instances.
 * Provides read-only access to both raw {@link ItemStack}s and their {@link MagicItemInstance}
 * representations, keyed by {@link EquipmentSlot}.
 */
public final class EquipmentProfile {

    private final Map<EquipmentSlot, ItemStack> items;
    private final Map<EquipmentSlot, MagicItemInstance> instances;

    /**
     * Constructs a new equipment profile.
     *
     * @param items     map of equipped items keyed by slot, must not be {@code null}
     * @param instances map of magic item instances keyed by slot, must not be {@code null}
     */
    public EquipmentProfile(
            Map<EquipmentSlot, ItemStack> items,
            Map<EquipmentSlot, MagicItemInstance> instances
    ) {
        this.items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
        this.instances = Collections.unmodifiableMap(new LinkedHashMap<>(instances));
    }

    /**
     * Returns the raw {@link ItemStack} in the given slot.
     *
     * @param slot the equipment slot to look up
     * @return an {@link Optional} containing the item, or empty if the slot is unoccupied
     */
    public Optional<ItemStack> item(EquipmentSlot slot) {
        return Optional.ofNullable(items.get(slot));
    }

    /**
     * Returns the {@link MagicItemInstance} in the given slot.
     *
     * @param slot the equipment slot to look up
     * @return an {@link Optional} containing the instance, or empty if the slot is unoccupied
     */
    public Optional<MagicItemInstance> instance(EquipmentSlot slot) {
        return Optional.ofNullable(instances.get(slot));
    }

    /**
     * Returns an unmodifiable map of all raw items keyed by slot.
     *
     * @return the items map
     */
    public Map<EquipmentSlot, ItemStack> items() {
        return items;
    }

    /**
     * Returns an unmodifiable map of all magic item instances keyed by slot.
     *
     * @return the instances map
     */
    public Map<EquipmentSlot, MagicItemInstance> instances() {
        return instances;
    }
}
