package me.nagasonic.alkatraz.progression.arcane;

/**
 * Data-driven source of Arcane Knowledge rewards.
 */
public final class ArcaneKnowledgeSource {

    private final String id;
    private final double amount;
    private final boolean enabled;
    private final java.util.Map<Integer, Double> circleAmounts;

    public ArcaneKnowledgeSource(String id, double amount, boolean enabled) {
        this(id, amount, enabled, java.util.Map.of());
    }

    public ArcaneKnowledgeSource(String id, double amount, boolean enabled, java.util.Map<Integer, Double> circleAmounts) {
        this.id = id;
        this.amount = amount;
        this.enabled = enabled;
        this.circleAmounts = java.util.Map.copyOf(circleAmounts);
    }

    public String getId() {
        return id;
    }

    public double getAmount() {
        return amount;
    }

    public double getAmount(int circle) {
        return circleAmounts.getOrDefault(circle, amount);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
