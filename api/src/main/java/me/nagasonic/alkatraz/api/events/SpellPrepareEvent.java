package me.nagasonic.alkatraz.api.events;

import me.nagasonic.alkatraz.api.spells.Spell;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Called when any living entity prepares to cast a spell.
 * <p>
 * This event fires before {@link CastEvent}, at the moment the caster begins preparing
 * a spell for casting. Cancelling this event will prevent the spell from being prepared.
 * </p>
 *
 * @see SpellPrepareEvent
 * @see PlayerSpellPrepareEvent
 */
public class SpellPrepareEvent extends Event implements Cancellable {
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
     * Constructs a new SpellPrepareEvent.
     *
     * @param caster the entity preparing the spell
     * @param spell  the spell being prepared
     * @param wand   the wand item being used
     */
    public SpellPrepareEvent(LivingEntity caster, Spell spell, ItemStack wand) {
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
     * If cancelled, the spell will not be prepared for casting.
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
     * Gets the entity preparing the spell.
     *
     * @return the caster
     */
    public LivingEntity getCaster() { return caster; }

    /**
     * Gets the spell being prepared.
     *
     * @return the spell
     */
    public Spell getSpell() { return spell; }

    /**
     * Gets the wand item being used.
     *
     * @return the wand item stack
     */
    public ItemStack getWand() { return wand; }
}
