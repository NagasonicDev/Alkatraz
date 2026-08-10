package me.nagasonic.alkatraz.items.magic.condition.implementation;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Map;

/**
 * Blocks an activation while a per-item cooldown is still active.
 * <p>
 * Cooldown time only starts once the activation actually succeeds: the
 * condition records a "pending" cooldown on the {@link TriggerContext} and
 * {@link #commitPending(List, TriggerContext)} applies it after the effects
 * have executed, so a later failing condition never consumes the cooldown.
 */
public final class CooldownCondition implements Condition {

    private static final String PENDING_KEY_PARAM = "alkatraz:pending_cooldown_key";
    private static final String PENDING_END_PARAM = "alkatraz:pending_cooldown_end";

    private final double seconds;

    public CooldownCondition(double seconds) {
        this.seconds = Math.max(0.0, seconds);
    }

    @Override
    public boolean test(TriggerContext context) {
        LivingEntity actor = context.actor();
        if (actor == null) {
            return false;
        }
        String itemKey = itemKey(context);
        long now = System.currentTimeMillis();
        if (CooldownTracker.isOnCooldown(actor.getUniqueId(), itemKey, now)) {
            return false;
        }
        context.setParameter(PENDING_KEY_PARAM, itemKey);
        context.setParameter(PENDING_END_PARAM, now + (long) (seconds * 1000));
        return true;
    }

    /**
     * Applies any pending cooldowns recorded by cooldown conditions in the given
     * list. Intended to be called by the trigger pipeline after effects execute.
     */
    public static void commitPending(List<Condition> conditions, TriggerContext context) {
        if (conditions == null || context == null) {
            return;
        }
        for (Condition condition : conditions) {
            if (condition instanceof CooldownCondition cooldown) {
                cooldown.commit(context);
            }
        }
    }

    private void commit(TriggerContext context) {
        LivingEntity actor = context.actor();
        Object rawKey = context.parameter(PENDING_KEY_PARAM);
        Object rawEnd = context.parameter(PENDING_END_PARAM);
        if (actor == null || rawKey == null || rawEnd == null) {
            return;
        }
        CooldownTracker.markCooldown(
                actor.getUniqueId(), String.valueOf(rawKey), ((Number) rawEnd).longValue());
    }

    private static String itemKey(TriggerContext context) {
        return context.sourceItem() != null
                ? context.sourceItem().instanceId().toString()
                : "no-source";
    }

    public static Condition fromConfig(Map<String, Object> config) {
        double seconds = Double.parseDouble(String.valueOf(config.getOrDefault("seconds", 1.0)));
        return new CooldownCondition(seconds);
    }
}
