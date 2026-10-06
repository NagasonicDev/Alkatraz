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
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ChannelRegistry;
import me.nagasonic.alkatraz.spells.vfx.WindUp;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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
 * Circle 8 AIR sustain: you hold still for a moment and a personal storm condenses
 * over your head and <em>follows you</em> for twelve seconds, striking anything
 * that comes into its column exactly once a second.
 *
 * <h2>The storm tracks the caster, so it is never placed</h2>
 * The storm's anchor is recomputed from the caster's own location every tick.
 * That is why {@code Utils.resolveTarget} is deliberately never called here: this
 * is air's mobility pillar, not a placed field, so there is nowhere for it to sit
 * and nothing for it to read out of the terrain.
 *
 * <h2>The channel uses the shared wind-up, not a private one</h2>
 * The interruption triple - move too far, take damage, quit - lives in
 * {@link ChannelRegistry} and the escalating charge in {@link WindUp}, which are
 * what {@code warp} and {@code skeletal_knights} already share. A private
 * {@code Map<UUID, ChannelData>} plus three {@code @EventHandler}s would be a
 * fourth copy of code that exists precisely to be written once.
 * {@code move_cancel_threshold} is read from this spell's own yml by
 * {@link WindUp#moveCancelThreshold}, so the caster may shuffle but not run.
 *
 * <h2>Damage contract</h2>
 * <ul>
 *   <li><b>One getPower per damage instance.</b> {@link AttackProperties} is seeded
 *       with RAW base power <em>before</em> the channel, so a counter during the
 *       channel cancels the whole storm. The two-arg {@code getPower} overload is
 *       never called; the three-arg form runs once per strike.</li>
 *   <li><b>Deduped per target per cast.</b> {@code props.hit(struck)} on every
 *       application, plus an explicit per-cast {@code nextStrikeTick} map which is
 *       what actually enforces one application per 20 ticks. See the note in
 *       {@code CycloneNest}'s Javadoc: {@code AttackProperties.hit} writes to a
 *       {@code Set} that is never cleared, so gating a SUSTAIN's <em>repeats</em>
 *       on it would allow one application per cast rather than twelve.</li>
 *   <li><b>power reaches damage.</b> {@code props.getRemainingPower()} is the raw
 *       {@code power} key and goes straight to
 *       {@link SpellDamageUtil#damageWithSpell}. No separate damage key exists.</li>
 *   <li><b>One shared AttackProperties per cast,</b> created once and carried
 *       through the channel into the storm.</li>
 * </ul>
 *
 * <h2>No terrain, ever</h2>
 * AIR is forbidden from TERRAIN (spec 5.2). This class contains no
 * {@code TerrainEditor} call, no {@code setType(} and no {@code Material}
 * reference. The buffeting writes velocity; nothing else in the world changes.
 */
public class Stormcaller extends AttackSpell {

    /**
     * This spell's colour, handed to the wind-up telegraph that precedes its payload.
     *
     * <p>Seeded with the `AIR` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the charge-up.
     * <p>Matches its BOLT_TINT, so the charge and the lightning read as one thing.
     */
    private static final Color CAST_COLOR = Color.fromRGB(225, 255, 245);

    /** Mint green - the only green in the air element, distinct from azure and violet. */
    private static final Color STORM_TINT = Color.fromRGB(160, 255, 210);
    private static final Color BOLT_TINT = Color.fromRGB(225, 255, 245);
    private static final Particle.DustOptions STORM_DUST = new Particle.DustOptions(STORM_TINT, 0.9F);
    private static final Particle.DustOptions STORM_BOLT = new Particle.DustOptions(BOLT_TINT, 1.4F);

    /** Rings drawn per tick at the storm. */
    private static final int STORM_LAYERS = 4;
    /** Points per ring. */
    private static final int STORM_RING_POINTS = 16;
    /** How much each successive ring shrinks, as a fraction of the radius. */
    private static final double STORM_LAYER_SHRINK = 0.3;
    /** Vertical drop between successive rings, in blocks. */
    private static final double STORM_LAYER_DROP = 0.8;
    /** How fast the rings spin, in radians per tick. */
    private static final double STORM_SPIN = 0.3;
    /** The tether column between caster and storm is drawn every this many ticks. */
    private static final int STORM_TETHER_EVERY = 2;
    /** Points on the tether column. */
    private static final int STORM_TETHER_POINTS = 12;
    /** Cloud body count and its spread multipliers. */
    private static final int STORM_CLOUD_COUNT = 24;
    private static final double STORM_CLOUD_SPREAD_H = 0.5;
    private static final double STORM_CLOUD_SPREAD_V = 0.15;
    /** Horizontal and vertical jitter written to a buffeted target, per tick. */
    private static final double STORM_BUFF_JITTER = 0.12;
    private static final double STORM_BUFF_LIFT = 0.10;
    /** Dust points drawn on a buffeted target. */
    private static final int STORM_BUFF_DUST = 2;
    /** Points along one bolt, excluding both endpoints. */
    private static final int STORM_BOLT_SEGMENTS = 13;
    /** Vertical velocity written to a struck target. */
    private static final double STORM_STRIKE_LIFT = 0.6;
    /** Dispersion counts when the storm ends. */
    private static final int STORM_DISPERSE_EMITTER = 1;
    private static final int STORM_DISPERSE_SMOKE = 30;
    /**
     * The mob path's own storm: shorter, tighter and anchored over the mob's
     * target rather than over the mob. This is NOT the yml's {@code storm_duration}
     * - the yml describes the player cast, which is what {@code hit_count: 12} and
     * {@code power: 8.0} describe. A mob therefore deals at most 6 applications of
     * a mob-scaled power, which is not part of the declared player budget.
     */
    private static final double MOB_STORM_DURATION = 6.0;
    private static final double MOB_STORM_RADIUS = 4.5;
    private static final double MOB_STORM_HEIGHT = 6.0;

    private StormcallerConfig config;

    public Stormcaller(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/stormcaller.yml");
        Alkatraz.getInstance().saveConfig("spells/stormcaller_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/stormcaller.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = StormcallerConfig.from(spellConfig);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        UUID uuid = caster.getUniqueId();
        // One caster holds at most one wind-up; a second cast while the first is
        // still converging is refused rather than stacking two storms.
        if (ChannelRegistry.isChanneling(uuid)) return;
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        if (profile == null) return;

        double channelSeconds = getModifiedStat(caster, "channel_duration", config.channelDuration());
        int channelTicks = StormcallerConfig.channelTicksOf(channelSeconds,
                profile.getSpellMastery(this), getMaxMastery());

        final AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster), AttackType.MAGIC);
        final WindUp windUp = WindUp.begin(this, CAST_COLOR, caster, channelTicks);

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    return;
                }
                // WindUp.tick() draws the escalating charge and advances its clock;
                // it never fires the payload itself. isComplete() flips exactly on
                // the final tick, and cancel() has already run if it was interrupted.
                windUp.tick();
                if (windUp.isCancelled() || windUp.isComplete()) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    if (windUp.isComplete()) launchStorm(caster, wand, props,
                            getModifiedStat(caster, "storm_duration", config.stormDuration()),
                            getModifiedStat(caster, "storm_radius", config.stormRadius()),
                            getModifiedStat(caster, "storm_height", config.stormHeight()));
                    return;
                }
            }
        };

        ChannelRegistry.register(uuid, windUp, runnable::cancel);
        runnable.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand), AttackType.MAGIC);
        launchStorm(caster, wand, props, MOB_STORM_DURATION, MOB_STORM_RADIUS, MOB_STORM_HEIGHT);
    }

    /**
     * The storm itself, shared by both cast paths. One runnable, one cadence clock
     * and one per-target map, so countering the cast kills the whole storm.
     */
    private void launchStorm(LivingEntity caster, ItemStack wand, AttackProperties props,
                             double duration, double radius, double height) {
        final int life = Math.max(1, (int) Math.round(duration * 20.0));
        final Map<UUID, Long> nextStrikeTick = new ConcurrentHashMap<>();

        World soundWorld = caster.getWorld();
        if (soundWorld != null) {
            soundWorld.playSound(caster.getEyeLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.4f);
        }

        new BukkitRunnable() {
            int t = 0;
            long lastStrikeTick = -SpellAttackContext.DAMAGE_INTERVAL_TICKS;

            @Override
            public void run() {
                World world = caster.getWorld();
                if (world == null || props.isCountered() || props.isCancelled()
                        || caster.isDead() || t >= life
                        || (caster instanceof Player online && !online.isOnline())) {
                    if (world != null) dissipate(caster, world, radius);
                    cancel();
                    return;
                }

                // The storm follows the caster: re-anchored every tick.
                Location anchor = caster.getLocation();
                Location storm = anchor.clone().add(0.0, height, 0.0);

                drawStorm(caster, world, storm, anchor, radius, height, t);
                buffet(caster, world, storm, radius, height);

                if (t - lastStrikeTick < SpellAttackContext.DAMAGE_INTERVAL_TICKS) {
                    t++;
                    return;
                }
                lastStrikeTick = t;
                LivingEntity struck = nearestStrikeable(caster, storm, radius);
                if (struck != null) {
                    bolt(caster, wand, props, world, storm, struck, nextStrikeTick, t);
                }
                t++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /** Four shrinking rings, the tether down to the caster, and the cloud body. */
    private void drawStorm(LivingEntity caster, World world, Location storm, Location anchor,
                           double radius, double height, int tick) {
        for (int layer = 0; layer < STORM_LAYERS; layer++) {
            double r = radius * (1.4 - STORM_LAYER_SHRINK * layer);
            for (int i = 0; i < STORM_RING_POINTS; i++) {
                double angle = 2 * Math.PI * i / STORM_RING_POINTS + tick * STORM_SPIN + layer * 0.9;
                Location point = storm.clone().add(
                        Math.cos(angle) * r, -layer * STORM_LAYER_DROP, Math.sin(angle) * r);
                ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, STORM_DUST);
            }
        }

        if (tick % STORM_TETHER_EVERY == 0) {
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
            for (int i = 0; i < STORM_TETHER_POINTS; i++) {
                double t = (i + 1) / (double) STORM_TETHER_POINTS;
                point.setX(sx + dx * t);
                point.setY(sy + dy * t);
                point.setZ(sz + dz * t);
                ParticleCaster.spawn(caster, world, Utils.WITCH, point, 0, 0.0, 0.0, 0.0, 0.0);
            }
        }

        ParticleCaster.spawn(caster, world, Utils.CLOUD, storm, STORM_CLOUD_COUNT,
                radius * STORM_CLOUD_SPREAD_H, height * STORM_CLOUD_SPREAD_V,
                radius * STORM_CLOUD_SPREAD_H, 0.02);
    }

    /**
     * The displacement, on every tick, unconditionally and with no damage at all.
     * This is what the storm is really doing; the bolt is what rides along with it.
     * Removing the buffet would gut the spell without touching {@code power}.
     */
    private void buffet(LivingEntity caster, World world, Location storm, double radius, double height) {
        for (LivingEntity le : Utils.getNearbyLivingEntities(storm, radius, height, radius)) {
            if (le.equals(caster) || le.isDead()) continue;
            le.setVelocity(le.getVelocity().add(new Vector(
                    (Math.random() - 0.5) * STORM_BUFF_JITTER,
                    STORM_BUFF_LIFT,
                    (Math.random() - 0.5) * STORM_BUFF_JITTER)));
            ParticleCaster.spawn(caster, world, Utils.DUST, le.getLocation().add(0, 1.0, 0),
                    STORM_BUFF_DUST, STORM_DUST);
        }
    }

    /**
     * Draws the bolt, then applies it. The bolt is drawn first so the damage lands
     * inside the flash rather than a tick after it.
     */
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
        for (int i = 1; i <= STORM_BOLT_SEGMENTS; i++) {
            double t = i / (double) (STORM_BOLT_SEGMENTS + 1);
            point.setX(sx + dx * t);
            point.setY(sy + dy * t);
            point.setZ(sz + dz * t);
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, STORM_BOLT);
        }
        ParticleCaster.spawn(caster, world, Utils.EXPLOSION_EMITTER, to, 1);
        world.playSound(to, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f, 1.3f);

        Long next = nextStrikeTick.get(struck.getUniqueId());
        if (next == null || tick >= next) {
            nextStrikeTick.put(struck.getUniqueId(), (long) tick + SpellAttackContext.DAMAGE_INTERVAL_TICKS);
            props.hit(struck);
            SpellDamageUtil.damageWithSpell(struck,
                    getPower(caster, struck, props.getRemainingPower()),
                    caster, wand, Stormcaller.this);
            // The strike lifts what it hits - air moves things, that is the element.
            struck.setVelocity(new Vector(0.0, STORM_STRIKE_LIFT, 0.0));
        }
    }

    /**
     * The nearest living entity under the storm, skipping the caster and the dead.
     * The vertical range is zero so the bolt always reaches the ground the caster
     * is standing on rather than only things floating at the storm's own height.
     *
     * @param caster the storm's owner, which is never its own first target
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
        ParticleCaster.spawn(caster, world, Utils.EXPLOSION_EMITTER, at, STORM_DISPERSE_EMITTER);
        ParticleCaster.spawn(caster, world, Utils.SMOKE, at, STORM_DISPERSE_SMOKE,
                radius, 0.8, radius, 0.05);
        world.playSound(at, Sound.ENTITY_BLAZE_DEATH, 0.9f, 1.2f);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.EXPLOSION_EMITTER, location, 1);
        ParticleCaster.spawn(caster, location.getWorld(), Utils.DUST, location, 0, STORM_BOLT);
        location.getWorld().playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.7f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION_EMITTER, location, 1);
        ParticleCaster.spawn(null, location.getWorld(), Utils.SMOKE, location, 40, 0.8, 0.8, 0.8, 0.15);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.8f);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.stormcaller.book_name"))
                .addCustomLoreLine(lang().get("spells.stormcaller.lore1"))
                .addCustomLoreLine(lang().get("spells.stormcaller.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}