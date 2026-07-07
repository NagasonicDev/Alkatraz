package me.nagasonic.alkatraz.api.spells;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SpellOption {
    private final String id;
    private final String displayName;
    private final List<OptionValue<?>> values;
    private final String defaultValueId;

    public SpellOption(String id, String displayName, List<OptionValue<?>> values, String defaultValueId) {
        this.id = Objects.requireNonNull(id, "Option id must not be null");
        this.displayName = Objects.requireNonNull(displayName, "Option displayName must not be null");
        this.values = values == null ? List.of() : List.copyOf(values);
        this.defaultValueId = defaultValueId;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public List<OptionValue<?>> getValues() { return values; }
    public String getDefaultValueId() { return defaultValueId; }

    public OptionValue<?> getDefaultValue() {
        if (defaultValueId == null && !values.isEmpty()) return values.get(0);
        return values.stream().filter(v -> v.getId().equals(defaultValueId)).findFirst().orElse(null);
    }
}
