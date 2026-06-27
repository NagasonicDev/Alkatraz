package me.nagasonic.alkatraz.progression.circle;

import me.nagasonic.alkatraz.progression.requirement.ProgressionRequirement;

import java.util.List;

public final class CircleDefinition {

    private final int circle;
    private final List<ProgressionRequirement> requirements;
    private final int statPoints;
    private final double maxMana;
    private final double manaRegeneration;

    public CircleDefinition(
            int circle,
            List<ProgressionRequirement> requirements,
            int statPoints,
            double maxMana,
            double manaRegeneration
    ) {
        this.circle = circle;
        this.requirements = List.copyOf(requirements);
        this.statPoints = statPoints;
        this.maxMana = maxMana;
        this.manaRegeneration = manaRegeneration;
    }

    public int getCircle() {
        return circle;
    }

    public List<ProgressionRequirement> getRequirements() {
        return requirements;
    }

    public int getStatPoints() {
        return statPoints;
    }

    public double getMaxMana() {
        return maxMana;
    }

    public double getManaRegeneration() {
        return manaRegeneration;
    }
}
