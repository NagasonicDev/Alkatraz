package me.nagasonic.alkatraz.api.magic.effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;

/**
 * Represents a visual or gameplay effect that can be executed within the magic system.
 * Implementations define the behavior that occurs when a trigger activates this effect.
 */
public interface Effect {

    /**
     * Executes this effect using the given trigger context.
     *
     * @param context the context of the trigger that activated this effect
     */
    void execute(TriggerContext context);
}
