package me.nagasonic.alkatraz.items.magic.effect;

import me.nagasonic.alkatraz.api.magic.effect.Effect;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;

import java.util.Map;

public final class ParticleEffect implements Effect {
    private final Particle particle;
    private final int count;
    private final double offsetX, offsetY, offsetZ;
    private final double speed;

    public ParticleEffect(Particle particle, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        this.particle = particle;
        this.count = count;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.speed = speed;
    }

    @Override
    public void execute(TriggerContext context) {
        LivingEntity origin = context.actor();
        if (origin == null) return;
        origin.getWorld().spawnParticle(particle, origin.getLocation(), count, offsetX, offsetY, offsetZ, speed);
    }

    public static Effect fromConfig(Map<String, Object> config) {
        Particle particle = Particle.valueOf(String.valueOf(config.getOrDefault("particle", "FLAME")));
        return new ParticleEffect(
                particle,
                Integer.parseInt(String.valueOf(config.getOrDefault("count", 10))),
                Double.parseDouble(String.valueOf(config.getOrDefault("offset_x", 0.5))),
                Double.parseDouble(String.valueOf(config.getOrDefault("offset_y", 0.5))),
                Double.parseDouble(String.valueOf(config.getOrDefault("offset_z", 0.5))),
                Double.parseDouble(String.valueOf(config.getOrDefault("speed", 0.1)))
        );
    }
}
