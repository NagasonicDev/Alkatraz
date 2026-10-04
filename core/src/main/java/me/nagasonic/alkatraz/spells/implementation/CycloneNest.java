package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
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
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 7 AIR sustain: three small tornadoes set into the ground around the aim
 * point, each persisting independently and wandering on its own heading.
 *
 * <h2>Three nests, ONE budget</h2>
 * The yml declares {@code power: 9.0} and {@code hit_count: 8}, i.e.
 * {@code 9.0 x 8 = 72.0} against {@code sustainBudget(7)} of 76. That is the
 * per-TARGET ceiling for the whole cast, <b>not</b> three times over. Three
 * mechanisms in code hold it there:
 *
 * <ol>
 *   <li><b>One shared {@link AttackProperties}, one runnable.</b> All three nests
 *       are stepped by a single {@link BukkitRunnable}. {@code props} is created
 *       once in {@link #launch} and captured by it, so damage-contract rule 5 is
 *       satisfied structurally: countering the cast kills all three nests on the
 *       same tick and none of them can outlive the cast budget.</li>
 *   <li><b>One shared cadence clock.</b> A single {@code lastDamageTick} on that
 *       runnable opens the damage gate once every
 *       {@link SpellAttackContext#DAMAGE_INTERVAL_TICKS} ticks <em>for the entire
 *       cast</em>. A target standing in the overlap of all three nests takes one
 *       application per opening, not three.</li>
 *   <li><b>One shared per-target map.</b> {@code nextStrikeTick} is consulted
 *       across all three nests, so the cadence is per-target rather than
 *       per-nest.</li>
 * </ol>
 *
 * <p>{@code nest_count} therefore never touches a {@code damage} stat modifier in
 * any options file, at any level: three nests buy <em>area</em>, never damage.
 *
 * <h2>Why the cadence is NOT {@code props.hasHit}</h2>
 * {@link AttackProperties#hit} writes into a {@code Set<UUID>} that is never
 * cleared for the life of the cast. Gating a SUSTAIN field's <em>repeats</em> on
 * it would therefore allow one application per cast rather than one per 20 ticks,
 * and the field would land 9.0 over its whole life instead of 72.0 - silently
 * under-reporting a third of the budget it was designed to spend. So
 * {@code props.hit(le)} is still called, to satisfy rules 2 and 5 and to let
 * counters and barriers see the target as struck, while the repeat cadence is
 * owned by the explicit, spell-local, unit-testable {@code nextStrikeTick} map.
 *
 * <h2>Terrain is read, never written</h2>
 * AIR is forbidden from TERRAIN (spec 5.2). This class contains no
 * {@code TerrainEditor} call, no {@code setType(} and no {@code Material}
 * reference. It <em>reads</em> the block grid - {@code Utils.findTopSolid} to seat
 * a funnel, {@code Block.isPassable} to make a wandering nest reverse - because
 * deciding where a tornado may sit is not the same as reshaping the land. The
 * world is byte-identical after the cast.
 */
public class CycloneNest extends AttackSpell {

    /** Deep azure - much bluer than updraft's near-white ice and paler than tornado's. */
    private static final Color NEST_TINT = Color.fromRGB(90, 170, 255);
    private static final Color NEST_CORE = Color.fromRGB(30, 90, 200);
    private static final Particle.DustOptions NEST_DUST = new Particle.DustOptions(NEST_TINT, 0.6F);
    private static final Particle.DustOptions NEST_CORE_DUST = new Particle.DustOptions(NEST_CORE, 1.1F);

    /** Layers in the drawn funnel cone, throat to mouth. */
    private static final int FUNNEL_LAYERS = 8;
    /** Points on the bottom layer's ring; each layer above adds one. */
    private static final int FUNNEL_BASE_POINTS = 8;
    /** Radians the wander heading advances per tick - one turn every 30 ticks. */
    private static final double NEST_TURN_PER_TICK = 0.21;
    /** A nest reverses if it would step onto a wall. */
    private static final int NEST_WALL_LOOKAHEAD = 1;
    /** How far below a candidate position the ground is probed. */
    private static final int NEST_GROUND_PROBE = 6;
    /** Blocks above the nest the ground probe starts from. */
    private static final double NEST_GROUND_PROBE_LIFT = 3;
    /** A drop steeper than this reverses the nest instead of following it down. */
    private static final double NEST_MAX_DROP = 2.5;
    /** Inside this radius from the axis the pull direction is undefined, so it is skipped. */
    private static final double NEST_DEAD_CENTRE_SQUARED = 0.09;
    /** The edge of the funnel pulls at 10% of the axis pull. */
    private static final double NEST_EDGE_FALLOFF = 0.9;
    /** Vertical velocity written to a target on every buffeting tick. */
    private static final double NEST_LIFT_PER_TICK = 0.25;
    /** Floor on the vertical velocity a buffeted target is written to. */
    private static final double NEST_MIN_LIFT = 0.2;
    /** Funnel dust is drawn on every other ring point. */
    private static final int NEST_DUST_STRIDE = 2;
    /** Cloud points per funnel ring point. */
    private static final int NEST_CLOUD_COUNT = 1;
    /** Dispersion cloud count when a nest ends. */
    private static final int NEST_DISPERSE_COUNT = 14;
    /** How far a player aims before the nests are placed around the hit block. */
    private static final int NEST_AIM_RANGE = 24;
    /** Ground search depth used to seat a nest. */
    private static final int NEST_PLACEMENT_DEPTH = 20;

    private CycloneNestConfig config;

    public CycloneNest(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/cyclone_nest.yml");
        Alkatraz.getInstance().saveConfig("spells/cyclone_nest_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/cyclone_nest.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = CycloneNestConfig.from(spellConfig);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        Location aim = Utils.resolveTarget(caster, NEST_AIM_RANGE);
        if (aim.getWorld() == null) return;

        int nests = selectedNestCount(caster);
        List<Nest> seats = placeNests(aim, caster.getLocation(), nests,
                getModifiedStat(caster, "nest_spawn_spread", config.nestSpawnSpread()));
        if (seats.isEmpty()) {
            // No ground anywhere. Refund the mana rather than charging 240 for
            // three tornadoes that never appear.
            cancelCast(caster);
            return;
        }

        launch(caster, wand, getWandPower(wand, caster), seats,
                getModifiedStat(caster, "nest_radius", config.nestRadius()),
                getModifiedStat(caster, "nest_height", config.nestHeight()),
                getModifiedStat(caster, "nest_pull", config.nestPull()),
                getModifiedStat(caster, "nest_wander_speed", config.nestWanderSpeed()),
                config.lifetimeTicks());
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        if (caster.getWorld() == null) return;
        // Mobs get the plain config values - never a player's option.
        List<Nest> seats = placeNests(caster.getTarget().getLocation(), caster.getLocation(),
                config.nestCount(), config.nestSpawnSpread());
        if (seats.isEmpty()) return;
        launch(caster, wand, getWandPowerOrDefault(wand), seats,
                config.nestRadius(), config.nestHeight(), config.nestPull(),
                config.nestWanderSpeed(), config.lifetimeTicks());
    }

    /**
     * Places the nests DETERMINISTICALLY - never {@code Math.random} - at 0, 120
     * and 240 degrees around the aim point, each at half {@code spread} from the
     * centre. A deterministic cast is reproducible and reviewable; a random one
     * makes a bug report impossible to reason about.
     *
     * <p>A seat with no ground beneath it is skipped rather than aborting the
     * whole cast, so a nest hanging over a cliff still leaves two tornadoes.
     */
    private List<Nest> placeNests(Location anchor, Location fallbackAnchor, int nests, double spread) {
        List<Nest> seats = new ArrayList<>(nests);
        for (int i = 0; i < nests; i++) {
            double theta = 2 * Math.PI * i / nests;
            Location seat = anchor.clone().add(
                    Math.cos(theta) * spread * 0.5, 0.0, Math.sin(theta) * spread * 0.5);
            Location ground = Utils.findTopSolid(seat, NEST_PLACEMENT_DEPTH);
            if (ground == null) ground = Utils.findTopSolid(fallbackAnchor, NEST_PLACEMENT_DEPTH);
            if (ground == null) continue;
            seats.add(new Nest(ground.clone().add(0.5, 0.0, 0.5), theta));
        }
        return seats;
    }

    /**
     * The single runnable that steps every nest. Shared by both cast paths.
     *
     * @param wanderSpeed blocks per tick of drift around a nest's seat
     * @param lifetime   the whole field's life in ticks, which is also one nest's life
     */
    private void launch(LivingEntity caster, ItemStack wand, double wandPower, List<Nest> seats,
                        double radius, double height, double pull, double wanderSpeed, int lifetime) {
        final AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * wandPower, AttackType.MAGIC);
        final World world = caster.getWorld();
        if (world == null) return;

        final List<Nest> nests = new ArrayList<>(seats);
        final int life = Math.max(1, lifetime);

        new BukkitRunnable() {
            int t = 0;
            // THE SHARED CADENCE CLOCK. Opens once every DAMAGE_INTERVAL_TICKS for
            // the entire cast; all three nests consult it. Seeded at -20 so the
            // first opening is tick 0, giving exactly ceil(life / 20) windows.
            long lastDamageTick = -SpellAttackContext.DAMAGE_INTERVAL_TICKS;
            // THE SHARED PER-TARGET MAP, consulted across all three nests.
            final Map<UUID, Long> nextStrikeTick = new ConcurrentHashMap<>();

            @Override
            public void run() {
                if (props.isCountered() || props.isCancelled() || t >= life) {
                    disperseAll(caster, world, nests);
                    cancel();
                    return;
                }
                if (caster.isDead() || (caster instanceof Player player && !player.isOnline())) {
                    disperseAll(caster, world, nests);
                    cancel();
                    return;
                }

                boolean cadenceOpen = (t - lastDamageTick) >= SpellAttackContext.DAMAGE_INTERVAL_TICKS;
                if (cadenceOpen) lastDamageTick = t;

                for (Nest nest : nests) {
                    wander(nest, wanderSpeed);
                    drawFunnel(caster, world, nest, radius, height, t);
                    buff(caster, nest, radius, height, pull);
                    if (!cadenceOpen) continue;
                    strike(caster, wand, props, world, nest, radius, height, t, nextStrikeTick);
                }
                t++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * One nest's drift. The heading turns a fixed amount per tick and reverses on a
     * wall or a drop steeper than {@link #NEST_MAX_DROP}, so a nest paces its own
     * little circle instead of sliding off a cliff.
     */
    private void wander(Nest nest, double speed) {
        nest.moveAngle += NEST_TURN_PER_TICK;
        Location next = nest.origin.clone().add(
                Math.cos(nest.moveAngle) * speed, 0.0, Math.sin(nest.moveAngle) * speed);

        if (!next.getBlock().isPassable()
                || !next.clone().add(0, NEST_WALL_LOOKAHEAD, 0).getBlock().isPassable()) {
            nest.moveAngle += Math.PI;
            return;
        }
        Location below = Utils.findTopSolid(next.clone().add(0, NEST_GROUND_PROBE_LIFT, 0),
                NEST_GROUND_PROBE);
        if (below == null || Math.abs(below.getY() + 1 - next.getY()) > NEST_MAX_DROP) {
            nest.moveAngle += Math.PI;
            return;
        }
        nest.origin = next;
    }

    /**
     * The visible funnel: an eight-layer cone that widens from 35% of the radius at
     * the throat to 115% at the mouth, every point jittered so it never reads as a
     * clean ring. Visual only.
     */
    private void drawFunnel(LivingEntity caster, World world, Nest nest,
                            double radius, double height, int tick) {
        for (int layer = 0; layer < FUNNEL_LAYERS; layer++) {
            double tt = layer / (double) (FUNNEL_LAYERS - 1);
            double y = tt * height;
            double r = config.funnelRadiusAt(tt);
            int points = FUNNEL_BASE_POINTS + layer;
            double base = tick * 0.4 + tt * 2.0;
            for (int i = 0; i < points; i++) {
                double angle = base + 2 * Math.PI * i / points;
                double variance = 0.85 + Math.random() * 0.3;
                Location point = nest.origin.clone().add(
                        Math.cos(angle) * r * variance, y, Math.sin(angle) * r * variance);
                ParticleCaster.spawn(caster, world, Utils.CLOUD, point,
                        NEST_CLOUD_COUNT, 0.05, 0.05, 0.05, 0.01);
                if (i % NEST_DUST_STRIDE == 0) {
                    ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, NEST_DUST);
                }
            }
        }
        // The funnel throat, so the cone has one dark centre to read from.
        ParticleCaster.spawn(caster, world, Utils.DUST,
                nest.origin.clone().add(0, height * 0.5, 0), 0, NEST_CORE_DUST);
    }

    /**
     * The displacement pillar: every tick, unconditionally, no damage. This runs on
     * all {@code life} ticks and is why a nest is worth casting even against a
     * target that never takes a second hit. Pull falls off linearly toward the
     * funnel's edge, and every target is nudged upward so it climbs the throat.
     */
    private void buff(LivingEntity caster, Nest nest, double radius, double height, double pull) {
        for (LivingEntity le : Utils.getNearbyLivingEntities(nest.origin, radius, height, radius)) {
            if (le.equals(caster) || le.isDead()) continue;

            Vector inward = nest.origin.toVector().subtract(le.getLocation().toVector());
            inward.setY(0.0);
            if (inward.lengthSquared() < NEST_DEAD_CENTRE_SQUARED) continue;
            double falloff = 1.0 - Math.min(inward.length() / radius, NEST_EDGE_FALLOFF);
            Vector drift = Utils.safeNormalize(inward).multiply(pull * falloff);

            Vector velocity = le.getVelocity();
            le.setVelocity(velocity.add(drift)
                    .setY(Math.max(velocity.getY() + NEST_LIFT_PER_TICK * falloff, NEST_MIN_LIFT)));
        }
    }

/**
     * One nest's damage pass, gated by BOTH the shared cadence clock (the caller
     * only gets here on an opening) and the shared per-target map. Either gate alone
     * would be wrong: the clock alone would let a target standing in a three-nest
     * overlap take three applications on the same opening, and the map alone would
     * let damage land every tick.
     *
     * @param nextStrikeTick the ONE per-cast map, shared across all three nests
     */
    private void strike(LivingEntity caster, ItemStack wand, AttackProperties props, World world,
                        Nest nest, double radius, double height, int tick,
                        Map<UUID, Long> nextStrikeTick) {
        for (LivingEntity le : Utils.getNearbyLivingEntities(nest.origin, radius, height, radius)) {
            if (le.equals(caster) || le.isDead()) continue;
            Long next = nextStrikeTick.get(le.getUniqueId());
            if (next != null && tick < next) continue;
            nextStrikeTick.put(le.getUniqueId(), (long) tick + SpellAttackContext.DAMAGE_INTERVAL_TICKS);
            props.hit(le);
            SpellDamageUtil.damageWithSpell(le,
                    getPower(caster, le, props.getRemainingPower()),
                    caster, wand, CycloneNest.this);
            ParticleCaster.spawn(caster, world, Utils.EXPLOSION_EMITTER,
                    le.getLocation().add(0, 1.0, 0), 1);
        }
    }

    private void disperseAll(LivingEntity caster, World world, List<Nest> nests) {
        for (Nest nest : nests) {
            ParticleCaster.spawn(caster, world, Utils.CLOUD, nest.origin,
                    NEST_DISPERSE_COUNT, 0.4, 0.7, 0.4, 0.04);
            ParticleCaster.spawn(caster, world, Utils.DUST, nest.origin, 0, NEST_CORE_DUST);
        }
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.CLOUD, location, 18);
        ParticleCaster.spawn(caster, location.getWorld(), Utils.DUST, location, 0, NEST_CORE_DUST);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION_EMITTER, location, 1);
        ParticleCaster.spawn(null, location.getWorld(), Utils.CLOUD, location, 40, 0.5, 0.8, 0.5, 0.06);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.cyclonenest.book_name"))
                .addCustomLoreLine(lang().get("spells.cyclonenest.lore1"))
                .addCustomLoreLine(lang().get("spells.cyclonenest.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }

    /**
     * How many nests this player has selected. The option carries no
     * {@code damage} stat modifier by design - see the class Javadoc - so its
     * value is read straight off the selection rather than through
     * {@code getModifiedStat}.
     */
    private int selectedNestCount(Player caster) {
        SpellOption option = getOption("nests");
        if (option != null) {
            OptionValue<?> value = option.getSelectedValue(caster);
            if (value != null && value.getValue() instanceof Number number) {
                return Math.max(1, number.intValue());
            }
        }
        return config.nestCount();
    }

    /**
     * One wandering funnel. Deliberately a small mutable class rather than a
     * record: the heading and the position both change every tick, and the state
     * lives only inside the one runnable's captured list, so there is no static
     * field to leak between casts.
     */
    private static final class Nest {

        private Location origin;
        private double moveAngle;

        private Nest(Location origin, double moveAngle) {
            this.origin = origin;
            this.moveAngle = moveAngle;
        }
    }
}