package me.nagasonic.alkatraz.items.magic.effect.implementation;

import me.nagasonic.alkatraz.api.magic.effect.Effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.items.magic.attribute.MagicDamage;
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
            double total = damage + MagicDamage.of(context.sourceItem());
            if (bypassArmor) {
                target.setHealth(Math.max(0, target.getHealth() - total));
            } else {
                target.damage(total);
            }
        }
    }

    public static Effect fromConfig(Map<String, Object> config) {
        double damage = Double.parseDouble(String.valueOf(config.getOrDefault("damage", 1.0)));
        boolean bypassArmor = Boolean.parseBoolean(String.valueOf(config.getOrDefault("bypass_armor", false)));
        return new DamageEffect(damage, bypassArmor);
    }
}
