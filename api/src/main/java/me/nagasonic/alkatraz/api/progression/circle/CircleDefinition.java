package me.nagasonic.alkatraz.api.progression.circle;

import me.nagasonic.alkatraz.api.progression.requirement.ProgressionRequirement;

import java.util.List;

/**
 * Immutable definition for a single Circle level, holding the requirements
 * to advance and the stat bonuses granted upon reaching it.
 */
public final class CircleDefinition {

    private final int circle;
    private final List<ProgressionRequirement> requirements;
    private final int statPoints;
    private final double maxMana;
    private final double manaRegeneration;
    private final double magicAffinity;
    private final double magicResistance;

    /**
     * Constructs a new Circle definition.
     *
     * @param circle the Circle level (1-9)
     * @param requirements the requirements to advance to this Circle
     * @param statPoints the number of stat points granted upon reaching this Circle
     * @param maxMana the maximum mana granted
     * @param manaRegeneration the mana regeneration bonus granted
     * @param magicAffinity the magic affinity bonus granted
     * @param magicResistance the magic resistance bonus granted
     */
    public CircleDefinition(
            int circle,
            List<ProgressionRequirement> requirements,
            int statPoints,
            double maxMana,
            double manaRegeneration,
            double magicAffinity,
            double magicResistance
    ) {
        this.circle = circle;
        this.requirements = List.copyOf(requirements);
        this.statPoints = statPoints;
        this.maxMana = maxMana;
        this.manaRegeneration = manaRegeneration;
        this.magicAffinity = magicAffinity;
        this.magicResistance = magicResistance;
    }

    /**
     * Returns the Circle level.
     *
     * @return the Circle level (1-9)
     */
    public int getCircle() {
        return circle;
    }

    /**
     * Returns the requirements to advance to this Circle.
     *
     * @return an unmodifiable list of requirements
     */
    public List<ProgressionRequirement> getRequirements() {
        return requirements;
    }

    /**
     * Returns the number of stat points granted upon reaching this Circle.
     *
     * @return the stat point bonus
     */
    public int getStatPoints() {
        return statPoints;
    }

    /**
     * Returns the maximum mana granted by this Circle.
     *
     * @return the max mana bonus
     */
    public double getMaxMana() {
        return maxMana;
    }

    /**
     * Returns the mana regeneration bonus granted by this Circle.
     *
     * @return the mana regen bonus
     */
    public double getManaRegeneration() {
        return manaRegeneration;
    }

    /**
     * Returns the magic affinity bonus granted by this Circle.
     *
     * @return the magic affinity bonus
     */
    public double getMagicAffinity() {
        return magicAffinity;
    }

    /**
     * Returns the magic resistance bonus granted by this Circle.
     *
     * @return the magic resistance bonus
     */
    public double getMagicResistance() {
        return magicResistance;
    }
}
