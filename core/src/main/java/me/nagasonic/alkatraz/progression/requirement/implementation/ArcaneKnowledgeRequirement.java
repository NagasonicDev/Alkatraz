package me.nagasonic.alkatraz.progression.requirement.implementation;

import me.nagasonic.alkatraz.progression.requirement.ProgressionRequirement;
import me.nagasonic.alkatraz.progression.requirement.RequirementContext;

import java.util.Map;

public final class ArcaneKnowledgeRequirement implements ProgressionRequirement {

    private final double amount;

    public ArcaneKnowledgeRequirement(double amount) {
        this.amount = amount;
    }

    public double getAmount() {
        return amount;
    }

    public static ProgressionRequirement fromConfig(Map<String, Object> config) {
        return new ArcaneKnowledgeRequirement(readDouble(config, "amount", 0));
    }

    @Override
    public boolean isMet(RequirementContext context) {
        return context.getProfile().getArcaneKnowledge() >= amount;
    }

    @Override
    public String describe() {
        return "Arcane Knowledge " + amount;
    }

    private static double readDouble(Map<String, Object> config, String key, double fallback) {
        Object value = config.get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value != null) return Double.parseDouble(String.valueOf(value));
        return fallback;
    }
}
