package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.util.SustainEffect;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 9 AIR capstone: call down a storm that follows the caster, buffets nearby
 * enemies and repeatedly strikes the nearest target. It grants no flight.
 *
 * <p>The storm is a mana-drained toggle sustain. Its teardown is owned by
 * {@link SustainEffect}, so it stops cleanly on expiry, recast, insufficient mana,
 * death, quit or counter. Air never edits terrain.</p>
 */
public class Skywrath extends AttackSpell {

    /** Pale violet - the only purple in the air element, and the palest dust of the five. */
    private static final Color SKY_TINT = Color.fromRGB(235, 200, 255);
    private static final Color BOLT_TINT = Color.fromRGB(250, 245, 255);
    private static final Particle.DustOptions SKY_DUST = new Particle.DustOptions(SKY_TINT, 1.0F);
    private static final Particle.DustOptions SKY_BOLT = new Particle.DustOptions(BOLT_TINT, 1.5F);

    /** Rings drawn per tick at the storm. */
    private static final int SKY_LAYERS = 6;
    /** Points per ring. */
    private static final int SKY_RING_POINTS = 18;
    /** How much each successive ring shrinks, as a fraction of the radius. */
    private static final double SKY_LAYER_SHRINK = 0.22;
    /** Vertical drop between successive rings, in blocks. */
    private static final double SKY_LAYER_DROP = 1.1;
    /** How fast the rings spin, in radians per tick. */
    private static final double SKY_SPIN = 0.35;
    /** The sky column between caster and storm is drawn every this many ticks. */
    private static final int SKY_COLUMN_EVERY = 2;
    /** Points on the sky column. */
    private static final int SKY_COLUMN_POINTS = 16;
    /** Cloud halo count and its spread multipliers. */
    private static final int SKY_CLOUD_COUNT = 40;
    private static final double SKY_CLOUD_SPREAD_H = 0.6;
    private static final double SKY_CLOUD_SPREAD_V = 0.12;
    /** Horizontal and vertical jitter written to a buffeted target, per tick. */
    private static final double SKY_BUFF_JITTER = 0.16;
    private static final double SKY_BUFF_LIFT = 0.14;
    /**
     * Vertical velocity at or above which a target is left alone. {@link #SKY_BUFF_LIFT} is an
     * impulse added every tick, so without this gate a mob standing still reaches 0.43 after two
     * ticks and keeps climbing - it is thrown ~12 blocks up and vanilla fall damage from there is
     * 9 hearts. 0.3 tops out the lift at one impulse of ~1.2 blocks, far under the 3-block fall
     * damage threshold, so the buffet displaces without ever killing.
     */
    private static final double SKY_BUFF_CEILING = 0.3;
    /** Dust points drawn on a buffeted target. */
    private static final int SKY_BUFF_DUST = 2;
    /** Segments in one bolt, excluding both endpoints. */
    private static final int SKY_BOLT_SEGMENTS = 17;
    /** Vertical velocity written to a struck target. */
    private static final double SKY_STRIKE_LIFT = 0.75;
    /** Dispersion counts when the storm ends. */
    private static final int SKY_DISPERSE_EMITTER = 1;
    private static final int SKY_DISPERSE_SMOKE = 50;
    /** The mob path uses a short storm anchored around the combat area. */
    private static final double MOB_STORM_DURATION = 5.0;
    private static final double MOB_STORM_RADIUS = 5.0;
    private static final double MOB_STORM_HEIGHT = 6.0;

    private SkywrathConfig config;

    public Skywrath(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/skywrath.yml");
        Alkatraz.getInstance().saveConfig("spells/skywrath_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/skywrath.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = SkywrathConfig.from(spellConfig);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double duration = getModifiedStat(caster, "storm_duration", config.stormDuration());
        double radius = getModifiedStat(caster, "storm_radius", config.stormRadius());
        double height = getModifiedStat(caster, "storm_height", config.stormHeight());
        int stormTicks = Math.max(1, (int) Math.round(duration * 20.0));

        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster), AttackType.MAGIC);
        StormClock clock = new StormClock();
        double upkeep = getModifiedStat(caster, "upkeep_mana_per_second", config.upkeepManaPerSecond());

        // The toggle runs first: recasting ends this live storm without opening a
        // second instance. Circle-9 sustains share one slot, so a foreign owner
        // blocks this cast without affecting that sustain.
        SustainEffect.Toggle toggled = SustainEffect.toggle(caster, upkeep, this,
                (c, elapsed) -> {
                    if (elapsed == 0) {
                        clock.bar = Bukkit.createBossBar(lang().get("spells.skywrath.name"),
                                getMasteryBarColor(), BarStyle.SEGMENTED_10);
                        clock.bar.addPlayer(c);
                    }
                    boolean keepGoing = tickStorm(c, clock, wand, props,
                            stormTicks, radius, height);
                    if (clock.bar != null) {
                        clock.bar.setProgress(remainingFraction(c, elapsed, stormTicks, upkeep));
                    }
                    return keepGoing;
                },
                p -> endStorm(p, clock, radius));
        if (toggled == SustainEffect.Toggle.BLOCKED) {
            // Blocked changed nothing, so Spell.cast must refund this cast and skip cooldown/mastery.
            cancelCast(caster);
            Utils.sendActionBar(caster, lang().get("spells.sustain.blocked"));
            return;
        }
        if (toggled == SustainEffect.Toggle.CLOSED) {
            // Return this cast's modified mana cost when toggling the storm off.
            StatUtils.addMana(caster, getModifiedManaCost(caster));
            return;
        }

        World world = caster.getWorld();
        if (world != null) {
            world.playSound(caster.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand), AttackType.MAGIC);
        StormClock clock = new StormClock();
        int life = (int) Math.round(MOB_STORM_DURATION * 20.0);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (tickStorm(caster, clock, wand, props, life, MOB_STORM_RADIUS, MOB_STORM_HEIGHT)) {
                    return;
                }
                World world = caster.getWorld();
                if (world != null) dissipate(caster, world, MOB_STORM_RADIUS);
                cancel();
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * The single end path of a player's storm. Runs exactly once per instance
     * whichever way it ended - the duration elapsed, the second cast, mana ran
     * dry, the caster died, quit or the cast was countered - because
     * {@link SustainEffect} owns the lifecycle.
     */
    private void endStorm(Player caster, StormClock clock, double radius) {
        if (clock.bar != null) {
            clock.bar.removeAll();
            clock.bar = null;
        }
        if (caster == null) return;
        World world = caster.getWorld();
        if (world != null) dissipate(caster, world, radius);
    }

    /** The bar empties on whichever runs out first: the clock, or the caster's mana. */
    private double remainingFraction(Player caster, int elapsed, int life, double upkeepPerSecond) {
        double byTime = (life - elapsed) / 20.0;
        MagicProfile manaProfile = upkeepPerSecond > 0
                ? ProfileManager.getProfile(caster.getUniqueId(), MagicProfile.class)
                : null;
        double byMana = (upkeepPerSecond > 0 && manaProfile != null)
                ? manaProfile.getMana() / upkeepPerSecond
                : byTime;
        return Math.max(0.0, Math.min(byTime, byMana) / (life / 20.0));
    }

    /**
     * The storm, shared by both cast paths. One clock, one cadence gate and one
     * per-target map, so countering the cast kills the whole storm.
     *
     * @return false once the storm is over; the caller owns the closing VFX
     */
    private boolean tickStorm(LivingEntity caster, StormClock clock, ItemStack wand, AttackProperties props,
                              int life, double radius, double height) {
        World world = caster.getWorld();
        if (world == null || props.isCountered() || props.isCancelled()
                || caster.isDead() || clock.t >= life
                || (caster instanceof Player online && !online.isOnline())) {
            return false;
        }

        // The storm follows the caster: re-anchored every tick, which is why
        // Utils.resolveTarget is never called anywhere in this class.
        Location anchor = caster.getLocation();
        Location storm = anchor.clone().add(0.0, height, 0.0);

        drawStorm(caster, world, storm, anchor, radius, height, clock.t);
        // Keep a visible outer edge throughout the full storm sustain.
        SpellVfx.ring(SKY_TINT, caster, storm, radius * 1.5, SKY_RING_POINTS, clock.t * SKY_SPIN, 0.8);
        buffet(caster, world, storm, radius, height);

        if (clock.t - clock.lastStrikeTick < SpellAttackContext.DAMAGE_INTERVAL_TICKS) {
            clock.t++;
        } else {
            clock.lastStrikeTick = clock.t;
            LivingEntity struck = nearestStrikeable(caster, storm, radius);
            if (struck != null) {
                bolt(caster, wand, props, world, storm, struck, clock.nextStrikeTick, clock.t);
            }
            clock.t++;
        }

        return true;
    }

    /** Six shrinking rings, the sky column down to the caster, and the cloud halo. */
    private void drawStorm(LivingEntity caster, World world, Location storm, Location anchor,
                           double radius, double height, int tick) {
        for (int layer = 0; layer < SKY_LAYERS; layer++) {
            double r = radius * (1.5 - SKY_LAYER_SHRINK * layer);
            for (int i = 0; i < SKY_RING_POINTS; i++) {
                double angle = 2 * Math.PI * i / SKY_RING_POINTS + tick * SKY_SPIN + layer * 0.75;
                Location point = storm.clone().add(
                        Math.cos(angle) * r, -layer * SKY_LAYER_DROP, Math.sin(angle) * r);
                ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, SKY_DUST);
            }
        }

        if (tick % SKY_COLUMN_EVERY == 0) {
            Location feet = anchor.clone().add(0, 1.0, 0);
            // Bukkit's Location has no lerp, so the interpolation is written out as
            // storm + (feet - storm) * t, which is what lerp would have computed.
            // The delta is loop-invariant, so it is hoisted into three doubles and
            // every point lands in ONE scratch Location: no allocation per point on
            // a path that runs every other tick for the whole storm, and the scratch
            // never escapes this loop, so no shared Location is ever mutated.
            double sx = storm.getX();
            double sy = storm.getY();
            double sz = storm.getZ();
            double dx = feet.getX() - sx;
            double dy = feet.getY() - sy;
            double dz = feet.getZ() - sz;
            Location point = storm.clone();
            for (int i = 0; i < SKY_COLUMN_POINTS; i++) {
                double t = (i + 1) / (double) SKY_COLUMN_POINTS;
                point.setX(sx + dx * t);
                point.setY(sy + dy * t);
                point.setZ(sz + dz * t);
                ParticleCaster.spawn(caster, world, Utils.WITCH, point, 0, 0.0, 0.0, 0.0, 0.0);
            }
        }

        ParticleCaster.spawn(caster, world, Utils.CLOUD, storm, SKY_CLOUD_COUNT,
                radius * SKY_CLOUD_SPREAD_H, height * SKY_CLOUD_SPREAD_V,
                radius * SKY_CLOUD_SPREAD_H, 0.03);
    }

    /**
     * The displacement, every tick, on everything caught under the storm that is not already
     * rising. The lift is skipped once a target is climbing ({@link #SKY_BUFF_CEILING}), so the
     * impulse cannot compound into a throw the target then dies falling off - the storm shoves,
     * it does not kill. This is what the storm is really doing; the bolt is what rides along
     * with it.
     */
    private void buffet(LivingEntity caster, World world, Location storm, double radius, double height) {
        for (LivingEntity le : Utils.getNearbyLivingEntities(storm, radius, height, radius)) {
            if (le.equals(caster) || le.isDead()) continue;
            Vector v = le.getVelocity();
            if (v.getY() >= SKY_BUFF_CEILING) continue;
            le.setVelocity(new Vector(
                    v.getX() + (Math.random() - 0.5) * SKY_BUFF_JITTER,
                    v.getY() + SKY_BUFF_LIFT,
                    v.getZ() + (Math.random() - 0.5) * SKY_BUFF_JITTER));
            ParticleCaster.spawn(caster, world, Utils.DUST, le.getLocation().add(0, 1.0, 0),
                    SKY_BUFF_DUST, SKY_DUST);
        }
    }

    /** Draws the bolt, then applies it, so the damage lands inside the flash. */
    private void bolt(LivingEntity caster, ItemStack wand, AttackProperties props, World world,
                      Location storm, LivingEntity struck, Map<UUID, Long> nextStrikeTick, int tick) {
        Location to = struck.getLocation().clone().add(0, 0.2, 0);
        // storm + (to - storm) * t, hoisted delta and one scratch Location: the same
        // arithmetic lerp would have done, without allocating per segment.
        double sx = storm.getX();
        double sy = storm.getY();
        double sz = storm.getZ();
        double dx = to.getX() - sx;
        double dy = to.getY() - sy;
        double dz = to.getZ() - sz;
        Location point = storm.clone();
        for (int i = 1; i <= SKY_BOLT_SEGMENTS; i++) {
            double t = i / (double) (SKY_BOLT_SEGMENTS + 1);
            point.setX(sx + dx * t);
            point.setY(sy + dy * t);
            point.setZ(sz + dz * t);
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, SKY_BOLT);
        }
        ParticleCaster.spawn(caster, world, Utils.EXPLOSION_EMITTER, to, 1);
        world.playSound(to, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.2f);

        Long next = nextStrikeTick.get(struck.getUniqueId());
        if (next == null || tick >= next) {
            nextStrikeTick.put(struck.getUniqueId(), (long) tick + strikeIntervalTicks());
            props.hit(struck);
            SpellDamageUtil.damageWithSpell(struck,
                    getPower(caster, struck, props.getRemainingPower()),
                    caster, wand, Skywrath.this);
            struck.setVelocity(new Vector(0.0, SKY_STRIKE_LIFT, 0.0));
        }
    }

    /**
     * The per-target damage gap, in ticks. This is the gate that makes
     * {@code hit_count} true, so it reads the yml's {@code strike_interval_seconds}
     * and never drops below spec 4.1's 20-tick floor.
     */
    private int strikeIntervalTicks() {
        return (int) Math.max(SpellAttackContext.DAMAGE_INTERVAL_TICKS, config.strikeIntervalTicks());
    }

    /**
     * The nearest living entity under the storm, skipping the caster and the dead.
     * The vertical range is zero so the bolt always reaches the ground under the
     * storm rather than only things floating at the storm's own height.
     */
    private LivingEntity nearestStrikeable(LivingEntity caster, Location storm, double radius) {
        LivingEntity nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (LivingEntity le : Utils.getNearbyLivingEntities(storm, radius, 0.0, radius)) {
            if (le.equals(caster) || le.isDead()) continue;
            double distanceSq = le.getLocation().distanceSquared(storm);
            if (distanceSq >= nearestSq) continue;
            nearestSq = distanceSq;
            nearest = le;
        }
        return nearest;
    }

    private void dissipate(LivingEntity caster, World world, double radius) {
        Location at = caster.getLocation();
        ParticleCaster.spawn(caster, world, Utils.EXPLOSION_EMITTER, at, SKY_DISPERSE_EMITTER);
        ParticleCaster.spawn(caster, world, Utils.SMOKE, at, SKY_DISPERSE_SMOKE,
                radius, 1.2, radius, 0.06);
        world.playSound(at, Sound.ENTITY_BLAZE_DEATH, 1.0f, 0.9f);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.EXPLOSION_EMITTER, location, 1);
        ParticleCaster.spawn(caster, location.getWorld(), Utils.DUST, location, 0, SKY_BOLT);
        location.getWorld().playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION_EMITTER, location, 1);
        ParticleCaster.spawn(null, location.getWorld(), Utils.SMOKE, location, 50, 1.0, 1.0, 1.0, 0.18);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.7f);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.skywrath.book_name"))
                .addCustomLoreLine(lang().get("spells.skywrath.lore1"))
                .addCustomLoreLine(lang().get("spells.skywrath.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }

    /**
     * The mutable state one live storm carries: its own tick count, the last global
     * strike, the per-target strike gate and the sustain's boss bar. Held on an
     * object rather than in locals because the storm's body and its teardown are two
     * different callbacks and must see the same clock.
     */
    private static final class StormClock {
        private final Map<UUID, Long> nextStrikeTick = new ConcurrentHashMap<>();
        private int t;
        private long lastStrikeTick = -SpellAttackContext.DAMAGE_INTERVAL_TICKS;
        private BossBar bar;
    }
}