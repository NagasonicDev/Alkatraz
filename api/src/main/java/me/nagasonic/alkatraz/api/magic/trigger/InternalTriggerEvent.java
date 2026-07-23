package me.nagasonic.alkatraz.api.magic.trigger;

import org.bukkit.NamespacedKey;

/**
 * Base class for all internal trigger events dispatched by the Alkatraz magic system.
 * <p>
 * Each concrete subclass represents a specific trigger condition (e.g. entity killed, spell cast)
 * and carries the {@link NamespacedKey} identifying the trigger type along with a
 * {@link TriggerContext} containing execution details.
 */
public class InternalTriggerEvent {

    private final NamespacedKey triggerType;
    private final TriggerContext context;

    /**
     * Constructs a new internal trigger event.
     *
     * @param triggerType the {@link NamespacedKey} identifying the trigger type
     * @param context     the {@link TriggerContext} carrying execution details
     */
    public InternalTriggerEvent(NamespacedKey triggerType, TriggerContext context) {
        this.triggerType = triggerType;
        this.context = context;
    }

    /**
     * Returns the {@link NamespacedKey} identifying the trigger type.
     *
     * @return the trigger type key
     */
    public NamespacedKey triggerType() {
        return triggerType;
    }

    /**
     * Returns the {@link TriggerContext} carrying execution details.
     *
     * @return the trigger context
     */
    public TriggerContext context() {
        return context;
    }
}
