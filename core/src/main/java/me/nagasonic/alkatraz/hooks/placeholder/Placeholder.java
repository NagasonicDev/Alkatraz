package me.nagasonic.alkatraz.hooks.placeholder;

import org.bukkit.entity.Player;

public interface Placeholder {
    String name();
    String onPlaceholderRequest(Player player, String params);
}
