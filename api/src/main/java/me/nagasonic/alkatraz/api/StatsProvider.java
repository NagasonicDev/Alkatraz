package me.nagasonic.alkatraz.api;

import org.bukkit.entity.Player;

/**
 * Functional interface that resolves a player's {@link PlayerStats} from a Bukkit {@link Player}.
 * Implementations are expected to return a read-only snapshot of the player's current magical stats.
 */
@FunctionalInterface
public interface StatsProvider {

    /**
     * Returns the magic stats for the given player.
     *
     * @param player the player whose stats to retrieve
     * @return the player's current magical stats
     */
    PlayerStats getStats(Player player);
}
