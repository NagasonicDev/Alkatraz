package me.nagasonic.alkatraz.items.magic.attribute;

import me.nagasonic.alkatraz.api.magic.attribute.AttributeContribution;
import org.bukkit.NamespacedKey;

/**
 * Determines how a magic item attribute key is resolved when multiple
 * contributions target the same attribute.
 */
public final class AttributeOps {

    private AttributeOps() {}

    public static AttributeContribution.AttributeOperation operationFor(NamespacedKey key) {
        String path = key.getKey();
        if (path.equals("cast_time_multiplier")) {
            return AttributeContribution.AttributeOperation.MULTIPLY;
        }
        if (path.contains("_set_")) {
            return AttributeContribution.AttributeOperation.SET;
        }
        if (path.contains("_multiply_")) {
            return AttributeContribution.AttributeOperation.MULTIPLY;
        }
        return AttributeContribution.AttributeOperation.ADD;
    }
}
