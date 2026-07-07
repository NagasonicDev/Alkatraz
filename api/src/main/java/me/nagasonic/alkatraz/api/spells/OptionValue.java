package me.nagasonic.alkatraz.api.spells;

import java.util.Objects;

public class OptionValue<T> {
    private final String id;
    private final String displayName;
    private final T value;

    public OptionValue(String id, String displayName, T value) {
        this.id = Objects.requireNonNull(id, "Value id must not be null");
        this.displayName = Objects.requireNonNull(displayName, "Value displayName must not be null");
        this.value = value;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public T getValue() { return value; }
}
