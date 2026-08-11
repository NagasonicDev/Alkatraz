package me.nagasonic.alkatraz.items.magic.effect.implementation;

import me.nagasonic.alkatraz.api.magic.effect.Effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.items.magic.effect.EffectExecutor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

public final class ApplyPotionEffect implements Effect {
    private final PotionEffectType effectType;
    private final int duration;
    private final int amplifier;
    private final boolean selfTarget;

    public ApplyPotionEffect(PotionEffectType effectType, int duration, int amplifier) {
        this(effectType, duration, amplifier, false);
    }

    public ApplyPotionEffect(PotionEffectType effectType, int duration, int amplifier, boolean selfTarget) {
        this.effectType = effectType;
        this.duration = duration;
        this.amplifier = amplifier;
        this.selfTarget = selfTarget;
    }

    @Override
    public void execute(TriggerContext context) {
        LivingEntity target = selfTarget ? context.actor() : EffectExecutor.resolveTarget(context);
        if (target != null) {
            target.addPotionEffect(new PotionEffect(effectType, duration, amplifier));
        }
    }

    public static Effect fromConfig(Map<String, Object> config) {
        PotionEffectType type = PotionEffectType.getByName(String.valueOf(config.getOrDefault("effect", "SPEED")));
        int duration = Integer.parseInt(String.valueOf(config.getOrDefault("duration_ticks", 100)));
        int amplifier = Integer.parseInt(String.valueOf(config.getOrDefault("amplifier", 0)));
        boolean selfTarget = "self".equalsIgnoreCase(String.valueOf(config.get("target")));
        return new ApplyPotionEffect(type, duration, amplifier, selfTarget);
    }
}
