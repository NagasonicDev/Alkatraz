package me.nagasonic.alkatraz.items.magic.effect;

import me.nagasonic.alkatraz.api.magic.effect.Effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.Bukkit;

import java.util.Map;

public final class CommandEffect implements Effect {
    private final String command;

    public CommandEffect(String command) {
        this.command = command;
    }

    @Override
    public void execute(TriggerContext context) {
        String parsed = command
                .replace("%player%", context.actor() != null ? context.actor().getName() : "")
                .replace("%target%", context.target() != null ? context.target().getName() : "");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
    }

    public static Effect fromConfig(Map<String, Object> config) {
        return new CommandEffect(String.valueOf(config.getOrDefault("command", "say Hello!")));
    }
}
