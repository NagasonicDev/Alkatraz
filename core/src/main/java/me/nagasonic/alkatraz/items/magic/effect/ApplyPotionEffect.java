package me.nagasonic.alkatraz.items.magic.effect;

import me.nagasonic.alkatraz.api.magic.effect.Effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

public final class ApplyPotionEffect implements Effect {
    private final PotionEffectType effectType;
    private final int duration;
    private final int amplifier;

    public ApplyPotionEffect(PotionEffectType effectType, int duration, int amplifier) {
        this.effectType = effectType;
        this.duration = duration;
        this.amplifier = amplifier;
    }

    @Override
    public void execute(TriggerContext context) {
        LivingEntity target = EffectExecutor.resolveTarget(context);
        if (target != null) {
            target.addPotionEffect(new PotionEffect(effectType, duration, amplifier));
        }
    }

    public static Effect fromConfig(Map<String, Object> config) {
        PotionEffectType type = PotionEffectType.getByName(String.valueOf(config.getOrDefault("effect", "SPEED")));
        int duration = Integer.parseInt(String.valueOf(config.getOrDefault("duration_ticks", 100)));
        int amplifier = Integer.parseInt(String.valueOf(config.getOrDefault("amplifier", 0)));
        return new ApplyPotionEffect(type, duration, amplifier);
    }
}
