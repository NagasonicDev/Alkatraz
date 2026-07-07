package me.nagasonic.alkatraz.api.configuration.impact;

import org.bukkit.entity.Player;

public interface Impact {

    void apply(Player player);

    void unapply(Player player);

    String getDescription();
}
