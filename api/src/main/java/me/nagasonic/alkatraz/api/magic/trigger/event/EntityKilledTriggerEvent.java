package me.nagasonic.alkatraz.api.magic.trigger.event;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

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
