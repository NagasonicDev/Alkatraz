package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The circle 6-9 post-payload spectacle: what the spell looks like <i>after</i> its effect has
 * landed, so a ten-second circle-9 cast ends on a ten-second arc rather than on a single frame.
 *
 * <h2>It is spectacle only</h2>
 * An aftermath never changes a block, never damages, and never cancels movement. It only spawns
 * particles through {@link ParticleCaster#spawn} using {@link Utils} aliases, so it cannot
 * contradict the payload that already resolved.
 *
 * <h2>One per caster, and lifecycle-safe</h2>
 * A fresh cast silently cancels the caster's previous aftermath, and the task is dropped on quit,
 * death, world change and plugin shutdown, so nothing survives its owner.
 *
 * <p>The generic archetype (burst, shock rings, drifting motes, and the circle-9 phase sequence)
 * is drawn here; a spell may add its own tail by overriding {@link Spell#drawAftermath}.
 */
public final class CastAftermath implements Listener {

    /** Registered once from {@code Alkatraz.onEnable} so quit/death/world-change are cleaned up. */
    public static final CastAftermath INSTANCE = new CastAftermath();

    private static final Map<UUID, BukkitTask> RUNNING = new ConcurrentHashMap<>();

    private CastAftermath() {
    }

    /**
     * Begins the aftermath for a completed cast. Does nothing below circle 6, where
     * {@link CastTimeline#aftermathTicks(int)} is zero.
     *
     * @param spell           the spell that just resolved; supplies the circle and element palette
     * @param caster          the caster, used as the particle-density reference and cleanup key
     * @param origin          where the payload landed; a null falls back to the caster's feet
     * @param actualCastTicks the cast time that actually elapsed; kept for debug only
     */
    public static void begin(Spell spell, LivingEntity caster, Location origin, int actualCastTicks) {
        if (spell == null || caster == null) return;
        int circle = spell.getRequiredCircleLevel();
        int total = CastTimeline.aftermathTicks(circle);
        if (total <= 0) return;

        UUID id = caster.getUniqueId();
        cancel(caster);
        Location anchor = (origin == null ? caster.getLocation() : origin).clone();
        ElementPalette.Palette palette = ElementPalette.of(spell.getElement());
        int[] tick = {0};

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (tick[0] >= total) {
                    RUNNING.remove(id);
                    cancel();
                    return;
                }
                draw(spell, caster, anchor, palette, circle, tick[0], total);
                tick[0]++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
        RUNNING.put(id, task);
    }

    /** Stops a caster's aftermath, if any. Safe to call when nothing is running. */
    public static void cancel(LivingEntity caster) {
        if (caster == null) return;
        BukkitTask task = RUNNING.remove(caster.getUniqueId());
        if (task != null) {
            try {
                task.cancel();
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Cancels every running aftermath. Called from {@code Alkatraz.onDisable}. */
    public static void shutdown() {
        for (BukkitTask task : RUNNING.values()) {
            try {
                task.cancel();
            } catch (RuntimeException ignored) {
            }
        }
        RUNNING.clear();
    }

    // ================================================================
    // Lifecycle
    // ================================================================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cancel(event.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        cancel(event.getPlayer());
    }

    // ================================================================
    // Visuals
    // ================================================================

    private static void draw(Spell spell, LivingEntity caster, Location origin,
                             ElementPalette.Palette palette, int circle, int tick, int total) {
        World world = origin.getWorld();
        if (world == null) return;
        double t = clamp((double) tick / Math.max(1, total));
        Location scratch = new Location(world, 0.0, 0.0, 0.0);

        // Impact flash, gone within a few ticks.
        if (tick < 3) {
            ParticleCaster.spawn(caster, world, Utils.END_ROD, origin, 6);
        }

        // Expanding shock rings: later rings start later, so the blast reads as a wave train.
        int waves = circle >= 8 ? 3 : 2;
        for (int w = 0; w < waves; w++) {
            double waveT = clamp((t - w * 0.16) / 0.7);
            if (waveT <= 0.0) continue;
            double radius = 1.0 + waveT * (4.0 + circle);
            double fade = 1.0 - waveT;
            if (fade <= 0.05) continue;
            ring(caster, world, scratch, origin, radius, 24 + circle * 2, palette.coreDust());
            ring(caster, world, scratch, origin, radius * 0.7, 16, palette.accentDust());
        }

        // Settling element motes: drift outward and down, thinning as the aftermath fades.
        int motes = 10 + circle * 3;
        double drift = 1.0 + t * 6.0;
        for (int i = 0; i < motes; i++) {
            double angle = Math.PI * 2.0 * i / motes + tick * 0.06;
            double r = drift * (0.6 + 0.4 * ((i % 3) / 2.0));
            scratch.setX(origin.getX() + Math.cos(angle) * r);
            scratch.setY(origin.getY() + 0.4 + Math.sin(angle * 2.0) * 0.6 - t * 1.5);
            scratch.setZ(origin.getZ() + Math.sin(angle) * r);
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.accentDust());
        }

        // Circle 7 adds a collapsing column; circle 8 adds element rain and a fading ground scar.
        if (circle >= 7) {
            double collapse = 1.0 - t;
            int columnPoints = 14;
            for (int i = 0; i < columnPoints; i++) {
                double u = (double) i / columnPoints;
                double angle = tick * 0.2 + u * Math.PI * 4.0;
                double r = (1.6 + 2.0 * u) * collapse;
                scratch.setX(origin.getX() + Math.cos(angle) * r);
                scratch.setY(origin.getY() + 5.0 * collapse * (1.0 - u));
                scratch.setZ(origin.getZ() + Math.sin(angle) * r);
                ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.deepDust());
            }
        }
        if (circle >= 8 && tick < 40) {
            int rain = 8;
            for (int i = 0; i < rain; i++) {
                double angle = Math.PI * 2.0 * i / rain + tick * 0.25;
                double r = 2.0 + (i % 4);
                scratch.setX(origin.getX() + Math.cos(angle) * r);
                scratch.setY(origin.getY() + 9.0 - tick * 0.18);
                scratch.setZ(origin.getZ() + Math.sin(angle) * r);
                ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.coreDust());
            }
        }

        // Circle 9 runs five phases over its 130 ticks: impact, storm, pillar, dissolve, close.
        if (circle >= 9) {
            if (tick < 20) {
                // Phase 1 is the shared burst above; a rising flare marks the transition.
                ParticleCaster.spawn(caster, world, Utils.END_ROD, origin.clone().add(0, 2.0, 0), 2);
            } else if (tick < 60) {
                storm(caster, world, scratch, origin, palette, tick);
            } else if (tick < 100) {
                pillar(caster, world, scratch, origin, palette, (tick - 60) / 40.0);
            } else {
                double close = (tick - 100) / 30.0;
                ring(caster, world, scratch, origin, 3.0 * (1.0 - close), 28, palette.coreDust());
            }
        }

        // A spell may add its own tail on top of the archetype.
        spell.drawAftermath(caster, origin, tick, total, palette);
    }

    /** Circle 9's element storm: a dense churn of dust inside the blast radius. */
    private static void storm(LivingEntity caster, World world, Location scratch, Location origin,
                              ElementPalette.Palette palette, int tick) {
        for (int i = 0; i < 18; i++) {
            double angle = tick * 0.31 + i * 2.399;
            double r = 1.0 + (i % 5);
            scratch.setX(origin.getX() + Math.cos(angle) * r);
            scratch.setY(origin.getY() + 0.3 + Math.abs(Math.sin(tick * 0.2 + i)) * 5.0);
            scratch.setZ(origin.getZ() + Math.sin(angle) * r);
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0,
                    (i % 3 == 0) ? palette.coreDust() : palette.accentDust());
        }
    }

    /** Circle 9's ascending pillar: a rising column of core dust, leaving motes behind. */
    private static void pillar(LivingEntity caster, World world, Location scratch, Location origin,
                               ElementPalette.Palette palette, double progress) {
        double p = clamp(progress);
        for (int i = 0; i < 16; i++) {
            double u = (double) i / 16;
            double angle = p * Math.PI * 3.0 + u * Math.PI * 2.0;
            double r = 1.2 * (1.0 - u * 0.5);
            scratch.setX(origin.getX() + Math.cos(angle) * r);
            scratch.setY(origin.getY() + u * 12.0 * p);
            scratch.setZ(origin.getZ() + Math.sin(angle) * r);
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.coreDust());
        }
    }

    private static void ring(LivingEntity caster, World world, Location scratch, Location center,
                             double radius, int points, Particle.DustOptions dust) {
        double r = Math.max(0.0, radius);
        int count = Math.max(3, points);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            scratch.setX(center.getX() + Math.cos(angle) * r);
            scratch.setY(center.getY() + 0.1);
            scratch.setZ(center.getZ() + Math.sin(angle) * r);
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, dust);
        }
    }

    private static double clamp(double value) {
        return Math.min(1.0, Math.max(0.0, value));
    }
}
