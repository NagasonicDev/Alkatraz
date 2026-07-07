package me.nagasonic.alkatraz.items.magic.condition.implementation;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;

import java.util.Map;

public final class ManaCondition implements Condition {
    private final Comparison comparison;
    private final double amount;

    public enum Comparison {
        GREATER_THAN,
        GREATER_OR_EQUAL,
        LESS_THAN,
        LESS_OR_EQUAL,
        EQUAL;

        boolean compare(double current, double expected) {
            return switch (this) {
                case GREATER_THAN -> current > expected;
                case GREATER_OR_EQUAL -> current >= expected;
                case LESS_THAN -> current < expected;
                case LESS_OR_EQUAL -> current <= expected;
                case EQUAL -> Double.compare(current, expected) == 0;
            };
        }
    }

    public ManaCondition(Comparison comparison, double amount) {
        this.comparison = comparison;
        this.amount = amount;
    }

    @Override
    public boolean test(TriggerContext context) {
        return context.playerActor().map(player -> {
            MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
            double mana = profile.getMana();
            return comparison.compare(mana, amount);
        }).orElse(false);
    }

    public static Condition fromConfig(Map<String, Object> config) {
        Comparison comparison;
        try {
            comparison = Comparison.valueOf(String.valueOf(config.getOrDefault("comparison", "GREATER_OR_EQUAL")));
        } catch (IllegalArgumentException e) {
            comparison = Comparison.GREATER_OR_EQUAL;
        }
        double amount = Double.parseDouble(String.valueOf(config.getOrDefault("amount", 10)));
        return new ManaCondition(comparison, amount);
    }
}
