package me.nagasonic.alkatraz.items.magic.effect.implementation;

import me.nagasonic.alkatraz.api.magic.effect.Effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

import java.util.Map;

public final class HealEffect implements Effect {
    private final double amount;

    public HealEffect(double amount) {
        this.amount = amount;
    }

    @Override
    public void execute(TriggerContext context) {
        LivingEntity target = context.actor();
        if (target != null) {
            target.setHealth(Math.min(target.getMaxHealth(), target.getHealth() + amount));
        }
    }

    public static Effect fromConfig(Map<String, Object> config) {
        double amount = Double.parseDouble(String.valueOf(config.getOrDefault("amount", 2.0)));
        return new HealEffect(amount);
    }
}
