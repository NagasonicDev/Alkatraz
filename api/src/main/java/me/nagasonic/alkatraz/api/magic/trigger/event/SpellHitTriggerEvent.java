package me.nagasonic.alkatraz.api.magic.trigger.event;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

/**
 * Trigger event fired when a spell projectile or effect hits a living entity.
 * <p>
 * Provides access to both the caster (actor) and the hit entity (target) from the
 * underlying {@link TriggerContext}.
 */
public final class SpellHitTriggerEvent extends InternalTriggerEvent {

    /**
     * Constructs a new spell-hit trigger event.
     *
     * @param context the {@link TriggerContext} containing the caster and hit entity
     */
    public SpellHitTriggerEvent(TriggerContext context) {
        super(MagicKeys.alkatraz("on_spell_hit"), context);
    }

    /**
     * Returns the entity that cast the spell.
     *
     * @return the caster entity
     */
    public LivingEntity caster() {
        return context().actor();
    }

    /**
     * Returns the entity that was hit by the spell.
     *
     * @return the hit entity
     */
    public LivingEntity hitTarget() {
        return context().target();
    }
}
