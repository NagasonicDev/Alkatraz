package me.nagasonic.alkatraz.items.magic.attribute;

import org.bukkit.NamespacedKey;

/**
 * A single contribution to an attribute value from a specific source layer.
 */
public record AttributeContribution(
        NamespacedKey attribute,
        double value,
        AttributeOperation operation,
        AttributeSourceType sourceType,
        int priority
) {
    public enum AttributeOperation {
        ADD,
        MULTIPLY,
        SET
    }

    public enum AttributeSourceType {
        BASE,
        DEFINITION,
        MODIFIER,
        EQUIPMENT,
        SKILL,
        BUFF,
        TEMPORARY
    }
}
