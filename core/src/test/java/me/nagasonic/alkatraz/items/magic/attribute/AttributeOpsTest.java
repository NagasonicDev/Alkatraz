package me.nagasonic.alkatraz.items.magic.attribute;

import me.nagasonic.alkatraz.api.magic.attribute.AttributeContribution;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttributeOpsTest {

    private static NamespacedKey key(String path) {
        return new NamespacedKey("alkatraz", path);
    }

    @Test
    void castTimeMultiplierIsMultiply() {
        assertEquals(AttributeContribution.AttributeOperation.MULTIPLY,
                AttributeOps.operationFor(key("cast_time_multiplier")));
    }

    @Test
    void setSuffixIsSet() {
        assertEquals(AttributeContribution.AttributeOperation.SET,
                AttributeOps.operationFor(key("ember_set_bonus")));
    }

    @Test
    void multiplySuffixIsMultiply() {
        assertEquals(AttributeContribution.AttributeOperation.MULTIPLY,
                AttributeOps.operationFor(key("some_multiply_value")));
    }

    @Test
    void plainKeyIsAdd() {
        assertEquals(AttributeContribution.AttributeOperation.ADD,
                AttributeOps.operationFor(key("fire_affinity")));
    }
}
