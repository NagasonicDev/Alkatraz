package me.nagasonic.alkatraz.api.magic.trigger.event;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

/**
 * Trigger event fired when a spell is cast from a magic item.
 * <p>
 * Provides access to the caster (actor) via the underlying {@link TriggerContext}.
 */
public final class SpellCastTriggerEvent extends InternalTriggerEvent {

    /**
     * Constructs a new spell-cast trigger event.
     *
     * @param context the {@link TriggerContext} containing the caster
     */
    public SpellCastTriggerEvent(TriggerContext context) {
        super(MagicKeys.alkatraz("on_spell_cast"), context);
    }

    /**
     * Returns the entity that cast the spell.
     *
     * @return the caster entity
     */
    public LivingEntity caster() {
        return context().actor();
    }
}
