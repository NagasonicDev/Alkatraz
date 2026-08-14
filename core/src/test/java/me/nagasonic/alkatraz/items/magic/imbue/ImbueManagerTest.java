package me.nagasonic.alkatraz.items.magic.imbue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImbueManagerTest {

    @Test
    void wrapNameWrapsCleanNameInSymbols() {
        assertEquals("§d§k𖢻 §r§dDiamond Sword §d§k𖢻", ImbueManager.wrapName("Diamond Sword"));
    }

    @Test
    void wrapNameIsIdempotentForAlreadyWrappedNames() {
        assertEquals("§d§k𖢻 §r§dMy Sword §d§k𖢻", ImbueManager.wrapName("§d§k𖢻 §r§dMy Sword §d§k𖢻"));
    }

    @Test
    void wrapNameStripsLegacyImbuedPrefix() {
        assertEquals("§d§k𖢻 §r§dDiamond Sword §d§k𖢻", ImbueManager.wrapName("§dImbued §rDiamond Sword"));
    }

    @Test
    void unwrapNameRemovesNewWrapper() {
        assertEquals("Diamond Sword", ImbueManager.unwrapName("§d§k𖢻 §r§dDiamond Sword §d§k𖢻"));
    }

    @Test
    void unwrapNameRemovesLegacyImbuedPrefix() {
        assertEquals("Diamond Sword", ImbueManager.unwrapName("§dImbued §rDiamond Sword"));
    }

    @Test
    void unwrapNameKeepsPlainNamesUntouched() {
        assertEquals("My Sword", ImbueManager.unwrapName("My Sword"));
    }

    @Test
    void unwrapNameKeepsNamesThatStartWithImbuedWord() {
        assertEquals("Imbued Blade", ImbueManager.unwrapName("§d§k𖢻 §r§dImbued Blade §d§k𖢻"));
    }

    @Test
    void unwrapNameHandlesNullOrEmpty() {
        assertEquals("", ImbueManager.unwrapName(null));
        assertEquals("", ImbueManager.unwrapName(""));
        assertEquals("", ImbueManager.unwrapName("𖢻"));
    }

    @Test
    void wrapNameHandlesNullOrEmpty() {
        assertEquals("", ImbueManager.wrapName(null));
        assertEquals("", ImbueManager.wrapName(""));
    }

    @Test
    void wrapAndUnwrapAreInverseForCleanNames() {
        assertEquals("Diamond Sword", ImbueManager.unwrapName(ImbueManager.wrapName("Diamond Sword")));
        assertEquals("My Sword", ImbueManager.unwrapName(ImbueManager.wrapName("My Sword")));
    }

    @Test
    void isWeaponRecognizesMeleeWeapons() {
        assertTrue(ImbueManager.isWeapon(Material.DIAMOND_SWORD));
        assertTrue(ImbueManager.isWeapon(Material.WOODEN_AXE));
        assertTrue(ImbueManager.isWeapon(Material.NETHERITE_AXE));
        assertTrue(ImbueManager.isWeapon(Material.TRIDENT));
    }

    @Test
    void isWeaponRejectsArmorToolsAndRanged() {
        assertFalse(ImbueManager.isWeapon(Material.DIAMOND_CHESTPLATE));
        assertFalse(ImbueManager.isWeapon(Material.IRON_HELMET));
        assertFalse(ImbueManager.isWeapon(Material.DIAMOND_PICKAXE));
        assertFalse(ImbueManager.isWeapon(Material.BOW));
        assertFalse(ImbueManager.isWeapon(Material.SHIELD));
    }

    @Test
    void getTierKeyReturnsWeaponVariantForWeapons() {
        ImbueManager.initialize();
        assertEquals("imbued_tier4_weapon", ImbueManager.getTierKey(Material.DIAMOND_SWORD).getKey());
        assertEquals("imbued_tier5_weapon", ImbueManager.getTierKey(Material.NETHERITE_AXE).getKey());
        assertEquals("imbued_tier1_weapon", ImbueManager.getTierKey(Material.WOODEN_SWORD).getKey());
        assertEquals("imbued_tier4_weapon", ImbueManager.getTierKey(Material.TRIDENT).getKey());
    }

    @Test
    void getTierKeyKeepsArmorVariantForArmor() {
        ImbueManager.initialize();
        assertEquals("imbued_tier4", ImbueManager.getTierKey(Material.DIAMOND_CHESTPLATE).getKey());
        assertEquals("imbued_tier1", ImbueManager.getTierKey(Material.LEATHER_BOOTS).getKey());
    }

    @Test
    void getTierKeyKeepsDefaultVariantForNonWeaponEquipment() {
        ImbueManager.initialize();
        assertEquals("imbued_tier3", ImbueManager.getTierKey(Material.BOW).getKey());
        assertEquals("imbued_tier1", ImbueManager.getTierKey(Material.WOODEN_PICKAXE).getKey());
        assertEquals("imbued_tier3", ImbueManager.getTierKey(Material.SHIELD).getKey());
    }
}
