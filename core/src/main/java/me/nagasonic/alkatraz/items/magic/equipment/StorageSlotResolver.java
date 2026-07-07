package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.api.magic.equipment.VirtualSlotResolver;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class StorageSlotResolver implements VirtualSlotResolver {

    private final EquipmentSlot slot;

    public StorageSlotResolver(EquipmentSlot slot) {
        this.slot = slot;
    }

    @Override
    public ItemStack resolve(Player player) {
        return EquipmentStorage.getItem(player, slot).orElse(null);
    }
}
