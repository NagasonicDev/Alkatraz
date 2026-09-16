package me.nagasonic.alkatraz.api.ai.attribute;

import org.bukkit.attribute.Attribute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttributeProfileTest {

    @Test
    void varargsProfile_preservesInsertionOrder() {
        AttributeProfile p = AttributeProfile.of(
                new AttributeValue(Attribute.GENERIC_MAX_HEALTH, 40.0),
                new AttributeValue(Attribute.GENERIC_MOVEMENT_SPEED, 0.35));
        assertEquals(List.of(
                new AttributeValue(Attribute.GENERIC_MAX_HEALTH, 40.0),
                new AttributeValue(Attribute.GENERIC_MOVEMENT_SPEED, 0.35)),
                p.values());
    }

    @Test
    void listProfile_preservesOrderAndIsUnmodifiable() {
        List<AttributeValue> src = new java.util.ArrayList<>(List.of(
                new AttributeValue(Attribute.GENERIC_FOLLOW_RANGE, 32.0)));
        AttributeProfile p = AttributeProfile.of(src);
        src.add(new AttributeValue(Attribute.GENERIC_MAX_HEALTH, 10.0));
        assertEquals(1, p.values().size());
        assertThrows(UnsupportedOperationException.class, () -> p.values().add(
                new AttributeValue(Attribute.GENERIC_MAX_HEALTH, 10.0)));
    }

    @Test
    void nulls_areRejected() {
        assertThrows(NullPointerException.class, () -> AttributeProfile.of((AttributeValue[]) null));
        assertThrows(NullPointerException.class, () -> AttributeProfile.of((List<AttributeValue>) null));
        assertThrows(NullPointerException.class, () -> AttributeProfile.of(new AttributeValue[]{
                new AttributeValue(Attribute.GENERIC_MAX_HEALTH, 1.0), null}));
    }

    @Test
    void attributeValue_validates() {
        assertThrows(NullPointerException.class, () -> new AttributeValue(null, 1.0));
        assertEquals(2.0, new AttributeValue(Attribute.GENERIC_ATTACK_DAMAGE, 2.0).base());
        assertEquals(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeValue(Attribute.GENERIC_ATTACK_DAMAGE, 2.0).attribute());
    }
}