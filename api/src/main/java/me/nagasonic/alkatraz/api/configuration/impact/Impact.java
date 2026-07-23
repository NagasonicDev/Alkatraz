package me.nagasonic.alkatraz.api.configuration.impact;

import org.bukkit.entity.Player;

/**
 * Represents an effect that can be applied to or unapplied from a player.
 * <p>
 * Implementations of this interface define a reversible modification to a player's state,
 * such as a stat boost or debuff. The effect must be cleanly reversible via {@link #unapply(Player)}.
 * </p>
 */
public interface Impact {

    /**
     * Applies this impact effect to the specified player.
     *
     * @param player the player to apply the effect to
     */
    void apply(Player player);

    /**
     * Removes this impact effect from the specified player, reverting any changes made by {@link #apply(Player)}.
     *
     * @param player the player to remove the effect from
     */
    void unapply(Player player);

    /**
     * Returns a human-readable description of this impact effect.
     *
     * @return the description of this impact
     */
    String getDescription();
}
