package me.nagasonic.alkatraz.api;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface StatsProvider {
    PlayerStats getStats(Player player);
}
