package me.nagasonic.alkatraz.events;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player attempts to cast a spell but the cast is rejected before
 * any mana is spent or any effect happens.
 *
 * <p>This event is public so other plugins can listen for failed casts, e.g. to
 * notify players or run rewards. It is not cancellable. The {@code reason} is a
 * stable upper-case key identifying why the cast failed.</p>
 */
public class PlayerSpellFailEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Spell spell;
    private final ItemStack wand;
    private final String reason;

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public PlayerSpellFailEvent(Player player, Spell spell, ItemStack wand, String reason) {
        this.player = player;
        this.spell = spell;
        this.wand = wand;
        this.reason = reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the player who attempted the cast.
     *
     * @return the casting player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the spell that failed to cast.
     *
     * @return the spell
     */
    public Spell getSpell() {
        return spell;
    }

    /**
     * Returns the wand used for the cast, if any.
     *
     * @return the wand item
     */
    public ItemStack getWand() {
        return wand;
    }

    /**
     * Returns a stable upper-case key identifying why the cast failed. Values
     * include {@code LOW_CIRCLE}, {@code CANNOT_CAST}, {@code NOT_ENOUGH_MANA},
     * {@code COOLDOWN}, {@code DEAD} and {@code CANCELLED}.
     *
     * @return the failure reason key
     */
    public String getReason() {
        return reason;
    }
}
