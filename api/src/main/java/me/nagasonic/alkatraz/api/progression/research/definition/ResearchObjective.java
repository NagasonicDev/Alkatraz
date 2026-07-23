package me.nagasonic.alkatraz.api.progression.research.definition;

import java.util.Locale;
import java.util.Map;

/**
 * Defines a single objective within a {@link ResearchNode},
 * tracking a specific event a specified number of times with optional filters.
 */
public class ResearchObjective {

    private final String id;
    private final String displayName;
    private final String event;
    private final int amount;
    private final Map<String, Object> filters;

    /**
     * Constructs a new research objective.
     *
     * @param id the unique identifier for this objective
     * @param displayName the display name shown in the GUI
     * @param event the event name that triggers progress
     * @param amount the number of times the event must occur
     * @param filters additional criteria the event must match
     */
    public ResearchObjective(String id, String displayName, String event, int amount, Map<String, Object> filters) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        this.event = event.toLowerCase(Locale.ROOT);
        this.amount = Math.max(1, amount);
        this.filters = Map.copyOf(filters);
    }

    /**
     * Returns the unique identifier for this objective.
     *
     * @return the objective ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the display name shown in the GUI.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the event name that triggers progress for this objective.
     *
     * @return the event identifier
     */
    public String getEvent() {
        return event;
    }

    /**
     * Returns the number of times the event must occur to complete this objective.
     *
     * @return the required amount
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Returns the additional filters the event must match.
     *
     * @return an unmodifiable map of filter key-value pairs
     */
    public Map<String, Object> getFilters() {
        return filters;
    }
}
