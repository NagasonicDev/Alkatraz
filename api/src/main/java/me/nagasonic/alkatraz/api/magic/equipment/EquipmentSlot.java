package me.nagasonic.alkatraz.api.magic.equipment;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import org.bukkit.NamespacedKey;

public final class EquipmentSlot implements Keyed {

    public static final EquipmentSlot MAIN_HAND = of("main_hand");
    public static final EquipmentSlot OFF_HAND = of("off_hand");
    public static final EquipmentSlot HEAD = of("head");
    public static final EquipmentSlot CHEST = of("chest");
    public static final EquipmentSlot LEGS = of("legs");
    public static final EquipmentSlot FEET = of("feet");
    public static final EquipmentSlot BODY = of("body");
    public static final EquipmentSlot RING = of("ring");
    public static final EquipmentSlot NECKLACE = of("necklace");
    public static final EquipmentSlot BRACELET = of("bracelet");
    public static final EquipmentSlot PENDANT = of("pendant");

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
