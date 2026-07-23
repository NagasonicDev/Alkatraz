package me.nagasonic.alkatraz.api.magic.trigger.event;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

/**
 * Trigger event fired when a living entity is killed while using a magic item.
 * <p>
 * Provides access to both the killer (actor) and the victim (target) from the
 * underlying {@link TriggerContext}.
 */
public final class EntityKilledTriggerEvent extends InternalTriggerEvent {

    /**
     * Constructs a new entity-killed trigger event.
     *
     * @param context the {@link TriggerContext} containing the killer and victim
     */
    public EntityKilledTriggerEvent(TriggerContext context) {
        super(MagicKeys.alkatraz("on_kill"), context);
    }

    /**
     * Returns the entity that performed the kill.
     *
     * @return the killer entity
     */
    public LivingEntity killer() {
        return context().actor();
    }

    /**
     * Returns the entity that was killed.
     *
     * @return the victim entity
     */
    public LivingEntity victim() {
        return context().target();
    }
}
