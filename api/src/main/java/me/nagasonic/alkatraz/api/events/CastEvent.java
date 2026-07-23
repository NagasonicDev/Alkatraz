package me.nagasonic.alkatraz.api.events;

import me.nagasonic.alkatraz.api.spells.Spell;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Called when any spell is cast by a living entity.
 * <p>
 * This event fires after spell preparation and just before the spell's effects are applied.
 * Cancelling this event will prevent the spell from being cast.
 * </p>
 *
 * @see PlayerCastEvent
 * @see SpellPrepareEvent
 */
public class CastEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final LivingEntity caster;
    private final Spell spell;
    private final ItemStack wand;
    private boolean cancelled = false;

    /**
     * Gets the static {@link HandlerList} for this event.
     *
     * @return the handler list
     */
    public static HandlerList getHandlerList() { return HANDLERS; }

    /**
     * Constructs a new CastEvent.
     *
     * @param caster the entity casting the spell
     * @param spell  the spell being cast
     * @param wand   the wand item used to cast the spell
     */
    public CastEvent(LivingEntity caster, Spell spell, ItemStack wand) {
        this.caster = caster;
        this.spell = spell;
        this.wand = wand;
    }

    /**
     * Gets whether this event has been cancelled.
     *
     * @return {@code true} if the event is cancelled
     */
    @Override public boolean isCancelled() { return cancelled; }

    /**
     * Sets whether this event should be cancelled.
     * If cancelled, the spell will not be cast.
     *
     * @param cancel {@code true} to cancel the event
     */
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }

    /**
     * Gets the {@link HandlerList} for this event instance.
     *
     * @return the handler list
     */
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }

    /**
     * Gets the spell being cast.
     *
     * @return the spell
     */
    public Spell getSpell() { return spell; }

    /**
     * Gets the entity casting the spell.
     *
     * @return the caster
     */
    public LivingEntity getCaster() { return caster; }

    /**
     * Gets the wand item used to cast the spell.
     *
     * @return the wand item stack
     */
    public ItemStack getWand() { return wand; }
}
