package me.nagasonic.alkatraz.api.magic.trigger.event;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.Player;

public final class EquipTriggerEvent extends InternalTriggerEvent {

    public EquipTriggerEvent(TriggerContext context) {
        super(MagicKeys.alkatraz("on_equip"), context);
    }

    public Player player() {
        return (Player) context().actor();
    }
}
