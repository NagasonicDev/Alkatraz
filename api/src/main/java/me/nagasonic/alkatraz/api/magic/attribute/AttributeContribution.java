package me.nagasonic.alkatraz.api.magic.attribute;

import org.bukkit.NamespacedKey;

/**
 * Represents a single contribution to an entity's attribute value from a specific source.
 * Each contribution carries the attribute key, a numeric value, the operation to apply,
 * the type of source it originated from, and a priority that controls resolution order.
 *
 * @param attribute  the {@link NamespacedKey} identifying the attribute
 * @param value      the numeric value of this contribution
 * @param operation  how this contribution is combined with others
 * @param sourceType the category of source that produced this contribution
 * @param priority   lower values are applied first when resolving contributions of the same source type
 */
public record AttributeContribution(
        NamespacedKey attribute,
        double value,
        AttributeOperation operation,
        AttributeSourceType sourceType,
        int priority
) {
    /**
     * Defines how an {@link AttributeContribution} is combined with other contributions
     * for the same attribute.
     */
    public enum AttributeOperation {
        /** Adds the contribution's value to the running total. */
        ADD,
        /** Multiplies the running total by the contribution's value. */
        MULTIPLY,
        /** Sets the attribute value directly, overriding any prior value. */
        SET
    }

    /**
     * Categorises where an {@link AttributeContribution} originated, used to determine
     * the order in which contributions of different categories are resolved.
     */
    public enum AttributeSourceType {
        /** The inherent base value of the attribute. */
        BASE,
        /** Value defined by the magic item's definition. */
        DEFINITION,
        /** Value added by a modifier or enchantment. */
        MODIFIER,
        /** Value contributed by equipped items. */
        EQUIPMENT,
        /** Value contributed by an active skill. */
        SKILL,
        /** Value contributed by a buff effect. */
        BUFF,
        /** Temporary value, typically removed after a short duration. */
        TEMPORARY
    }
}
