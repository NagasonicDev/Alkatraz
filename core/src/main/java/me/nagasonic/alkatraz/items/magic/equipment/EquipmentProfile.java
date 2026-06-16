package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.items.magic.instance.MagicItemInstance;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps abstract equipment slots to item stacks and parsed instances.
 */
public final class EquipmentProfile {

    private final Map<EquipmentSlot, ItemStack> items;
    private final Map<EquipmentSlot, MagicItemInstance> instances;

    public EquipmentProfile(
            Map<EquipmentSlot, ItemStack> items,
            Map<EquipmentSlot, MagicItemInstance> instances
    ) {
        this.items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
        this.instances = Collections.unmodifiableMap(new LinkedHashMap<>(instances));
    }

    public Optional<ItemStack> item(EquipmentSlot slot) {
        return Optional.ofNullable(items.get(slot));
    }

    public Optional<MagicItemInstance> instance(EquipmentSlot slot) {
        return Optional.ofNullable(instances.get(slot));
    }

    public Map<EquipmentSlot, ItemStack> items() {
        return items;
    }

    public Map<EquipmentSlot, MagicItemInstance> instances() {
        return instances;
    }
}
