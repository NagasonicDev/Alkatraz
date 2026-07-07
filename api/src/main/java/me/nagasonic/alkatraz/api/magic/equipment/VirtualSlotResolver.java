package me.nagasonic.alkatraz.api.magic.equipment;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@FunctionalInterface
public interface VirtualSlotResolver {
    ItemStack resolve(Player player);
}
