package me.nagasonic.alkatraz.api.magic.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentSlotTest {

    @Test
    void armorSlotsAreBackedByVanillaSlots() {
        assertEquals(org.bukkit.inventory.EquipmentSlot.HEAD, EquipmentSlot.HEAD.vanillaSlot());
        assertEquals(org.bukkit.inventory.EquipmentSlot.CHEST, EquipmentSlot.CHEST.vanillaSlot());
        assertEquals(org.bukkit.inventory.EquipmentSlot.LEGS, EquipmentSlot.LEGS.vanillaSlot());
        assertEquals(org.bukkit.inventory.EquipmentSlot.FEET, EquipmentSlot.FEET.vanillaSlot());
    }

    @Test
    void armorSlotKeysMatchVanillaNames() {
        assertEquals("head", EquipmentSlot.HEAD.getKey().getKey());
        assertEquals("chest", EquipmentSlot.CHEST.getKey().getKey());
        assertEquals("legs", EquipmentSlot.LEGS.getKey().getKey());
        assertEquals("feet", EquipmentSlot.FEET.getKey().getKey());
    }

    @Test
    void virtualSlotsHaveNoVanillaBacking() {
        assertTrue(EquipmentSlot.RING.isVirtual());
        assertNull(EquipmentSlot.NECKLACE.vanillaSlot());
    }
}
