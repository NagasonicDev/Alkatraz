package me.nagasonic.alkatraz.api.magic.condition;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;

/**
 * A predicate that determines whether a magic item's effect should activate
 * based on the current {@link TriggerContext}.
 * <p>
 * Implementations define the logic for a single condition check (e.g. "player must be sneaking",
 * "target must be on fire"). Conditions are evaluated by the runtime before an effect executes.
 */
public interface Condition {

    /**
     * Tests whether this condition is satisfied for the given trigger context.
     *
     * @param context the current trigger context providing execution details
     * @return {@code true} if the condition is met and the effect should proceed
     */
    boolean test(TriggerContext context);
}
