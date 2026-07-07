package me.nagasonic.alkatraz.items.magic.condition.implementation;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomCondition implements Condition {
    private final double chance;

    public RandomCondition(double chance) {
        this.chance = Math.min(1.0, Math.max(0.0, chance));
    }

    @Override
    public boolean test(TriggerContext context) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    public static Condition fromConfig(Map<String, Object> config) {
        double chance = Double.parseDouble(String.valueOf(config.getOrDefault("chance", 1.0)));
        return new RandomCondition(chance);
    }
}
