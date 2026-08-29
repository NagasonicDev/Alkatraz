package me.nagasonic.alkatraz.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called whenever a player's focus value changes (regeneration, movement
 * penalty, damage loss). Not cancellable.
 */
public class PlayerFocusChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final double oldFocus;
    private final double newFocus;

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public PlayerFocusChangeEvent(Player player, double oldFocus, double newFocus) {
        this.player = player;
        this.oldFocus = oldFocus;
        this.newFocus = newFocus;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public Player getPlayer() {
        return player;
    }

    public double getOldFocus() {
        return oldFocus;
    }

    public double getNewFocus() {
        return newFocus;
    }
}
