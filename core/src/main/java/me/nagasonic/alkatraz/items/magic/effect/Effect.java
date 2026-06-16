package me.nagasonic.alkatraz.items.magic.effect;

import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;

/**
 * Generic executable action resolved from configuration.
 */
public interface Effect {

    void execute(TriggerContext context);
}
