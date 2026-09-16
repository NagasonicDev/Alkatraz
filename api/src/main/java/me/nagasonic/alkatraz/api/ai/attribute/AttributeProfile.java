package me.nagasonic.alkatraz.api.ai.attribute;

import java.util.Arrays;
import java.util.List;

/**
 * Insertion-ordered, immutable set of attribute overrides for a mob. The general
 * framework speaks {@link org.bukkit.attribute.Attribute} directly; YAML-key to
 * attribute name-mapping is a later data-loading concern.
 */
public final class AttributeProfile {

    private final List<AttributeValue> values;

    private AttributeProfile(List<AttributeValue> values) {
        this.values = values;
    }

    public static AttributeProfile of(AttributeValue... values) {
        if (values == null) throw new NullPointerException("values");
        return new AttributeProfile(List.copyOf(Arrays.asList(values)));
    }

    public static AttributeProfile of(List<AttributeValue> values) {
        if (values == null) throw new NullPointerException("values");
        return new AttributeProfile(List.copyOf(values));
    }

    /** The attribute overrides in insertion order. Unmodifiable. */
    public List<AttributeValue> values() {
        return values;
    }
}