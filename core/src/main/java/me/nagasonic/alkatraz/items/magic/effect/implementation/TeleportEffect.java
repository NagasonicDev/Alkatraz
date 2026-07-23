package me.nagasonic.alkatraz.items.magic.effect.implementation;

import me.nagasonic.alkatraz.api.magic.effect.Effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;

public final class TeleportEffect implements Effect {
    private final double distance;

    public TeleportEffect(double distance) {
        this.distance = distance;
    }

    @Override
    public void execute(TriggerContext context) {
        if (context.actor() instanceof Player player) {
            Location loc = player.getLocation();
            loc.add(loc.getDirection().multiply(distance));
            loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);
            player.teleport(loc);
        }
    }

    public static Effect fromConfig(Map<String, Object> config) {
        double distance = Double.parseDouble(String.valueOf(config.getOrDefault("distance", 5.0)));
        return new TeleportEffect(distance);
    }
}
