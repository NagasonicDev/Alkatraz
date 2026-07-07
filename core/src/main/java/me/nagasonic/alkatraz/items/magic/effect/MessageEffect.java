package me.nagasonic.alkatraz.items.magic.effect;

import me.nagasonic.alkatraz.api.magic.effect.Effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;

import java.util.Map;

public final class MessageEffect implements Effect {
    private final String message;
    private final boolean actionBar;

    public MessageEffect(String message, boolean actionBar) {
        this.message = message;
        this.actionBar = actionBar;
    }

    @Override
    public void execute(TriggerContext context) {
        context.playerActor().ifPresent(player -> {
            if (actionBar) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message)));
            } else {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        });
    }

    public static Effect fromConfig(Map<String, Object> config) {
        return new MessageEffect(
                String.valueOf(config.getOrDefault("message", "&aEffect triggered!")),
                Boolean.parseBoolean(String.valueOf(config.getOrDefault("action_bar", false)))
        );
    }
}
