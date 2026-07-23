package me.nagasonic.alkatraz.api.spells;

import java.util.Objects;

/**
 * Represents a single selectable value within a {@link SpellOption}.
 * <p>
 * Each option value has a unique identifier, a display name for user-facing
 * text, and a typed value that can be of any type.
 *
 * @param <T> the type of the value held by this option value
 */
public class OptionValue<T> {
    private final String id;
    private final String displayName;
    private final T value;

    /**
     * Constructs a new option value.
     *
     * @param id          the unique identifier for this value
     * @param displayName the human-readable display name
     * @param value       the value itself, may be {@code null}
     * @throws NullPointerException if {@code id} or {@code displayName} is {@code null}
     */
    public OptionValue(String id, String displayName, T value) {
        this.id = Objects.requireNonNull(id, "Value id must not be null");
        this.displayName = Objects.requireNonNull(displayName, "Value displayName must not be null");
        this.value = value;
    }

    /**
     * Returns the unique identifier of this option value.
     *
     * @return the value id
     */
    public String getId() { return id; }

    /**
     * Returns the human-readable display name of this option value.
     *
     * @return the display name
     */
    public String getDisplayName() { return displayName; }

    /**
     * Returns the typed value held by this option value.
     *
     * @return the value, or {@code null} if none was set
     */
    public T getValue() { return value; }
}
