package me.nagasonic.alkatraz.util;

import me.nagasonic.alkatraz.config.ParticleConfig;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Sends particles per-player instead of broadcasting to the whole world. Players beyond
 * {@code particles.max_radius} receive nothing, players between {@code dense_radius} and
 * {@code max_radius} receive a linearly reduced count, everyone inside {@code dense_radius}
 * receives the full count, and the caster always receives full density. When
 * {@code particles.enabled} is false this falls through to the vanilla {@link World} broadcast,
 * so behavior is identical to the old direct call.
 */
public final class ParticleCaster {

    private ParticleCaster() {}

    public static void spawn(LivingEntity caster, World world, Particle particle, Location location, int count) {
        dispatch(caster, world, particle, location, count, 0, 0, 0, 0, null);
    }

    public static <T> void spawn(LivingEntity caster, World world, Particle particle, Location location, int count, T data) {
        dispatch(caster, world, particle, location, count, 0, 0, 0, 0, data);
    }

    public static void spawn(LivingEntity caster, World world, Particle particle, Location location, int count,
                             double dx, double dy, double dz) {
        dispatch(caster, world, particle, location, count, dx, dy, dz, 0, null);
    }

    public static void spawn(LivingEntity caster, World world, Particle particle, Location location, int count,
                             double dx, double dy, double dz, double speed) {
        dispatch(caster, world, particle, location, count, dx, dy, dz, speed, null);
    }

    public static <T> void spawn(LivingEntity caster, World world, Particle particle, Location location, int count,
                                 double dx, double dy, double dz, T data) {
        dispatch(caster, world, particle, location, count, dx, dy, dz, 0, data);
    }

    public static <T> void spawn(LivingEntity caster, World world, Particle particle, Location location, int count,
                                 double dx, double dy, double dz, double speed, T data) {
        dispatch(caster, world, particle, location, count, dx, dy, dz, speed, data);
    }

    public static void spawnForPlayer(Player recipient, World world, Particle particle, Location location, int count) {
        dispatchTo(recipient, world, particle, location, count, 0, 0, 0, 0, null);
    }

    public static <T> void spawnForPlayer(Player recipient, World world, Particle particle, Location location, int count, T data) {
        dispatchTo(recipient, world, particle, location, count, 0, 0, 0, 0, data);
    }

    private static <T> void dispatchTo(Player recipient, World world, Particle particle, Location location, int count,
                                       double dx, double dy, double dz, double speed, T data) {
        if (!ParticleConfig.isEnabled()) {
            world.spawnParticle(particle, location, count, dx, dy, dz, speed, data);
            return;
        }
        recipient.spawnParticle(particle, location, count, dx, dy, dz, speed, data);
    }

    private static <T> void dispatch(LivingEntity caster, World world, Particle particle, Location location, int count,
                                     double dx, double dy, double dz, double speed, T data) {
        if (!ParticleConfig.isEnabled()) {
            world.spawnParticle(particle, location, count, dx, dy, dz, speed, data);
            return;
        }

        double dense = ParticleConfig.getDenseRadius();
        double max = ParticleConfig.getMaxRadius();
        double minScale = ParticleConfig.getMinScale();
        boolean casterFull = ParticleConfig.isCasterFullDensity();
        double denseSq = dense * dense;
        double maxSq = max * max;
        Player casterPlayer = caster instanceof Player ? (Player) caster : null;

        for (Player player : world.getPlayers()) {
            double distSq = player.getLocation().distanceSquared(location);
            boolean isCaster = casterPlayer != null && player.equals(casterPlayer);
            if (distSq > maxSq && !isCaster) continue;

            double scale = isCaster && casterFull ? 1.0 : scaleForDistanceSq(distSq, denseSq, maxSq, minScale);
            if (scale <= 0) continue;

            player.spawnParticle(particle, location, scaledCount(count, scale), dx, dy, dz, speed, data);
        }
    }

    static double scaleForDistanceSq(double distSq, double denseSq, double maxSq, double minScale) {
        if (distSq <= denseSq) return 1.0;
        if (denseSq >= maxSq) return minScale;
        if (distSq >= maxSq) return minScale;
        double t = (Math.sqrt(distSq) - Math.sqrt(denseSq)) / (Math.sqrt(maxSq) - Math.sqrt(denseSq));
        return 1.0 - t * (1.0 - minScale);
    }

    static int scaledCount(int count, double scale) {
        if (count <= 0) return 0;
        return Math.max(1, (int) Math.floor(count * scale));
    }
}
