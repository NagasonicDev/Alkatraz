package me.nagasonic.alkatraz.items.magic.condition;

import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;

/**
 * Evaluates whether a trigger binding should execute its effects.
 */
public interface Condition {

    boolean test(TriggerContext context);
}
