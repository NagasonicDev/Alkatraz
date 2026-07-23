package me.nagasonic.alkatraz.api.magic.trigger.event;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.Player;

/**
 * Trigger event fired when a player equips a magic item.
 * <p>
 * The actor in the {@link TriggerContext} is guaranteed to be a {@link Player}.
 */
public final class EquipTriggerEvent extends InternalTriggerEvent {

    /**
     * Constructs a new equip trigger event.
     *
     * @param context the {@link TriggerContext} containing the equipping player
     */
    public EquipTriggerEvent(TriggerContext context) {
        super(MagicKeys.alkatraz("on_equip"), context);
    }

    /**
     * Returns the player who equipped the magic item.
     *
     * @return the equipping player
     */
    public Player player() {
        return (Player) context().actor();
    }
}
