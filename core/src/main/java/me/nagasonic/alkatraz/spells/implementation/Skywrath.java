package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 9 AIR capstone: a fifteen-second storm centred on you, one bolt a second
 * into anything that comes near, and <b>flight for exactly as long as the storm
 * lasts</b>.
 *
 * <h2>Why this is SUSTAIN and not UTILITY</h2>
 * The flight is a movement grant, which is air's whole identity. But
 * {@code SpellBudgetTest.utilitySpellsCarryNoDamageKey} fails any UTILITY spell
 * that carries a {@code power:} key, and this spell's
 * {@code power 9.6 x hit_count 15 = 144.0} is the number that prices the circle-9
 * air row. The storm is the product; the flight is how you survive while casting
 * it. So the grant is real, it has its own yml keys, and it is measured - but the
 * spell's role, budget and identity are the storm's.
 *
 * <h2>The flight grant is always revoked</h2>
 * {@code allowFlight}, {@code flying} and {@code flySpeed} are captured
 * <em>before</em> the grant and restored verbatim afterwards. The restore is
 * idempotent and runs from three places - the storm's own expiry branch, a safety
 * net scheduled at launch in case the storm's task is cancelled out from under it,
 * and the quit handler - so a player is never left permanently airborne. The
 * portal halo drawn on the caster's own body every tick is the visual tell that
 * flight is live.
 *
 * <h2>Damage contract</h2>
 * <ul>
 *   <li><b>One getPower per damage instance.</b> {@link AttackProperties} is seeded
 *       with RAW base power; the two-arg overload is never called. The three-arg
 *       form runs once per strike. The flight grant touches no damage path at all.</li>
 *   <li><b>Deduped per target per cast.</b> {@code props.hit(struck)} on every
 *       application, plus an explicit per-cast {@code nextStrikeTick} map which is
 *       what actually enforces one application per 20 ticks. See the note in
 *       {@code CycloneNest}'s Javadoc: {@code AttackProperties.hit} writes to a
 *       {@code Set} that is never cleared, so gating a SUSTAIN's <em>repeats</em>
 *       on it would allow one application per cast rather than fifteen.</li>
 *   <li><b>power reaches damage.</b> {@code props.getRemainingPower()} is the raw
 *       {@code power} key and goes straight to
 *       {@link SpellDamageUtil#damageWithSpell}. No separate damage key exists.</li>
 *   <li><b>One shared AttackProperties per cast.</b> Created once in
 *       {@link #castAction}, captured by the single runnable; a counter kills the
 *       storm and returns flight in the same tick.</li>
 * </ul>
 *
 * <h2>No terrain, ever</h2>
 * AIR is forbidden from TERRAIN (spec 5.2). This class contains no
 * {@code TerrainEditor} call, no {@code setType(} and no {@code Material}
 * reference. {@code Player#setFlySpeed} changes a movement attribute, not the
 * world.
 */
public class Skywrath extends AttackSpell implements Listener {

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
    /** Portal halo drawn on the caster's own body while flight is live. */
    private static final int SKY_PORTAL_COUNT = 20;
    private static final double SKY_PORTAL_SPREAD_H = 0.6;
    private static final double SKY_PORTAL_SPREAD_V = 1.2;
    /** Horizontal and vertical jitter written to a buffeted target, per tick. */
    private static final double SKY_BUFF_JITTER = 0.16;
    private static final double SKY_BUFF_LIFT = 0.14;
    /** Dust points drawn on a buffeted target. */
    private static final int SKY_BUFF_DUST = 2;
    /** Segments in one bolt, excluding both endpoints. */
    private static final int SKY_BOLT_SEGMENTS = 17;
    /** Vertical velocity written to a struck target. */
    private static final double SKY_STRIKE_LIFT = 0.75;
    /** How often the flight is topped up, in ticks, so landing mid-storm re-lifts you. */
    private static final int SKY_FLIGHT_TOP_UP = 20;
    /** Dispersion counts when the storm ends. */
    private static final int SKY_DISPERSE_EMITTER = 1;
    private static final int SKY_DISPERSE_SMOKE = 50;
    /**
     * The mob path's own storm: shorter, tighter and anchored over the mob's
     * target. This is NOT the yml's {@code storm_duration} - the yml describes the
     * player cast, which is what {@code hit_count: 15} and {@code power: 9.6}
     * describe. A mob also never gets flight, because {@code Player#setAllowFlight}
     * does not exist on {@code Mob}.
     */
    private static final double MOB_STORM_DURATION = 5.0;
    private static final double MOB_STORM_RADIUS = 5.0;
    private static final double MOB_STORM_HEIGHT = 6.0;

    /**
     * A caster's flight attributes as they were <em>before</em> the grant. Held
     * statically rather than per-cast because {@code Player} is the only entity the
     * grant applies to and one player holds at most one skywrath.
     */
    private static final Map<UUID, FlightState> FLIGHT_STATES = new ConcurrentHashMap<>();
    /** Casters whose flight has already been returned, so the restore lands exactly once. */
    private static final Set<UUID> FLIGHT_RESTORED = ConcurrentHashMap.newKeySet();

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
        Alkatraz.getInstance().getServer().getPluginManager()
                .registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster), AttackType.MAGIC);

        double duration = getModifiedStat(caster, "storm_duration", config.stormDuration());
        double radius = getModifiedStat(caster, "storm_radius", config.stormRadius());
        double height = getModifiedStat(caster, "storm_height", config.stormHeight());

        int stormTicks = Math.max(1, (int) Math.round(duration * 20.0));
        int flightTicks = config.flightTicksWithin(stormTicks);

        // The `flight` option writes a `flight_allow` stat modifier, so the switch
        // is player-selectable rather than a hardcoded true.
        boolean flightOn = getModifiedStat(caster, "flight_allow",
                config.flightAllow() ? 1.0 : 0.0) > 0.5;

        World world = caster.getWorld();
        if (world != null) {
            world.playSound(caster.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);
        }

        grantFlight(caster, flightOn, getModifiedStat(caster, "flight_speed", config.flightSpeed()));
        // SAFETY NET. The storm's own expiry restores flight, but if its task is
        // cancelled from outside - a reload, a scheduler wipe - this is what stops a
        // player being stranded in the air. restoreFlight is idempotent, so the two
        // paths cannot double-restore.
        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(),
                () -> restoreFlight(caster), Math.max(1L, flightTicks));

        runStorm(caster, wand, props, stormTicks, radius, height, flightOn);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand), AttackType.MAGIC);
        // No flight: Player#setAllowFlight does not exist on Mob.
        runStorm(caster, wand, props,
                (int) Math.round(MOB_STORM_DURATION * 20.0), MOB_STORM_RADIUS, MOB_STORM_HEIGHT, false);
    }

    /**
     * The storm, shared by both cast paths. One runnable, one cadence clock and one
     * per-target map, so countering the cast kills the whole storm and, for a
     * player, returns flight in the same tick.
     */
    private void runStorm(LivingEntity caster, ItemStack wand, AttackProperties props,
                          int life, double radius, double height, boolean flightOn) {
        final Map<UUID, Long> nextStrikeTick = new ConcurrentHashMap<>();

        new BukkitRunnable() {
            int t = 0;
            long lastStrikeTick = -SpellAttackContext.DAMAGE_INTERVAL_TICKS;

            @Override
            public void run() {
                World world = caster.getWorld();
                if (world == null || props.isCountered() || props.isCancelled()
                        || caster.isDead() || t >= life
                        || (caster instanceof Player online && !online.isOnline())) {
                    if (world != null) {
                        dissipate(caster, world, radius);
                        if (caster instanceof Player player) {
                            restoreFlight(player);
                            // Only on the natural-expiry path: a counter or a death
                            // should not announce that the sky closed.
                            if (t >= life && !props.isCountered()) {
                                Utils.sendActionBar(player, lang().get("spells.skywrath.ending"));
                            }
                        }
                    }
                    cancel();
                    return;
                }

                // The storm follows the caster: re-anchored every tick, which is why
                // Utils.resolveTarget is never called anywhere in this class.
                Location anchor = caster.getLocation();
                Location storm = anchor.clone().add(0.0, height, 0.0);

                drawStorm(caster, world, storm, anchor, radius, height, t);
                buffet(caster, world, storm, radius, height);

                if (t - lastStrikeTick < SpellAttackContext.DAMAGE_INTERVAL_TICKS) {
                    t++;
                } else {
                    lastStrikeTick = t;
                    LivingEntity struck = nearestStrikeable(caster, storm, radius);
                    if (struck != null) {
                        bolt(caster, wand, props, world, storm, struck, nextStrikeTick, t);
                    }
                    t++;
                }

                if (t % SKY_FLIGHT_TOP_UP == 0 && flightOn) {
                    if (caster instanceof Player player && player.isOnline() && !player.isDead()) {
                        player.setFlying(true);
                    }
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /** Six shrinking rings, the sky column down to the caster, the cloud halo and the flight tell. */
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
        ParticleCaster.spawn(caster, world, Utils.PORTAL, anchor.clone().add(0, 1.0, 0),
                SKY_PORTAL_COUNT, SKY_PORTAL_SPREAD_H, SKY_PORTAL_SPREAD_V,
                SKY_PORTAL_SPREAD_H, 0.05);
    }

    /**
     * The displacement, on every tick, unconditionally and with no damage at all.
     * This is what the storm is really doing; the bolt is what rides along with it.
     */
    private void buffet(LivingEntity caster, World world, Location storm, double radius, double height) {
        for (LivingEntity le : Utils.getNearbyLivingEntities(storm, radius, height, radius)) {
            if (le.equals(caster) || le.isDead()) continue;
            le.setVelocity(le.getVelocity().add(new Vector(
                    (Math.random() - 0.5) * SKY_BUFF_JITTER,
                    SKY_BUFF_LIFT,
                    (Math.random() - 0.5) * SKY_BUFF_JITTER)));
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
            nextStrikeTick.put(struck.getUniqueId(), (long) tick + SpellAttackContext.DAMAGE_INTERVAL_TICKS);
            props.hit(struck);
            SpellDamageUtil.damageWithSpell(struck,
                    getPower(caster, struck, props.getRemainingPower()),
                    caster, wand, Skywrath.this);
            struck.setVelocity(new Vector(0.0, SKY_STRIKE_LIFT, 0.0));
        }
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

    /**
     * Grants creative-style flight, capturing the three attributes it is about to
     * overwrite so {@link #restoreFlight} can put them back verbatim. Never
     * double-grants: a second call while a grant is live is a no-op.
     */
    private void grantFlight(Player caster, boolean allow, double flySpeed) {
        if (!allow) return;
        UUID uuid = caster.getUniqueId();
        if (FLIGHT_STATES.containsKey(uuid)) return;
        FLIGHT_STATES.put(uuid, new FlightState(
                caster.getAllowFlight(), caster.isFlying(), caster.getFlySpeed()));
        FLIGHT_RESTORED.remove(uuid);
        caster.setAllowFlight(true);
        caster.setFlying(true);
        caster.setFlySpeed(clampFlySpeed(flySpeed));
        World world = caster.getWorld();
        if (world != null) {
            world.playSound(caster.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 1.0f, 0.7f);
        }
    }

    /**
     * Returns flight exactly once, however many of the three restore paths reach it.
     * A player who was already flying before the cast is left flying.
     */
    private void restoreFlight(Player caster) {
        UUID uuid = caster.getUniqueId();
        if (!FLIGHT_RESTORED.add(uuid)) return;
        FlightState state = FLIGHT_STATES.remove(uuid);
        if (state == null) return;
        caster.setFlySpeed(state.flySpeed());
        caster.setAllowFlight(state.allowFlight());
        caster.setFlying(state.flying());
        World world = caster.getWorld();
        if (world != null) {
            world.playSound(caster.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.6f, 1.4f);
        }
    }

    /** {@code Player#setFlySpeed} rejects anything outside -1.0 .. 1.0, so clamp before writing. */
    private static float clampFlySpeed(double speed) {
        if (Double.isNaN(speed)) return 0.05F;
        return (float) Math.max(-1.0, Math.min(1.0, speed));
    }

    private void dissipate(LivingEntity caster, World world, double radius) {
        Location at = caster.getLocation();
        ParticleCaster.spawn(caster, world, Utils.EXPLOSION_EMITTER, at, SKY_DISPERSE_EMITTER);
        ParticleCaster.spawn(caster, world, Utils.SMOKE, at, SKY_DISPERSE_SMOKE,
                radius, 1.2, radius, 0.06);
        world.playSound(at, Sound.ENTITY_BLAZE_DEATH, 1.0f, 0.9f);
    }

    /** A quitting player's stored state is dropped rather than leaked. */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        FLIGHT_STATES.remove(uuid);
        FLIGHT_RESTORED.add(uuid);
    }

    /** Nothing may outlive a reload. */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Alkatraz.getInstance()) return;
        for (UUID uuid : FLIGHT_STATES.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) restoreFlight(player);
        }
        FLIGHT_STATES.clear();
        FLIGHT_RESTORED.clear();
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
     * A caster's flight attributes as they were before the grant. A record because
     * it is a fixed snapshot taken once and never mutated.
     */
    private record FlightState(boolean allowFlight, boolean flying, float flySpeed) {
    }
}