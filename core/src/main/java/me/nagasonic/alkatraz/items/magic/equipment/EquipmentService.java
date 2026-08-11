package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentProfile;
import me.nagasonic.alkatraz.api.magic.equipment.VirtualSlotResolver;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves equipped magic items for a player across vanilla and virtual slots.
 */
public final class EquipmentService {

    private static final EquipmentSlot[] VANILLA_ORDER = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final Map<EquipmentSlot, VirtualSlotResolver> virtualResolvers = new LinkedHashMap<>();

    public void registerVirtualSlot(EquipmentSlot slot, VirtualSlotResolver resolver) {
        virtualResolvers.put(slot, resolver);
    }

    public EquipmentProfile profile(Player player) {
        Map<EquipmentSlot, ItemStack> items = new LinkedHashMap<>();
        Map<EquipmentSlot, MagicItemInstance> instances = new LinkedHashMap<>();

        PlayerInventory inventory = player.getInventory();
        putIfMagic(inventory.getItemInMainHand(), EquipmentSlot.MAIN_HAND, items, instances);
        putIfMagic(inventory.getItemInOffHand(), EquipmentSlot.OFF_HAND, items, instances);
        for (EquipmentSlot slot : VANILLA_ORDER) {
            putIfMagic(inventory.getItem(slot.vanillaSlot()), slot, items, instances);
        }
        for (Map.Entry<EquipmentSlot, VirtualSlotResolver> entry : virtualResolvers.entrySet()) {
            putIfMagic(entry.getValue().resolve(player), entry.getKey(), items, instances);
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
        items.put(slot, stack);
        try {
            MagicItemStack.readInstance(stack).ifPresent(instance ->
                instances.put(slot, instance)
            );
        } catch (Exception e) {
            Alkatraz.logWarning("Skipping corrupt magic item in slot " + slot.getKey().getKey() + ": " + e.getMessage());
        }
    }
}
