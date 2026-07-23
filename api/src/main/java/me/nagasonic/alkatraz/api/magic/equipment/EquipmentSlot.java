package me.nagasonic.alkatraz.api.magic.equipment;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import org.bukkit.NamespacedKey;

/**
 * Represents a slot in which a magic item can be equipped.
 * Each slot is identified by a {@link NamespacedKey} and may optionally map to a vanilla
 * {@link org.bukkit.inventory.EquipmentSlot}. Custom (non-vanilla) slots such as
 * {@link #RING} or {@link #NECKLACE} are considered virtual slots.
 */
public final class EquipmentSlot implements Keyed {

    /** The player's main hand. */
    public static final EquipmentSlot MAIN_HAND = of("main_hand");
    /** The player's off hand. */
    public static final EquipmentSlot OFF_HAND = of("off_hand");
    /** The helmet/head slot. */
    public static final EquipmentSlot HEAD = of("head");
    /** The chestplate slot. */
    public static final EquipmentSlot CHEST = of("chest");
    /** The leggings slot. */
    public static final EquipmentSlot LEGS = of("legs");
    /** The boots slot. */
    public static final EquipmentSlot FEET = of("feet");
    /** The body (tunic) slot. */
    public static final EquipmentSlot BODY = of("body");
    /** A custom virtual ring slot. */
    public static final EquipmentSlot RING = of("ring");
    /** A custom virtual necklace slot. */
    public static final EquipmentSlot NECKLACE = of("necklace");
    /** A custom virtual bracelet slot. */
    public static final EquipmentSlot BRACELET = of("bracelet");
    /** A custom virtual pendant slot. */
    public static final EquipmentSlot PENDANT = of("pendant");

    private final NamespacedKey key;
    private final org.bukkit.inventory.EquipmentSlot vanillaSlot;

    private EquipmentSlot(NamespacedKey key, org.bukkit.inventory.EquipmentSlot vanillaSlot) {
        this.key = key;
        this.vanillaSlot = vanillaSlot;
    }

    /**
     * Creates a custom (virtual) equipment slot with the given path under the Alkatraz namespace.
     *
     * @param path the unique path identifier for this slot
     * @return a new virtual {@link EquipmentSlot}
     */
    public static EquipmentSlot of(String path) {
        return new EquipmentSlot(MagicKeys.alkatraz(path), null);
    }

    /**
     * Creates an equipment slot that wraps a vanilla {@link org.bukkit.inventory.EquipmentSlot}.
     *
     * @param slot the vanilla Bukkit equipment slot to wrap
     * @return a new {@link EquipmentSlot} backed by the given vanilla slot
     */
    public static EquipmentSlot vanilla(org.bukkit.inventory.EquipmentSlot slot) {
        NamespacedKey key = MagicKeys.alkatraz(slot.name().toLowerCase());
        return new EquipmentSlot(key, slot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NamespacedKey getKey() {
        return key;
    }

    /**
     * Returns the vanilla Bukkit equipment slot this slot wraps, or {@code null} for virtual slots.
     *
     * @return the vanilla slot, or {@code null}
     */
    public org.bukkit.inventory.EquipmentSlot vanillaSlot() {
        return vanillaSlot;
    }

    /**
     * Returns whether this slot is virtual (i.e., has no vanilla counterpart).
     *
     * @return {@code true} if this is a virtual slot
     */
    public boolean isVirtual() {
        return vanillaSlot == null;
    }
}
