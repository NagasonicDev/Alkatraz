package me.nagasonic.alkatraz.configuration.impact;

import org.bukkit.entity.Player;

public interface Impact {

    void apply(Player player);

    void unapply(Player player);

    String getDescription();
}
