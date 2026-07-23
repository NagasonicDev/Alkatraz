package me.nagasonic.alkatraz.api.progression.arcane;

/**
 * Data-driven source of Arcane Knowledge rewards.
 */
public final class ArcaneKnowledgeSource {

    private final String id;
    private final double amount;
    private final boolean enabled;
    private final java.util.Map<Integer, Double> circleAmounts;

    /**
     * Creates a source with a flat amount for all Circle levels.
     *
     * @param id the unique identifier for this source
     * @param amount the arcane knowledge amount to reward
     * @param enabled whether this source is currently active
     */
    public ArcaneKnowledgeSource(String id, double amount, boolean enabled) {
        this(id, amount, enabled, java.util.Map.of());
    }

    /**
     * Creates a source with per-Circle amount overrides.
     *
     * @param id the unique identifier for this source
     * @param amount the default arcane knowledge amount
     * @param enabled whether this source is currently active
     * @param circleAmounts map of Circle level to override amount
     */
    public ArcaneKnowledgeSource(String id, double amount, boolean enabled, java.util.Map<Integer, Double> circleAmounts) {
        this.id = id;
        this.amount = amount;
        this.enabled = enabled;
        this.circleAmounts = java.util.Map.copyOf(circleAmounts);
    }

    /**
     * Returns the unique identifier for this source.
     *
     * @return the source ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the default arcane knowledge amount.
     *
     * @return the flat amount
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Returns the arcane knowledge amount for a specific Circle level,
     * falling back to the default if no override exists.
     *
     * @param circle the Circle level
     * @return the amount for that Circle
     */
    public double getAmount(int circle) {
        return circleAmounts.getOrDefault(circle, amount);
    }

    /**
     * Returns whether this source is currently enabled.
     *
     * @return {@code true} if active
     */
    public boolean isEnabled() {
        return enabled;
    }
}
