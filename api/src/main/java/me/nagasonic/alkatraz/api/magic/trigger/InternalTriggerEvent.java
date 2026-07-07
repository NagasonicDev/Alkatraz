package me.nagasonic.alkatraz.api.magic.trigger;

import org.bukkit.NamespacedKey;

public class InternalTriggerEvent {

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
