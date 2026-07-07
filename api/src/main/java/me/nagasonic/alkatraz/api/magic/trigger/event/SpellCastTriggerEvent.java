package me.nagasonic.alkatraz.api.magic.trigger.event;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

public final class SpellCastTriggerEvent extends InternalTriggerEvent {

    public SpellCastTriggerEvent(TriggerContext context) {
        super(MagicKeys.alkatraz("on_spell_cast"), context);
    }

    public LivingEntity caster() {
        return context().actor();
    }
}
