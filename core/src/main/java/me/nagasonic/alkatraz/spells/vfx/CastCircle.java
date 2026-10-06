package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one cast-circle animation shared by every spell, replacing the 36 hand-rolled copies of the
 * same {@code scheduleSyncRepeatingTask} block, each of which rebuilt a {@code Color} and a
 * {@link Particle.DustOptions} on every tick for every casting player.
 *
 * <p><b>Cancellation now stops the animation.</b> Every one of the old implementations opened its
 * tick with {@code if (e.isCancelled()) return;}, which skipped a single repaint and left the
 * repeating task scheduled for the rest of the server's uptime - holding a strong reference to the
 * caster and the spell forever. This implementation cancels the task itself on the first cancelled
 * tick. The completion task in {@code Spell} still cancels it after a normal cast, so both exits
 * are covered.
 *
 * <p><b>Geometry: hoisted where it is safe, recomputed where it is not.</b> The ring layout depends
 * only on the circle size and the rotation step, so those un-rotated offsets are built once per
 * distinct size and shared read-only ({@link #GEOMETRY}). The frame they are placed in is not
 * hoistable: the circle is re-centred on {@code caster.getEyeLocation()} with the caster's live yaw
 * and pitch every tick, so no world {@link Location} is ever cached - a cached one would be stale
 * the instant the caster turns or steps. Rotating an offset needs no {@code Location} of its own, so
 * the draw loop mutates one scratch {@link Location} instead of allocating a clone per particle.
 *
 * <p>The layout is identical to {@link me.nagasonic.alkatraz.util.ParticleUtils#magicCircle}, offset
 * for offset and in the same order; {@code CastCircleGeometryTest} asserts that equivalence so the
 * visual cannot drift here.
 *
 * <p><b>Colours</b> come from {@link ElementPalette}: {@code core} for the ring body, {@code deep}
 * for the decorative sphere clusters. Both are pre-built {@code static final} instances, so a tick
 * allocates no colour objects at all.
 *
 * <p><b>Verification.</b> The scheduling path is covered by in-game verification, not by unit tests:
 * exercising it would mean mocking the {@code Bukkit} and {@code Alkatraz} statics for no real
 * confidence. {@code CastCircleGeometryTest} covers the pure geometry instead.
 */
public final class CastCircle {

    /** Circle size used when a spell does not ask for one; matches the previous default. */
    private static final double DEFAULT_SIZE = 3.0;
    /** Rotation step in degrees used when a spell does not ask for one. */
    private static final int DEFAULT_ROTATION_STEP = 0;
    /** How far in front of the caster's eyes the circle is drawn. */
    private static final double FORWARD_OFFSET = 1.5;
    /** Sizes below this are meaningless as a circle scale, so they fall back to the default. */
    private static final double MIN_SIZE = 0.05;
    /** Repaint period used when {@code circle_ticks} is missing or not a number. */
    private static final long DEFAULT_CIRCLE_TICKS = 10L;
    /**
     * Frames a cast circle must draw at minimum before the period is clamped shorter.
     *
     * <p>Two is not enough to read as an animation; six is roughly a sixth of a second of motion,
     * which is the floor below which a wind-up reads as nothing happening.
     */
    private static final long MIN_CIRCLE_FRAMES = 6L;

    /** Ring plane offset from the caster's pitch, in degrees. */
    private static final float RING_PITCH_OFFSET = 90.0f;
    /** Square and sphere-cluster plane offset from the caster's pitch, in degrees. */
    private static final float POLYGON_PITCH_OFFSET = 180.0f;

    /**
     * Un-rotated ring offsets, keyed by size. They depend only on the size, never on where the
     * caster is looking, so they are safe to build once and share.
     */
    private static final Map<Double, RingGeometry> GEOMETRY = new ConcurrentHashMap<>();

    private CastCircle() {
    }

    /**
     * @return the size used when a spell does not request one; package-private for the test
     */
    static double defaultSize() {
        return DEFAULT_SIZE;
    }

    /**
     * Starts the default cast circle for a spell.
     *
     * @param spell  the spell being cast; supplies the element colour
     * @param caster the entity casting; the circle follows its eyes
     * @param event  the prepare event; a cancelled event stops the circle
     * @return the Bukkit task id, exactly as {@code Spell.circleAction} must return it
     */
    public static int start(Spell spell, LivingEntity caster, SpellPrepareEvent event) {
        return start(spell, caster, event, DEFAULT_SIZE, DEFAULT_ROTATION_STEP);
    }

    /**
     * Starts the default cast circle for a spell at the spell's own colour.
     *
     * @param spell  the spell being cast; supplies the cast time that sizes the frame budget
     * @param color  the colour the spell declared; {@code null} falls back to element grey
     * @param caster the entity casting; the circle follows its eyes
     * @param event  the prepare event; a cancelled event stops the circle
     * @return the Bukkit task id, exactly as {@code Spell.circleAction} must return it
     */
    public static int start(Spell spell, Color color, LivingEntity caster, SpellPrepareEvent event) {
        return start(spell, color, caster, event, DEFAULT_SIZE, DEFAULT_ROTATION_STEP);
    }

    /**
     * Starts the cast circle for a spell at an explicit size and rotation step, in the spell's
     * element colour.
     *
     * @param spell        the spell being cast; supplies the element colour
     * @param caster       the entity casting; the circle follows its eyes
     * @param event        the prepare event; a cancelled event stops the circle
     * @param size         circle scale; values below {@link #MIN_SIZE} fall back to the default
     * @param rotationStep degrees added to the caster's yaw to spin the layout
     * @return the Bukkit task id, exactly as {@code Spell.circleAction} must return it
     */
    public static int start(Spell spell, LivingEntity caster, SpellPrepareEvent event,
                            double size, int rotationStep) {
        return start(spell, ElementPalette.of(spell == null ? null : spell.getElement()), caster, event, size, rotationStep);
    }

    /**
     * Starts the cast circle for a spell at an explicit size, rotation step and colour.
     *
     * <p>The colour is the casting spell's own constant rather than its element's palette, so the
     * circle a spell draws during its wind-up is the same colour as the effect that follows it. A
     * spell whose element is {@code NONE} would otherwise show a grey circle around a coloured
     * payload.
     *
     * @param spell        the spell being cast; supplies the cast time that sizes the frame budget
     * @param color        the colour the spell declared; {@code null} falls back to element grey
     * @param caster       the entity casting; the circle follows its eyes
     * @param event        the prepare event; a cancelled event stops the circle
     * @param size         circle scale; values below {@link #MIN_SIZE} fall back to the default
     * @param rotationStep degrees added to the caster's yaw to spin the layout
     * @return the Bukkit task id, exactly as {@code Spell.circleAction} must return it
     */
    public static int start(Spell spell, Color color, LivingEntity caster, SpellPrepareEvent event,
                            double size, int rotationStep) {
        return start(spell, ElementPalette.of(color), caster, event, size, rotationStep);
    }

    private static int start(Spell spell, ElementPalette.Palette palette, LivingEntity caster,
                             SpellPrepareEvent event, double size, int rotationStep) {
        Particle.DustOptions bodyDust = palette.coreDust();
        Particle.DustOptions accentDust = palette.deepDust();
        RingGeometry geometry = geometryFor(size);
        long period = circleTicks(spell);

        return new BukkitRunnable() {
            @Override
            public void run() {
                if (event.isCancelled()) {
                    cancel();
                    return;
                }
                draw(caster, geometry, rotationStep, bodyDust, accentDust);
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, period).getTaskId();
    }

    private static void draw(LivingEntity caster, RingGeometry geometry, int rotationStep,
                             Particle.DustOptions bodyDust, Particle.DustOptions accentDust) {
        Location eye = caster.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) return;

        Location origin = eye.clone().add(eye.getDirection().normalize().multiply(FORWARD_OFFSET));
        Location scratch = new Location(world, 0.0, 0.0, 0.0);

        forEachPoint(geometry, origin.getYaw(), origin.getPitch(), rotationStep,
                (x, y, z, accent) -> {
                    scratch.setX(origin.getX() + x);
                    scratch.setY(origin.getY() + y);
                    scratch.setZ(origin.getZ() + z);
                    ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0,
                            accent ? accentDust : bodyDust);
                });
    }

    /**
     * Walks every point of the layout in draw order, in world space relative to the circle centre.
     *
     * <p>This is the single place the layout is turned into coordinates. The drawing path feeds it a
     * sink that mutates one scratch {@link Location}; the test feeds it a sink that collects
     * vectors. Both therefore exercise the identical arithmetic, so the equivalence with
     * {@code magicCircle} asserted by {@code CastCircleGeometryTest} is a statement about the code
     * that actually runs in game.
     *
     * @param geometry     the un-rotated offsets
     * @param yaw          the caster's yaw in degrees
     * @param pitch        the caster's pitch in degrees
     * @param rotationStep degrees added to the yaw to spin the layout
     * @param sink         receives each point, and whether it is a decorative accent point
     */
    private static void forEachPoint(RingGeometry geometry, float yaw, float pitch, int rotationStep,
                                     PointSink sink) {
        float ringPitch = RING_PITCH_OFFSET - pitch;
        float planeYaw = yaw + rotationStep;

        for (Vector offset : geometry.rings) {
            Vector placed = place(offset, planeYaw, ringPitch);
            sink.accept(placed.getX(), placed.getY(), placed.getZ(), false);
        }

        float polygonPitch = POLYGON_PITCH_OFFSET - pitch;
        for (Vector offset : geometry.squares) {
            Vector placed = place(offset, planeYaw, polygonPitch);
            sink.accept(placed.getX(), placed.getY(), placed.getZ(), false);
        }

        // The sphere clusters are added after the cluster centre has been rotated, so their own
        // offsets are NOT rotated - matching magicCircle.
        for (Vector cluster : geometry.sphereCenters) {
            Vector placed = place(cluster, planeYaw, polygonPitch);
            for (Vector unit : geometry.sphereUnits) {
                sink.accept(placed.getX() + unit.getX(),
                        placed.getY() + unit.getY(),
                        placed.getZ() + unit.getZ(),
                        true);
            }
        }
    }

    /**
     * Returns every point of the layout in world space, relative to the circle centre.
     *
     * <p>Exposed package-private so {@code CastCircleGeometryTest} can compare this against
     * {@code ParticleUtils.magicCircle} without a live server.
     *
     * @param size         the circle scale
     * @param yaw          the caster's yaw in degrees
     * @param pitch        the caster's pitch in degrees
     * @param rotationStep degrees added to the yaw to spin the layout
     * @return a fresh list of the points, in the order {@link #draw} emits them
     */
    static List<Vector> worldOffsets(double size, float yaw, float pitch, int rotationStep) {
        List<Vector> points = new ArrayList<>();
        forEachPoint(geometryFor(size), yaw, pitch, rotationStep,
                (x, y, z, accent) -> points.add(new Vector(x, y, z)));
        return points;
    }

    /**
     * Receives one point of the ring layout in world space relative to the circle centre.
     */
    @FunctionalInterface
    private interface PointSink {
        void accept(double x, double y, double z, boolean accent);
    }

    /**
     * Rotates an un-rotated offset into the circle's plane, matching the yaw-then-inverted-pitch
     * convention {@code magicCircle} applies.
     *
     * @param offset       the un-rotated offset from the circle centre
     * @param yaw          the plane's yaw in degrees
     * @param pitch        the plane's pitch in degrees
     * @return a new vector holding the rotated offset; the input is left untouched
     */
    static Vector place(Vector offset, double yaw, double pitch) {
        double pitchRad = Math.toRadians(-pitch);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);
        double y = offset.getY() * cosPitch - offset.getZ() * sinPitch;
        double z = offset.getY() * sinPitch + offset.getZ() * cosPitch;

        double yawRad = Math.toRadians(-yaw);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double x = offset.getX();
        double rx = x * cosYaw + z * sinYaw;
        double rz = -x * sinYaw + z * cosYaw;
        return new Vector(rx, y, rz);
    }

    /**
     * Returns the shared offsets for a size, building them on first use.
     *
     * @param size the circle scale
     * @return the immutable offsets for that size
     */
    static RingGeometry geometryFor(double size) {
        double effective = (Double.isNaN(size) || size < MIN_SIZE) ? DEFAULT_SIZE : size;
        return GEOMETRY.computeIfAbsent(effective, CastCircle::build);
    }

    private static RingGeometry build(double size) {
        double sizeMult = 5 / size;

        List<Vector> rings = new ArrayList<>();
        rings.addAll(ringPoints(1.5 / sizeMult, 80));
        rings.addAll(ringPoints(4.45 / sizeMult, 40));

        List<Vector> squares = new ArrayList<>();
        squares.addAll(polygonPoints(3.0 / sizeMult, 4, 5, 0.0));
        squares.addAll(polygonPoints(3.0 / sizeMult, 4, 5, 60.0));

        List<Vector> clusters = polygonPoints(4.0 / sizeMult, 6, 1, 0.0);
        List<Vector> units = fibonacciSphereOffsets(0.25 / sizeMult, 6);

        return new RingGeometry(rings.toArray(new Vector[0]),
                squares.toArray(new Vector[0]),
                clusters.toArray(new Vector[0]),
                units.toArray(new Vector[0]));
    }

    /**
     * Returns every offset the ring layout uses for a size, un-rotated, in draw order.
     *
     * <p>Point count and radius are independent of where the caster looks, so both can be asserted
     * without a Bukkit server. Exposed package-private for {@code CastCircleGeometryTest}.
     *
     * @param size the circle scale
     * @return a fresh list of {@code 13 + 40 + 36} offsets relative to the circle centre
     */
    static List<Vector> ringOffsets(double size) {
        RingGeometry geometry = geometryFor(size);
        List<Vector> offsets = new ArrayList<>(geometry.pointCount());
        offsets.addAll(List.of(geometry.rings));
        offsets.addAll(List.of(geometry.squares));
        for (Vector center : geometry.sphereCenters) {
            for (Vector unit : geometry.sphereUnits) {
                offsets.add(center.clone().add(unit));
            }
        }
        return offsets;
    }

    private static int circlePointCount(double frequency) {
        return (int) (360 / frequency);
    }

    private static List<Vector> ringPoints(double radius, double frequency) {
        int count = circlePointCount(frequency);
        List<Vector> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            points.add(new Vector(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius));
        }
        return points;
    }

    private static List<Vector> polygonPoints(double radius, int sides, int frequency, double rotationDegrees) {
        List<Vector> points = new ArrayList<>(sides * frequency);
        double step = 2 * Math.PI / sides;
        double rotation = Math.toRadians(rotationDegrees);
        for (int side = 0; side < sides; side++) {
            double startAngle = side * step + rotation;
            double endAngle = (side + 1) * step + rotation;
            for (int f = 0; f < frequency; f++) {
                double t = (double) f / frequency;
                double x = lerp(Math.cos(startAngle), Math.cos(endAngle), t) * radius;
                double y = lerp(Math.sin(startAngle), Math.sin(endAngle), t) * radius;
                points.add(new Vector(x, y, 0.0));
            }
        }
        return points;
    }

    private static List<Vector> fibonacciSphereOffsets(double radius, int count) {
        List<Vector> offsets = new ArrayList<>(count);
        double phi = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < count; i++) {
            double y = (count == 1) ? 0.0 : 1 - (i / (double) (count - 1)) * 2;
            double ring = Math.sqrt(1 - y * y);
            double theta = phi * i;
            offsets.add(new Vector(Math.cos(theta) * ring * radius, y * radius, Math.sin(theta) * ring * radius));
        }
        return offsets;
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    /**
     * The repaint period for a spell's cast circle.
     *
     * <p>{@code circle_ticks} is a ceiling, not a fixed period. The circle task is cancelled the
     * moment the cast completes, so a spell with a 0.5 s cast time and the default 10-tick period
     * only ever drew twice - at tick 0 and at tick 10, about 50 ms of a two second animation. Any
     * short-cast spell looked like it had no wind-up at all.
     *
     * <p>Clamping down to {@code castTicks / MIN_FRAMES} keeps the circle animating across the whole
     * cast for fast spells while leaving a long, expensive circle alone at the configured period.
     */
    private static long circleTicks(Spell spell) {
        Object ticks = Configs.CIRCLE_TICKS.get();
        long period = (ticks instanceof Number) ? ((Number) ticks).longValue() : DEFAULT_CIRCLE_TICKS;
        // Bukkit rejects a period below one tick, and a zero period is never what an admin meant.
        period = Math.max(1L, period);
        long budget = (spell == null) ? period : (long) (spell.getCastTime() * 20.0) / MIN_CIRCLE_FRAMES;
        return Math.min(period, Math.max(1L, budget));
    }

    /**
     * The un-rotated layout of one circle, shared across every cast at that size.
     *
     * <p>Instances are immutable and the vectors inside them are only ever read, never mutated, so
     * one set of offsets can serve every casting player at the same size.
     */
    static final class RingGeometry {

        final Vector[] rings;
        final Vector[] squares;
        final Vector[] sphereCenters;
        final Vector[] sphereUnits;

        RingGeometry(Vector[] rings, Vector[] squares, Vector[] sphereCenters, Vector[] sphereUnits) {
            this.rings = rings;
            this.squares = squares;
            this.sphereCenters = sphereCenters;
            this.sphereUnits = sphereUnits;
        }

        int pointCount() {
            return rings.length + squares.length + sphereCenters.length * sphereUnits.length;
        }
    }
}