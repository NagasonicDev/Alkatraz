package me.nagasonic.alkatraz.api.progression.research.definition;

import java.util.Locale;

/**
 * Defines a reward granted upon completing a {@link ResearchNode},
 * specifying the reward type, target, amount, and operation.
 */
public class ResearchReward {

    private final String type;
    private final String target;
    private final double amount;
    private final String operation;
    private final String display;

    /**
     * Constructs a new research reward.
     *
     * @param type the reward type identifier (e.g. "spell_unlock", "stat")
     * @param target the specific target of the reward (e.g. spell ID or stat name)
     * @param amount the reward amount
     * @param operation the math operation to apply (defaults to "add" if null)
     * @param display the display string shown in the GUI
     */
    public ResearchReward(String type, String target, double amount, String operation, String display) {
        this.type = type.toLowerCase(Locale.ROOT);
        this.target = target;
        this.amount = amount;
        this.operation = operation == null ? "add" : operation.toLowerCase(Locale.ROOT);
        this.display = display;
    }

    /**
     * Returns the reward type identifier.
     *
     * @return the type string
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the specific target of this reward.
     *
     * @return the target string
     */
    public String getTarget() {
        return target;
    }

    /**
     * Returns the reward amount.
     *
     * @return the amount value
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Returns the math operation applied to the reward.
     *
     * @return the operation string (e.g. "add", "set")
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Returns the display string shown in the GUI.
     *
     * @return the display text
     */
    public String getDisplay() {
        return display;
    }
}
