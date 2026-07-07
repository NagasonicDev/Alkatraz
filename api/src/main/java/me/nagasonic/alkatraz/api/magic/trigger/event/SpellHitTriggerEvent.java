package me.nagasonic.alkatraz.api.magic.trigger.event;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

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
