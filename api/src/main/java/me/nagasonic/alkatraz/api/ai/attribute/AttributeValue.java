package me.nagasonic.alkatraz.api.ai.attribute;

import org.bukkit.attribute.Attribute;

/**
 * One attribute override: a Bukkit {@link Attribute} with its desired base value.
 */
public record AttributeValue(Attribute attribute, double base) {
    public AttributeValue {
        if (attribute == null) throw new NullPointerException("attribute");
    }
}