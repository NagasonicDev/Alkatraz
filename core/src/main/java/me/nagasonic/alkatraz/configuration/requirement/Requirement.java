package me.nagasonic.alkatraz.configuration.requirement;

import org.bukkit.entity.Player;

public interface Requirement {

    boolean isMet(Player player);

    String getDescription();

    default int getProgress(Player player) {
        return isMet(player) ? 100 : 0;
    }

    default String getDescription(Player player) {
        return getDescription();
    }
}
