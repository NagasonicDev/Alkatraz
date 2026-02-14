package me.nagasonic.alkatraz.spells.configuration.impact;

import org.bukkit.entity.Player;

public interface ValueImpact {
    /**
     * Applies this impact to the player
     * @param player The player to apply the impact to.
     */
    void apply(Player player);

    /**
     * Removes this impact from the player
     * @param player The player to remove the impact from.
     */
    void unapply(Player player);

    /**
     * Gets a description of this impact for display to players
     */
    String getDescription();
}
