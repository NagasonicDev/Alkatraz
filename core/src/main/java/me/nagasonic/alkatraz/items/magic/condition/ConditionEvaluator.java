package me.nagasonic.alkatraz.items.magic.condition;

import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;

import java.util.List;

/**
 * Evaluates ordered condition lists (AND semantics by default).
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    public static boolean allMatch(List<Condition> conditions, TriggerContext context) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (Condition condition : conditions) {
            if (!condition.test(context)) {
                return false;
            }
        }
        return true;
    }
}
