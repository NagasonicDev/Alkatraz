package me.nagasonic.alkatraz.api.events;

import me.nagasonic.alkatraz.api.spells.Spell;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Called when a {@link Player} casts a spell.
 * <p>
 * This is a type-safe specialization of {@link CastEvent} that guarantees the caster is a player.
 * Cancelling this event will prevent the spell from being cast.
 * </p>
 *
 * @see CastEvent
 */
public class PlayerCastEvent extends CastEvent {
    private final Player caster;

    /**
     * Constructs a new PlayerCastEvent.
     *
     * @param caster the player casting the spell
     * @param spell  the spell being cast
     * @param wand   the wand item used to cast the spell
     */
    public PlayerCastEvent(Player caster, Spell spell, ItemStack wand) {
        super(caster, spell, wand);
        this.caster = caster;
    }

    /**
     * Gets the player casting the spell.
     *
     * @return the caster as a {@link Player}
     */
    @Override
    public Player getCaster() { return caster; }
}
