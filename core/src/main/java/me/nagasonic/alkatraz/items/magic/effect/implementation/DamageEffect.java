package me.nagasonic.alkatraz.items.magic.effect.implementation;

import me.nagasonic.alkatraz.api.magic.effect.Effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.items.magic.effect.EffectExecutor;
import org.bukkit.entity.LivingEntity;

import java.util.Map;

public final class DamageEffect implements Effect {
    private final double damage;
    private final boolean bypassArmor;

    public DamageEffect(double damage, boolean bypassArmor) {
        this.damage = damage;
        this.bypassArmor = bypassArmor;
    }

    @Override
    public void execute(TriggerContext context) {
        LivingEntity target = EffectExecutor.resolveTarget(context);
        if (target != null) {
            if (bypassArmor) {
                target.setHealth(Math.max(0, target.getHealth() - damage));
            } else {
                target.damage(damage);
            }
        }
    }

    public static Effect fromConfig(Map<String, Object> config) {
        double damage = Double.parseDouble(String.valueOf(config.getOrDefault("damage", 1.0)));
        boolean bypassArmor = Boolean.parseBoolean(String.valueOf(config.getOrDefault("bypass_armor", false)));
        return new DamageEffect(damage, bypassArmor);
    }
}
