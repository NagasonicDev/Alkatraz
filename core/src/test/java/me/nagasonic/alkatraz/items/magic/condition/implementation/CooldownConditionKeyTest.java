package me.nagasonic.alkatraz.items.magic.condition.implementation;

import me.nagasonic.alkatraz.api.magic.instance.Engraving;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.persistence.ItemInstanceSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CooldownConditionKeyTest {

    @Test
    void keyIsStableAcrossSeparateReadsOfTheSameItem() {
        MagicItemInstance source = new MagicItemInstance(
                null,
                MagicKeys.alkatraz("fire_sword"),
                List.of(),
                List.of(new Engraving(MagicKeys.alkatraz("fire_rune"), MagicKeys.alkatraz("on_damage_dealt"))),
                Map.of(),
                Map.of());
        String payload = ItemInstanceSerializer.serialize(source);

        MagicItemInstance readOne = ItemInstanceSerializer.deserialize(payload);
        MagicItemInstance readTwo = ItemInstanceSerializer.deserialize(payload);

        assertNotEquals(readOne.instanceId(), readTwo.instanceId(),
                "instance ids are regenerated on every read because they are not persisted");
        assertEquals(CooldownCondition.stableItemKey(readOne),
                CooldownCondition.stableItemKey(readTwo),
                "the cooldown key must be identical for two reads of the same item");
    }

    @Test
    void differentDefinitionsHaveDifferentKeys() {
        MagicItemInstance sword = new MagicItemInstance(
                null, MagicKeys.alkatraz("fire_sword"), List.of(), List.of(), Map.of(), Map.of());
        MagicItemInstance other = new MagicItemInstance(
                null, MagicKeys.alkatraz("frost_sword"), List.of(), List.of(), Map.of(), Map.of());

        assertNotEquals(CooldownCondition.stableItemKey(sword), CooldownCondition.stableItemKey(other));
    }

    @Test
    void differentEngravingsHaveDifferentKeys() {
        MagicItemInstance withFire = new MagicItemInstance(null, MagicKeys.alkatraz("sword"), List.of(),
                List.of(new Engraving(MagicKeys.alkatraz("fire_rune"), MagicKeys.alkatraz("on_damage_dealt"))),
                Map.of(), Map.of());
        MagicItemInstance withThunder = new MagicItemInstance(null, MagicKeys.alkatraz("sword"), List.of(),
                List.of(new Engraving(MagicKeys.alkatraz("thunder_rune"), MagicKeys.alkatraz("on_damage_dealt"))),
                Map.of(), Map.of());

        assertNotEquals(CooldownCondition.stableItemKey(withFire), CooldownCondition.stableItemKey(withThunder));
    }

    @Test
    void modifiersParticipateInTheKey() {
        MagicItemInstance glowing = new MagicItemInstance(
                null, MagicKeys.alkatraz("sword"), List.of(MagicKeys.alkatraz("glowing")), List.of(), Map.of(), Map.of());
        MagicItemInstance plain = new MagicItemInstance(
                null, MagicKeys.alkatraz("sword"), List.of(), List.of(), Map.of(), Map.of());

        assertNotEquals(CooldownCondition.stableItemKey(glowing), CooldownCondition.stableItemKey(plain));
    }
}
