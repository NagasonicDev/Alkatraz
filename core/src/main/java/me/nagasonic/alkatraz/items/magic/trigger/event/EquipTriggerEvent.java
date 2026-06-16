package me.nagasonic.alkatraz.items.magic.trigger.event;

import me.nagasonic.alkatraz.items.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;
import org.bukkit.entity.Player;

/**
 * Fired when equipment is equipped in a slot.
 */
public final class EquipTriggerEvent extends InternalTriggerEvent {

    public EquipTriggerEvent(TriggerContext context) {
        super(MagicKeys.alkatraz("on_equip"), context);
    }

    public Player player() {
        return (Player) context().actor();
    }
}
