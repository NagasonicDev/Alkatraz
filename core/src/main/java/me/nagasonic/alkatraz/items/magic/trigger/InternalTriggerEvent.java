package me.nagasonic.alkatraz.items.magic.trigger;

import org.bukkit.NamespacedKey;

/**
 * Internal trigger event dispatched by spells, quests, skills, or Bukkit adapters.
 * Not a Bukkit event — keeps the pipeline independent of the server event API.
 */
public final class InternalTriggerEvent {

    private final NamespacedKey triggerType;
    private final TriggerContext context;

    public InternalTriggerEvent(NamespacedKey triggerType, TriggerContext context) {
        this.triggerType = triggerType;
        this.context = context;
    }

    public NamespacedKey triggerType() {
        return triggerType;
    }

    public TriggerContext context() {
        return context;
    }
}
