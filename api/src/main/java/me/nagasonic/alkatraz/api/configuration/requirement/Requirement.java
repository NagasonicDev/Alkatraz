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

    /**
     * Returns the player's progress towards meeting this requirement as a percentage.
     *
     * @param player the player to check
     * @return a progress value between 0 and 100
     */
    default int getProgress(Player player) {
        return isMet(player) ? 100 : 0;
    }

    /**
     * Returns a human-readable description of this requirement for the given player.
     *
     * @param player the player to check
     * @return the description of this requirement
     */
    default String getDescription(Player player) {
        return getDescription();
    }
}
