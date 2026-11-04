package me.nagasonic.alkatraz.util;

import me.nagasonic.alkatraz.config.ParticleConfig;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sends particles per-player instead of broadcasting to the whole world. Players beyond
 * {@code particles.max_radius} receive nothing, players between {@code dense_radius} and
 * {@code max_radius} receive a linearly reduced count, everyone inside {@code dense_radius}
 * receives the full count, and the caster always receives full density. When
 * {@code particles.enabled} is false this falls through to the vanilla {@link World} broadcast,
 * so behavior is identical to the old direct call.
 */
public final class ParticleCaster {

    private static final long CONFIG_REFRESH_MS = 1000L;
    private static final long TICK_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(50L);
    private static final Color DEFAULT_COLOR = Color.fromRGB(255, 255, 255);
    private static final Particle.DustOptions DEFAULT_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.0F);

    private static volatile PlayerSnapshot snapshot;

    private record PlayerSnapshot(World world, long window, List<Player> players) {}

    private static volatile boolean configLoaded = false;
    private static volatile double denseRadius = 12.0;
    private static volatile double maxRadius = 32.0;
    private static volatile double minScale = 0.25;
    private static volatile boolean casterFullDensity = true;
    private static long lastConfigRead = 0L;

    private ParticleCaster() {}

    /**
     * Re-reads the particle settings from config at most once per second so a
     * live {@code /alkatraz reload} still takes effect without paying for four
     * YAML lookups on every particle dispatch.
     */
    private static void refreshConfig() {
        long now = System.currentTimeMillis();
        if (configLoaded && now - lastConfigRead < CONFIG_REFRESH_MS) return;
        synchronized (ParticleCaster.class) {
            if (configLoaded && now - lastConfigRead < CONFIG_REFRESH_MS) return;
            denseRadius = ParticleConfig.getDenseRadius();
            maxRadius = ParticleConfig.getMaxRadius();
            minScale = ParticleConfig.getMinScale();
            casterFullDensity = ParticleConfig.isCasterFullDensity();
            lastConfigRead = now;
            configLoaded = true;
        }
    }

    /**
     * Some particle types declare a required data class, and Paper rejects the call
     * outright when it is null. {@code ENTITY_EFFECT} wants a {@link Color}, {@code DUST}
     * wants a {@link Particle.DustOptions}. Callers that do not care about the tint leave
     * {@code data} null, so fill in a neutral default based on the declared type.
     */
    private static <T> T withDefaultData(Particle particle, T data) {
        if (particle == null) return data;
        Class<?> required = particle.getDataType();
        // Newer Bukkit versions reject payloads for particles whose data type is Void.
        // Older renamed particles may have accepted/ignored them, so discard the payload
        // instead of letting one bad cosmetic call fail its scheduled task.
        if (required == Void.class || required == null) return null;
        if (data != null) return required.isInstance(data) ? data : null;
        if (required == Color.class) return cast(DEFAULT_COLOR);
        if (required == Particle.DustOptions.class) return cast(DEFAULT_DUST);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    private static List<Player> playersIn(World world) {
        if (world == null) return Collections.emptyList();
        long window = System.nanoTime() / TICK_WINDOW_NANOS;
        PlayerSnapshot cached = snapshot;
        if (cached != null && cached.world == world && cached.window == window) {
            return cached.players;
        }
        List<Player> players = world.getPlayers();
        snapshot = new PlayerSnapshot(world, window, players);
        return players;
    }

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
        T payload = withDefaultData(particle, data);
        int particles = normalizeCount(count);
        if (particles <= 0) return;
        if (!ParticleConfig.isEnabled()) {
            world.spawnParticle(particle, location, particles, dx, dy, dz, speed, payload);
            return;
        }
        recipient.spawnParticle(particle, location, particles, dx, dy, dz, speed, payload);
    }

    private static <T> void dispatch(LivingEntity caster, World world, Particle particle, Location location, int count,
                                     double dx, double dy, double dz, double speed, T data) {
        T payload = withDefaultData(particle, data);
        int particles = normalizeCount(count);
        if (particles <= 0) return;
        if (!ParticleConfig.isEnabled()) {
            world.spawnParticle(particle, location, particles, dx, dy, dz, speed, payload);
            return;
        }

        refreshConfig();
        double dense = denseRadius;
        double max = maxRadius;
        double minScale = ParticleCaster.minScale;
        boolean casterFull = casterFullDensity;
        double denseSq = dense * dense;
        double maxSq = max * max;
        Player casterPlayer = caster instanceof Player ? (Player) caster : null;
        double lx = location.getX();
        double ly = location.getY();
        double lz = location.getZ();

        for (Player player : playersIn(world)) {
            Location pl = player.getLocation();
            double pdx = pl.getX() - lx;
            double pdy = pl.getY() - ly;
            double pdz = pl.getZ() - lz;
            double distSq = pdx * pdx + pdy * pdy + pdz * pdz;
            boolean isCaster = casterPlayer != null && player.equals(casterPlayer);
            if (distSq > maxSq && !isCaster) continue;

            double scale = isCaster && casterFull ? 1.0 : scaleForDistanceSq(distSq, denseSq, maxSq, minScale);
            if (scale <= 0) continue;

            player.spawnParticle(particle, location, scaledCount(particles, scale), dx, dy, dz, speed, payload);
        }
    }

    /**
     * Vanilla reads a particle count of zero as "the offsets are absolute coordinates, draw exactly one
     * particle at this point", not as "draw nothing". The geometry VFX layer spells one particle per
     * computed point that way, so zero has to reach the client as a single particle rather than being
     * dropped. With zero offsets a count of one renders exactly the same particle zero did.
     */
    static int normalizeCount(int count) {
        return count < 0 ? 0 : Math.max(1, count);
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
