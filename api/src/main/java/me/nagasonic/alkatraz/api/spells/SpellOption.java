package me.nagasonic.alkatraz.api.spells;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Defines a configurable option for a {@link Spell}, consisting of a set of
 * selectable {@link OptionValue}s and a default selection.
 * <p>
 * Each spell may expose zero or more options (e.g. element selection, target
 * mode) that players can toggle in the spell GUI.
 */
public class SpellOption {
    private final String id;
    private final String displayName;
    private final List<OptionValue<?>> values;
    private final String defaultValueId;

    /**
     * Constructs a new spell option.
     *
     * @param id            the unique identifier for this option
     * @param displayName   the human-readable display name shown in the GUI
     * @param values        the available option values, or {@code null} for an empty list
     * @param defaultValueId the id of the value selected by default, or {@code null}
     * @throws NullPointerException if {@code id} or {@code displayName} is {@code null}
     */
    public SpellOption(String id, String displayName, List<OptionValue<?>> values, String defaultValueId) {
        this.id = Objects.requireNonNull(id, "Option id must not be null");
        this.displayName = Objects.requireNonNull(displayName, "Option displayName must not be null");
        this.values = values == null ? List.of() : List.copyOf(values);
        this.defaultValueId = defaultValueId;
    }

    /**
     * Returns the unique identifier of this option.
     *
     * @return the option id
     */
    public String getId() { return id; }

    /**
     * Returns the human-readable display name of this option.
     *
     * @return the display name
     */
    public String getDisplayName() { return displayName; }

    /**
     * Returns an unmodifiable list of all available values for this option.
     *
     * @return the option values
     */
    public List<OptionValue<?>> getValues() { return values; }

    /**
     * Returns the id of the default value for this option.
     *
     * @return the default value id, or {@code null} if none is set
     */
    public String getDefaultValueId() { return defaultValueId; }

    /**
     * Returns the default {@link OptionValue} for this option.
     * <p>
     * If no default id was specified and values are available, the first value
     * in the list is returned. If a default id was specified, the matching
     * value is looked up. Returns {@code null} if no match is found.
     *
     * @return the default option value, or {@code null} if not found
     */
    public OptionValue<?> getDefaultValue() {
        if (defaultValueId == null && !values.isEmpty()) return values.get(0);
        return values.stream().filter(v -> v.getId().equals(defaultValueId)).findFirst().orElse(null);
    }
}
