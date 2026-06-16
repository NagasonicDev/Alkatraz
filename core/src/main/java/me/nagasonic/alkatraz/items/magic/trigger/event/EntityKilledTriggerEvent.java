package me.nagasonic.alkatraz.items.magic.trigger.event;

import me.nagasonic.alkatraz.items.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

/**
 * Fired when an entity kills another entity.
 */
public final class EntityKilledTriggerEvent extends InternalTriggerEvent {

    public EntityKilledTriggerEvent(TriggerContext context) {
        super(MagicKeys.alkatraz("on_kill"), context);
    }

    public LivingEntity killer() {
        return context().actor();
    }

    public LivingEntity victim() {
        return context().target();
    }
}
