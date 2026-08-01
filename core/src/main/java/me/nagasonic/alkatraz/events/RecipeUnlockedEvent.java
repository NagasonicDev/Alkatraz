package me.nagasonic.alkatraz.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player unlocks a recipe.
 *
 * <p>This event is public so other plugins can listen for recipe unlocks, e.g. to run rewards or
 * broadcast progress. It is not cancellable.</p>
 */
public class RecipeUnlockedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String recipeKey;

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public RecipeUnlockedEvent(Player player, String recipeKey) {
        this.player = player;
        this.recipeKey = recipeKey;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the player who unlocked the recipe.
     *
     * @return the unlocking player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the lowercased recipe key that was unlocked.
     *
     * @return the recipe key
     */
    public String getRecipeKey() {
        return recipeKey;
    }
}
