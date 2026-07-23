package me.nagasonic.alkatraz.api.progression.research;

import org.bukkit.entity.Player;

/**
 * Adapter boundary for external or future research systems.
 */
@FunctionalInterface
public interface ResearchProgressProvider {

    /**
     * Checks whether the given player has completed the specified research.
     *
     * @param player the player to check
     * @param researchId the research identifier
     * @return {@code true} if the research is completed
     */
    boolean hasCompleted(Player player, String researchId);
}
