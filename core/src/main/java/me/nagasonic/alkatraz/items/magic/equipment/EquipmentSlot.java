package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.items.magic.registry.Keyed;
import me.nagasonic.alkatraz.items.magic.registry.MagicKeys;
import org.bukkit.NamespacedKey;

/**
 * Abstract equipment slot independent of vanilla inventory layout.
 */
public final class EquipmentSlot implements Keyed {

    public static final EquipmentSlot MAIN_HAND = of("main_hand");
    public static final EquipmentSlot OFF_HAND = of("off_hand");
    public static final EquipmentSlot HEAD = of("head");
    public static final EquipmentSlot CHEST = of("chest");
    public static final EquipmentSlot LEGS = of("legs");
    public static final EquipmentSlot FEET = of("feet");
    public static final EquipmentSlot RING_1 = of("ring_1");
    public static final EquipmentSlot RING_2 = of("ring_2");
    public static final EquipmentSlot ARTIFACT = of("artifact");
    public static final EquipmentSlot ROBE = of("robe");

    private final NamespacedKey key;
    private final org.bukkit.inventory.EquipmentSlot vanillaSlot;

    private EquipmentSlot(NamespacedKey key, org.bukkit.inventory.EquipmentSlot vanillaSlot) {
        this.key = key;
        this.vanillaSlot = vanillaSlot;
    }

    public static EquipmentSlot of(String path) {
        return new EquipmentSlot(MagicKeys.alkatraz(path), null);
    }

    public static EquipmentSlot vanilla(org.bukkit.inventory.EquipmentSlot slot) {
        NamespacedKey key = MagicKeys.alkatraz(slot.name().toLowerCase());
        return new EquipmentSlot(key, slot);
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    public org.bukkit.inventory.EquipmentSlot vanillaSlot() {
        return vanillaSlot;
    }

    public boolean isVirtual() {
        return vanillaSlot == null;
    }
}
