package me.nagasonic.alkatraz.spells.configuration.requirement;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.Player;

public interface ValueRequirement {
    /**
     * Checks if this requirement is met for the given player
     * @param player The Player.
     */
    boolean isMet(Player player);

    /**
     * Gets a description of this requirement for display to players
     */
    String getDescription();
}
