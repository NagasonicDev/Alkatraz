package me.nagasonic.alkatraz.api.magic.attribute;

import org.bukkit.NamespacedKey;

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
