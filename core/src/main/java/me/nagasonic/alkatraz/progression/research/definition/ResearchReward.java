package me.nagasonic.alkatraz.progression.research.definition;

import java.util.Locale;

public class ResearchReward {

    private final String type;
    private final String target;
    private final double amount;
    private final String operation;
    private final String display;

    public ResearchReward(String type, String target, double amount, String operation, String display) {
        this.type = type.toLowerCase(Locale.ROOT);
        this.target = target;
        this.amount = amount;
        this.operation = operation == null ? "add" : operation.toLowerCase(Locale.ROOT);
        this.display = display;
    }

    public String getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public double getAmount() {
        return amount;
    }

    public String getOperation() {
        return operation;
    }

    public String getDisplay() {
        return display;
    }
}
