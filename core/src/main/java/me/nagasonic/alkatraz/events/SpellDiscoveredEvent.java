package me.nagasonic.alkatraz.events;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player discovers (learns) a spell for the first time.
 *
 * <p>This event is public so other plugins can listen for spell discoveries,
 * e.g. to run rewards or notify players. It is not cancellable.</p>
 */
public class SpellDiscoveredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Spell spell;

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public SpellDiscoveredEvent(Player player, Spell spell) {
        this.player = player;
        this.spell = spell;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the player who discovered the spell.
     *
     * @return the discovering player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the discovered spell.
     *
     * @return the discovered spell
     */
    public Spell getSpell() {
        return spell;
    }
}
