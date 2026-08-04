package me.nagasonic.alkatraz.items.magic.persistence;

import me.nagasonic.alkatraz.api.magic.instance.Engraving;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ItemInstanceSerializerTest {

    private static final NamespacedKey STONE = MagicKeys.alkatraz("magic_stone");

    @Test
    void serializeIsDeterministicForIdenticalInstances() {
        String first = ItemInstanceSerializer.serialize(MagicItemInstance.createDefault(STONE));
        String second = ItemInstanceSerializer.serialize(MagicItemInstance.createDefault(STONE));
        assertEquals(first, second,
                "identical default instances must serialize identically so items can stack");
    }

    @Test
    void serializeIsDeterministicForIdenticalModifiedInstances() {
        MagicItemInstance a = MagicItemInstance.createDefault(STONE);
        a.addModifier(MagicKeys.alkatraz("glowing"));
        a.addEngraving(new Engraving(MagicKeys.alkatraz("ember_engraving"), MagicKeys.alkatraz("on_hit")));
        a.putProgression("level", 3);
        a.putCustomData("charges", 7);

        MagicItemInstance b = MagicItemInstance.createDefault(STONE);
        b.addModifier(MagicKeys.alkatraz("glowing"));
        b.addEngraving(new Engraving(MagicKeys.alkatraz("ember_engraving"), MagicKeys.alkatraz("on_hit")));
        b.putProgression("level", 3);
        b.putCustomData("charges", 7);

        assertEquals(
                ItemInstanceSerializer.serialize(a),
                ItemInstanceSerializer.serialize(b),
                "instances with identical state must serialize identically");
    }

    @Test
    void serializedPayloadDoesNotContainInstanceId() {
        String payload = ItemInstanceSerializer.serialize(MagicItemInstance.createDefault(STONE));
        assertFalse(payload.contains("instance-id"),
                "the per-instance UUID must not be persisted, otherwise every item is unique");
    }

    @Test
    void deserializePayloadWithoutInstanceIdSucceeds() {
        MagicItemInstance source = MagicItemInstance.createDefault(STONE);
        source.putProgression("level", 2);
        String payload = ItemInstanceSerializer.serialize(source);
        assertFalse(payload.contains("instance-id"));

        MagicItemInstance roundTrip = ItemInstanceSerializer.deserialize(payload);
        assertNotNull(roundTrip);
        assertNotNull(roundTrip.instanceId());
        assertEquals(STONE, roundTrip.definitionKey());
        assertEquals(2, roundTrip.progression().get("level"));
    }

    @Test
    void deserializeRoundTripPreservesState() {
        MagicItemInstance source = MagicItemInstance.createDefault(STONE);
        source.addModifier(MagicKeys.alkatraz("glowing"));
        source.addEngraving(new Engraving(MagicKeys.alkatraz("ember_engraving"), MagicKeys.alkatraz("on_hit")));
        source.putCustomData("charges", 5);

        MagicItemInstance roundTrip = ItemInstanceSerializer.deserialize(
                ItemInstanceSerializer.serialize(source));

        assertEquals(List.of(MagicKeys.alkatraz("glowing")), roundTrip.modifiers());
        assertEquals(1, roundTrip.engravings().size());
        assertEquals(Map.of("charges", 5), roundTrip.customData());
    }
}
