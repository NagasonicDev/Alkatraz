package me.nagasonic.alkatraz.progression.research.definition;

import java.util.Locale;
import java.util.Map;

public class ResearchObjective {

    private final String id;
    private final String displayName;
    private final String event;
    private final int amount;
    private final Map<String, Object> filters;

    public ResearchObjective(String id, String displayName, String event, int amount, Map<String, Object> filters) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        this.event = event.toLowerCase(Locale.ROOT);
        this.amount = Math.max(1, amount);
        this.filters = Map.copyOf(filters);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEvent() {
        return event;
    }

    public int getAmount() {
        return amount;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }
}
