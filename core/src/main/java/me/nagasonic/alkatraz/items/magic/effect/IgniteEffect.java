package me.nagasonic.alkatraz.items.magic.effect;

import me.nagasonic.alkatraz.api.magic.effect.Effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

import java.util.Map;

public final class IgniteEffect implements Effect {

    private final int durationTicks;

    public IgniteEffect(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    @Override
    public void execute(TriggerContext context) {
        LivingEntity target = EffectExecutor.resolveTarget(context);
        if (target != null) {
            target.setFireTicks(durationTicks);
        }
    }

    public static Effect fromConfig(Map<String, Object> config) {
        int duration = Integer.parseInt(String.valueOf(config.getOrDefault("duration_ticks", 60)));
        return new IgniteEffect(duration);
    }
}
