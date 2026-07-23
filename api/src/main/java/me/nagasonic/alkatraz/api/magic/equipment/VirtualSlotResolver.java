package me.nagasonic.alkatraz.api.magic.equipment;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Resolves the {@link ItemStack} held in a virtual (non-vanilla) equipment slot for a player.
 * Virtual slots such as rings or necklaces have no native Minecraft inventory slot, so this
 * functional interface allows a provider to supply the item dynamically.
 *
 * @see EquipmentSlot#isVirtual()
 */
@FunctionalInterface
public interface VirtualSlotResolver {

    /**
     * Returns the item currently occupying the virtual slot for the given player.
     *
     * @param player the player whose virtual slot is being resolved
     * @return the {@link ItemStack} in the virtual slot, or {@code null} if empty
     */
    ItemStack resolve(Player player);
}
