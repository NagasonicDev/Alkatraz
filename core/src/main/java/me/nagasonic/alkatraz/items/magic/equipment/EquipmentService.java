package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.items.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves equipped magic items for a player across vanilla and virtual slots.
 */
public final class EquipmentService {

    private final Map<EquipmentSlot, VirtualSlotResolver> virtualResolvers = new HashMap<>();

    @FunctionalInterface
    public interface VirtualSlotResolver {
        ItemStack resolve(Player player);
    }

    public void registerVirtualSlot(EquipmentSlot slot, VirtualSlotResolver resolver) {
        virtualResolvers.put(slot, resolver);
    }

    public EquipmentProfile profile(Player player) {
        Map<EquipmentSlot, ItemStack> items = new HashMap<>();
        Map<EquipmentSlot, MagicItemInstance> instances = new HashMap<>();

        PlayerInventory inventory = player.getInventory();
        putIfMagic(inventory.getItemInMainHand(), EquipmentSlot.MAIN_HAND, items, instances);
        putIfMagic(inventory.getItemInOffHand(), EquipmentSlot.OFF_HAND, items, instances);

        for (org.bukkit.inventory.EquipmentSlot vanilla : org.bukkit.inventory.EquipmentSlot.values()) {
            ItemStack armor = inventory.getItem(vanilla);
            EquipmentSlot slot = mapVanilla(vanilla);
            if (slot != null) {
                putIfMagic(armor, slot, items, instances);
            }
        }

        for (Map.Entry<EquipmentSlot, VirtualSlotResolver> entry : virtualResolvers.entrySet()) {
            ItemStack stack = entry.getValue().resolve(player);
            putIfMagic(stack, entry.getKey(), items, instances);
        }

        return new EquipmentProfile(items, instances);
    }

    private static void putIfMagic(
            ItemStack stack,
            EquipmentSlot slot,
            Map<EquipmentSlot, ItemStack> items,
            Map<EquipmentSlot, MagicItemInstance> instances
    ) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        MagicItemStack.readInstance(stack).ifPresent(instance -> {
            items.put(slot, stack);
            instances.put(slot, instance);
        });
    }

    private static EquipmentSlot mapVanilla(org.bukkit.inventory.EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
            case HAND -> EquipmentSlot.MAIN_HAND;
            case OFF_HAND -> EquipmentSlot.OFF_HAND;
            default -> null;
        };
    }
}
