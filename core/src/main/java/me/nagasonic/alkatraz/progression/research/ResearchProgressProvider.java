package me.nagasonic.alkatraz.progression.research;

import org.bukkit.entity.Player;

/**
 * Adapter boundary for external or future research systems.
 */
@FunctionalInterface
public interface ResearchProgressProvider {

    boolean hasCompleted(Player player, String researchId);
}
