package me.nagasonic.alkatraz.items.magic.effect;

import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

import java.util.List;

/**
 * Executes ordered effects for a trigger binding.
 */
public final class EffectExecutor {

    private EffectExecutor() {}

    public static void executeAll(List<Effect> effects, TriggerContext context) {
        if (effects == null || effects.isEmpty() || context.isCancelled()) {
            return;
        }
        for (Effect effect : effects) {
            effect.execute(context);
            if (context.isCancelled()) {
                return;
            }
        }
    }

    public static LivingEntity resolveTarget(TriggerContext context) {
        return context.target() != null ? context.target() : context.actor();
    }
}
