package me.nagasonic.alkatraz.configuration.requirement;

import org.bukkit.entity.Player;

public interface Requirement {

    boolean isMet(Player player);

    String getDescription();
}
