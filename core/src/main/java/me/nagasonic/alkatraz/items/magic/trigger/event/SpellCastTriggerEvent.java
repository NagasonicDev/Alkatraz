package me.nagasonic.alkatraz.items.magic.trigger.event;

import me.nagasonic.alkatraz.items.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

/**
 * Fired when a player or entity casts a spell.
 */
public final class SpellCastTriggerEvent extends InternalTriggerEvent {

    public SpellCastTriggerEvent(TriggerContext context) {
        super(MagicKeys.alkatraz("on_spell_cast"), context);
    }

    public LivingEntity caster() {
        return context().actor();
    }
}
