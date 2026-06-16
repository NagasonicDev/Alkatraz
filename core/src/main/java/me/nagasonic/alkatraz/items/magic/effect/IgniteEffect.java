package me.nagasonic.alkatraz.items.magic.effect;

import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

import java.util.Map;

final class IgniteEffect implements Effect {

    private final int durationTicks;

    IgniteEffect(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    @Override
    public void execute(TriggerContext context) {
        LivingEntity target = EffectExecutor.resolveTarget(context);
        if (target != null) {
            target.setFireTicks(durationTicks);
        }
    }

    static Effect fromConfig(Map<String, Object> config) {
        int duration = Integer.parseInt(String.valueOf(config.getOrDefault("duration_ticks", 60)));
        return new IgniteEffect(duration);
    }
}
