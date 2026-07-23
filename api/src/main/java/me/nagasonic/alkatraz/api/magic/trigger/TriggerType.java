package me.nagasonic.alkatraz.api.magic.trigger;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.List;

/**
 * Defines a type of trigger that can initiate spell effects on magic items.
 * <p>
 * Each trigger type has a unique {@link NamespacedKey}, a human-readable description,
 * and an optional list of allowed item type identifiers. Trigger types are registered
 * with the magic registry and looked up by key when bindings reference them.
 */
public final class TriggerType implements Keyed {

    private final NamespacedKey key;
    private final String description;
    private final List<String> allowedItemTypes;

    /**
     * Constructs a trigger type with no item type restrictions.
     *
     * @param key         the unique {@link NamespacedKey} for this trigger type
     * @param description a human-readable description of when this trigger fires
     */
    public TriggerType(NamespacedKey key, String description) {
        this(key, description, List.of());
    }

    /**
     * Constructs a trigger type restricted to specific item types.
     *
     * @param key              the unique {@link NamespacedKey} for this trigger type
     * @param description      a human-readable description of when this trigger fires
     * @param allowedItemTypes the list of item type identifiers this trigger may apply to; may be {@code null}
     */
    public TriggerType(NamespacedKey key, String description, List<String> allowedItemTypes) {
        this.key = key;
        this.description = description;
        this.allowedItemTypes = allowedItemTypes == null ? List.of() : List.copyOf(allowedItemTypes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NamespacedKey getKey() {
        return key;
    }

    /**
     * Returns the human-readable description of this trigger type.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    /**
     * Returns the list of item type identifiers that this trigger type may apply to.
     * An empty list means the trigger has no item type restrictions.
     *
     * @return an unmodifiable list of allowed item type identifiers
     */
    public List<String> allowedItemTypes() {
        return allowedItemTypes;
    }
}
