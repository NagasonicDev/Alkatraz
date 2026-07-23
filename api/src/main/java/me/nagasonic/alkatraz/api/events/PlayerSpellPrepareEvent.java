package me.nagasonic.alkatraz.api.events;

import me.nagasonic.alkatraz.api.spells.Spell;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Called when a {@link Player} prepares to cast a spell.
 * <p>
 * This is a type-safe specialization of {@link SpellPrepareEvent} that guarantees the caster is a player.
 * Cancelling this event will prevent the spell from being prepared for casting.
 * </p>
 *
 * @see SpellPrepareEvent
 */
public class PlayerSpellPrepareEvent extends SpellPrepareEvent {
    private final Player caster;

    /**
     * Constructs a new PlayerSpellPrepareEvent.
     *
     * @param caster the player preparing the spell
     * @param spell  the spell being prepared
     * @param wand   the wand item being used
     */
    public PlayerSpellPrepareEvent(Player caster, Spell spell, ItemStack wand) {
        super(caster, spell, wand);
        this.caster = caster;
    }

    /**
     * Gets the player preparing the spell.
     *
     * @return the caster as a {@link Player}
     */
    @Override
    public Player getCaster() { return caster; }
}
