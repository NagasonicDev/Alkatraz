package me.nagasonic.alkatraz.items.magic.trigger.event;

import me.nagasonic.alkatraz.items.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

/**
 * Fired when a spell hits a target.
 */
public final class SpellHitTriggerEvent extends InternalTriggerEvent {

    public SpellHitTriggerEvent(TriggerContext context) {
        super(MagicKeys.alkatraz("on_spell_hit"), context);
    }

    public LivingEntity caster() {
        return context().actor();
    }

    public LivingEntity hitTarget() {
        return context().target();
    }
}
