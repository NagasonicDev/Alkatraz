package me.nagasonic.alkatraz.items.magic.attribute;

import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.definition.ItemVisual;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MagicDamageTest {

    private static final NamespacedKey WEAPON = MagicKeys.alkatraz("imbued_tier4_weapon");
    private static final NamespacedKey ARMOR = MagicKeys.alkatraz("imbued_tier4");

    @AfterEach
    void tearDown() {
        MagicItemRegistries.ITEM_DEFINITIONS.clear();
    }

    @Test
    void ofReturnsZeroForNullInstance() {
        assertEquals(0.0, MagicDamage.of(null));
    }

    @Test
    void ofReturnsZeroWhenDefinitionNotRegistered() {
        MagicItemInstance instance = MagicItemInstance.createDefault(WEAPON);
        assertEquals(0.0, MagicDamage.of(instance));
    }

    @Test
    void ofReturnsZeroWhenDefinitionHasNoMagicDamage() {
        MagicItemRegistries.ITEM_DEFINITIONS.register(definition(ARMOR, "max_mana", 50.0));
        MagicItemInstance instance = MagicItemInstance.createDefault(ARMOR);
        assertEquals(0.0, MagicDamage.of(instance));
    }

    @Test
    void ofReturnsRegisteredMagicDamageValue() {
        MagicItemRegistries.ITEM_DEFINITIONS.register(definition(WEAPON, "magic_damage", 8.0));
        MagicItemInstance instance = MagicItemInstance.createDefault(WEAPON);
        assertEquals(8.0, MagicDamage.of(instance));
    }

    private static ItemDefinition definition(NamespacedKey key, String attribute, double value) {
        return new ItemDefinition(
                key,
                ItemVisual.of(Material.DIAMOND_SWORD, "Test", List.of()),
                List.of(),
                Map.of(MagicKeys.alkatraz(attribute), value),
                Map.of(),
                List.of(),
                Map.of()
        );
    }
}
