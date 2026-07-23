package me.nagasonic.alkatraz.api.configuration.requirement;

import org.bukkit.entity.Player;

/**
 * Represents a prerequisite condition that a player must satisfy.
 * <p>
 * Implementations of this interface define a check (e.g., level, inventory, permission)
 * that determines whether a player meets the requirement to perform an action such as casting a spell.
 * </p>
 */
public interface Requirement {

    /**
     * Checks whether the specified player meets this requirement.
     *
     * @param player the player to check
     * @return {@code true} if the player meets this requirement, {@code false} otherwise
     */
    boolean isMet(Player player);

    /**
     * Returns a human-readable description of this requirement.
     *
     * @return the description of this requirement
     */
    String getDescription();
}
