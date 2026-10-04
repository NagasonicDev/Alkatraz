package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared VFX signature library: one named builder per shape the 38-spell audit found,
 * so two spells stop hand-rolling the same geometry and two different spells stop looking
 * identical on screen.
 *
 * <h2>Invariants - the three rules this class exists to enforce</h2>
 * <ol>
 *   <li><b>No hardcoded colours.</b> Every particle is coloured from
 *       {@link ElementPalette#of(Element)} for the casting spell. A literal RGB or a
 *       {@code Color.YELLOW} in this file is a defect, because it is exactly how
 *       {@code heal} and {@code lesser_heal} ended up sharing a colour.</li>
 *   <li><b>No raw {@code Particle.*}.</b> Every particle constant is a {@link Utils} alias.
 *       Minecraft 1.20.5 renamed a large batch of them and this plugin supports 1.19
 *       through 1.26, so a bare {@code Particle.DUST} throws at class-initialisation time on
 *       the old end of the range. See {@code ParticleAliasTest}.</li>
 *   <li><b>No direct {@code world.spawnParticle}.</b> Every emission goes through
 *       {@link ParticleCaster#spawn}, which is what makes the per-player density falloff and
 *       the {@code particles.enabled} switch work.</li>
 * </ol>
 *
 * <h2>No size or count is baked in</h2>
 * Every builder takes its geometry parameters from the caller. Nothing here decides that a
 * ring is 24 points or that a beam is 12 steps long: those belong to the spell's yml, and a
 * shared default would be a shared magic number wearing a library's clothes. Callers that want
 * a preset pass the preset's own numbers.
 *
 * <h2>Stateless</h2>
 * Every method is {@code static} and every intermediate list and {@link Vector} is a local.
 * There is no instance state and no shared mutable scratch, so two concurrent casts on the main
 * thread (or an async compute path that only computes geometry) cannot interfere.
 *
 * <h2>The pure-geometry half is separable from {@link Utils}</h2>
 * Each builder is split in two:
 * <ul>
 *   <li>a <b>public draw method</b> ({@link #beam}, {@link #nova}, ...) that spawns particles
 *       and therefore touches {@link Utils};</li>
 *   <li>a <b>package-private geometry method</b> ({@code beamGeometry}, {@code novaGeometry},
 *       ...) that returns the point list and touches nothing at all.</li>
 * </ul>
 * The split is not cosmetic. {@code Utils}'s static initialiser reaches
 * {@code MinecraftVersion}'s, which calls {@code Alkatraz.getInstance().getServer()} and NPEs
 * without a live server. A test that calls a geometry method must therefore never transitively
 * initialise {@code Utils}, and {@code SpellVfxTest} asserts exactly that. The geometry methods
 * also take no {@link Spell}: colour is irrelevant to shape, so the shape half is testable with
 * numbers alone.
 *
 * <h2>Why the geometry is computed here rather than through {@link ParticleUtils}</h2>
 * {@code ParticleUtils} has two helpers that loop forever on a non-positive step and two more
 * with sharp edges: {@code line} mutates its {@code loc1} argument in place, and {@code sphere}
 * silently returns an <b>empty</b> list when the centre has no world - which is what a unit
 * test's {@code new Location(null, ...)} has. The geometry below therefore computes its own
 * points from plain trigonometry, and reuses only {@code ParticleUtils.fibonacciSphere}, which
 * neither mutates its arguments nor inspects the world.
 * {@code SpellVfxTest} has a guard that fails the build if a {@code createHelix} or
 * {@code spiral} call is ever added here with an unclamped step.
 *
 * <h2>The five clone pairs this library exists to break</h2>
 * <ol>
 *   <li><b>{@code heal} / {@code lesser_heal}</b> - both drew a three-turn helix plus three
 *       concentric circles in the same hardcoded yellow, so the weaker heal was the stronger
 *       heal at a smaller size. Generalised as {@link #column}: a vertical column revealed
 *       slice by slice upward. {@code heal} passes a taller, faster, {@code core}-led column,
 *       {@code lesser_heal} a shorter, slower one; the two are now visibly the same family at
 *       different strengths rather than the same picture twice.</li>
 *   <li><b>{@code warp} (NONE) / {@code skeletal_knights} (DARK)</b> - the worst pair in the
 *       plugin. Both spell classes carried byte-identical {@code ellipsePoint},
 *       {@code spawnFloorCircle} and {@code spawnHexGlyphs} methods <i>and</i> eleven identical
 *       colour constants, so a teleportation and a skeleton summoning were pixel-for-pixel the
 *       same aqua ritual. Generalised as {@link #channelGlyphs}: counter-rotating floor rings
 *       plus a ring of glyph markers, with the element supplying the colour. {@code warp} now
 *       reads in neutral grey and {@code skeletal_knights} in DARK purple, which is the single
 *       most useful thing in this file.</li>
 *   <li><b>{@code barrier} (NONE) / {@code wind_barrier} (AIR)</b> - identical 200-point sphere
 *       shell and identical outward push loop, differing only in tint. Generalised as
 *       {@link #nova}: one shell draw plus an inner ring, called at each spell's own radius,
 *       point count and lifetime. The gameplay halves - hitpoints, push strength, who it
 *       protects - stay in the spells.</li>
 *   <li><b>{@code earthen_wall} (EARTH) / {@code fire_wall} (FIRE)</b> - identical steered
 *       {@code WallSegment} build loop, only the block swapped. Generalised as
 *       {@link #column} along a caller-supplied path: the wall builder asks for a column at
 *       each segment's base, and {@code spell} supplies the palette, so EARTH rises in brown
 *       and FIRE in orange without either spell naming a colour.</li>
 *   <li><b>{@code radiance} / {@code tornado} / {@code meteor_shower}</b> - the same two-phase
 *       0.5 / 0.67 rotating-ring wind-up skeleton, copy-pasted three times. Wave A's
 *       {@link WindUp} already owns the shared skeleton. <b>Residual divergence, deliberately
 *       not unified here:</b> {@code radiance} anchors on the caster and grows rings outward,
 *       {@code tornado} anchors on a ground point and descends, and {@code meteor_shower}
 *       draws its rings at the <i>impact</i> points rather than the caster. Those are three
 *       genuinely different tells, not clones, so they keep distinct parameters through
 *       {@link WindUp} and only the charging shell is shared.</li>
 * </ol>
 *
 * <h2>Which shade is used where, and why</h2>
 * The rule throughout: {@code core} is the element's identity and belongs on the
 * <i>leading edge</i> - the part a player is meant to read first; {@code accent} is the
 * supporting detail; {@code deep} is the smallest and darkest shade and belongs on
 * <i>ground and trailing</i> structure, where a bright particle would read as a separate
 * effect rather than as part of the same one. Each builder's Javadoc states its own split.
 */
public final class SpellVfx {

    /**
     * Smallest point/step count any builder will act on. Zero and negative counts are clamped
     * up to this rather than trusted: a spell yml that says {@code ring_points: 0} should draw a
     * sparse ring, not divide by zero or spin forever.
     */
    private static final int MIN_POINTS = 1;

    /** Two full turns, used only as the default phase step when a builder needs one. */
    private static final double TAU = Math.PI * 2.0;

    private SpellVfx() {
    }

    // ================================================================
    // Shared helpers
    // ================================================================

    /**
     * Clamps a caller-supplied point count to a usable value.
     *
     * @param points the requested count, possibly zero, negative or {@code NaN}-producing
     * @return at least {@link #MIN_POINTS}
     */
    static int clampPoints(int points) {
        return Math.max(MIN_POINTS, points);
    }

    /**
     * Clamps a non-negative count where zero is genuinely meaningful - a column with no
     * revealed slices yet should draw nothing, not draw one.
     *
     * @param count the requested count
     * @return zero or more, never negative
     */
    static int clampOptional(int count) {
        return Math.max(0, count);
    }

    /**
     * The palette for a spell, tolerating a null spell so a caller mid-migration cannot NPE.
     *
     * @param spell the casting spell, or {@code null}
     * @return the element palette; {@link Element#NONE}'s neutral grey for {@code null}
     */
    static ElementPalette.Palette paletteOf(Spell spell) {
        return ElementPalette.of((spell == null) ? null : spell.getElement());
    }

    /**
     * Returns a world only when the location actually has one.
     *
     * <p>{@link ParticleCaster} is not null-world safe on its disabled-particles branch, where it
     * falls through to {@code world.spawnParticle}. Every draw method checks here first.
     *
     * @param location the location whose world is wanted
     * @return its world, or {@code null} when the location is null or worldless
     */
    static World worldOf(Location location) {
        return (location == null) ? null : location.getWorld();
    }

    /**
     * Whether a geometry method has a centre to build around.
     *
     * <p><b>Deliberately not a world check.</b> The draw methods need a real world, because
     * {@link ParticleCaster} falls through to {@code world.spawnParticle} when particles are
     * disabled; the geometry methods do not, and rejecting a worldless centre there would make
     * every shape untestable - a unit test can only build {@code new Location(null, ...)}.
     * {@link ParticleUtils#sphere} makes exactly that mistake and returns an empty list, which
     * is why {@link SpellVfx#nova} cannot use it.
     *
     * @param location the centre, possibly {@code null} or worldless
     * @return {@code true} when the location exists and its coordinates can be read
     */
    static boolean hasCentre(Location location) {
        return location != null;
    }

    /**
     * A unit vector perpendicular to {@code direction}, chosen so it is never near-degenerate.
     *
     * <p>A beam's ribbon has to be offset sideways to have visible width, and offsetting along a
     * fixed axis makes the ribbon collapse to a line whenever the beam points straight up or
     * down. Picking the reference axis based on how vertical the direction is avoids that.
     *
     * @param direction the direction to offset from; zero length yields a fallback perpendicular
     * @return a unit vector perpendicular to {@code direction}, never null
     */
    static Vector perpendicularTo(Vector direction) {
        Vector reference = (Math.abs(direction.getY()) > 0.9)
                ? new Vector(1.0, 0.0, 0.0)
                : new Vector(0.0, 1.0, 0.0);
        Vector perp = direction.clone().crossProduct(reference);
        if (perp.lengthSquared() < 1e-12) {
            return new Vector(1.0, 0.0, 0.0);
        }
        return perp.normalize();
    }

    // ================================================================
    // BEAM / LINE
    // ================================================================

    /**
     * A tapered line of particles from {@code origin} to {@code target}.
     *
     * <p><b>Shades.</b> The spine is {@code core}: it is the leading edge, the line the eye
     * follows from the caster to the impact. The two flanking ribbon points are {@code accent}:
     * they exist only to give the line thickness, so they must not out-shout the spine.
     *
     * @param spell      the casting spell; supplies the palette
     * @param caster     the entity casting, used for density falloff; {@code null} broadcasts
     * @param origin     where the beam starts
     * @param target     where the beam ends
     * @param steps      spine segments; clamped to at least one
     * @param startWidth ribbon width at the origin, in blocks
     * @param endWidth   ribbon width at the target, in blocks
     */
    public static void beam(Spell spell, LivingEntity caster, Location origin, Location target,
                            int steps, double startWidth, double endWidth) {
        World world = worldOf(origin);
        if (world == null) return;
        List<Location> points = beamGeometry(origin, target, steps, startWidth, endWidth);
        if (points.isEmpty()) return;

        ElementPalette.Palette palette = paletteOf(spell);
        Location scratch = new Location(world, 0.0, 0.0, 0.0);
        boolean spine = true;
        for (Location point : points) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0,
                    spine ? palette.coreDust() : palette.accentDust());
            spine = !spine;
        }
    }

    /**
     * The beam's points: a spine of {@code steps + 1} samples with a flanking ribbon point on
     * each side, interleaved so the draw order runs left-spine-right, left-spine-right.
     *
     * <p>Deliberately not {@code ParticleUtils.line}: that helper multiplies its direction vector
     * <i>by the running index and then normalises it</i>, so its output is not a straight line,
     * and it mutates its first argument in place. This computes the interpolation directly.
     *
     * @param origin     where the beam starts
     * @param target     where the beam ends
     * @param steps      spine segments; clamped to at least one
     * @param startWidth ribbon width at the origin, in blocks
     * @param endWidth   ribbon width at the target, in blocks
     * @return {@code 2 * (steps + 1)} points; empty when either endpoint is null. A worldless origin
     *         is fine, and a target in a different world is coerced onto the origin's, since a
     *         particle cannot span two
     */
    static List<Location> beamGeometry(Location origin, Location target, int steps, double startWidth, double endWidth) {
        if (!hasCentre(origin) || !hasCentre(target)) return new ArrayList<>();
        List<Location> points = new ArrayList<>();
        World world = origin.getWorld();

        int segments = clampPoints(steps);
        Vector start = origin.toVector();
        Vector delta = target.toVector().subtract(start);
        Vector direction = delta.clone();
        if (direction.lengthSquared() < 1e-12) {
            direction = new Vector(0.0, 1.0, 0.0);
        } else {
            direction.normalize();
        }
        Vector perp = perpendicularTo(direction);

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            Vector spinePoint = start.clone().add(delta.clone().multiply(t));
            double half = Math.max(0.0, beamWidthAt(t, startWidth, endWidth)) * 0.5;
            points.add(spinePoint.clone().add(perp.clone().multiply(half)).toLocation(world));
            points.add(spinePoint.clone().subtract(perp.clone().multiply(half)).toLocation(world));
        }
        return points;
    }

    /**
     * The beam's ribbon half-width at a fraction along its length, linearly interpolated.
     *
     * @param t          progress from 0.0 at the origin to 1.0 at the target, unclamped
     * @param startWidth width at the origin
     * @param endWidth   width at the target
     * @return the interpolated half-width; clamped at zero so a negative yml value cannot
     *         invert the ribbon
     */
    static double beamWidthAt(double t, double startWidth, double endWidth) {
        return Math.max(0.0, startWidth + (endWidth - startWidth) * t);
    }

    // ================================================================
    // PROJECTILE
    // ================================================================

    /**
     * A compact travelling cluster with a trailing tail.
     *
     * <p><b>Shades.</b> The head cluster is {@code core} - it is the projectile and must be the
     * brightest thing on screen. The tail is {@code accent}, fading toward {@code deep} as it
     * falls behind, so a projectile that has just been fired reads as moving away from where it
     * started rather than as a static blob.
     *
     * @param spell         the casting spell; supplies the palette
     * @param caster        the entity casting, used for density falloff
     * @param head          the cluster's centre; this is the travelling position
     * @param direction     unit travel direction; a zero vector falls back to straight up
     * @param clusterRadius radius of the head cluster, in blocks
     * @param clusterPoints number of particles in the head cluster
     * @param tailPoints    number of particles in the tail
     * @param tailLength    how far behind {@code head} the tail extends horizontally, in blocks
     */
    public static void projectile(Spell spell, LivingEntity caster, Location head, Vector direction,
                                  double clusterRadius, int clusterPoints, int tailPoints, double tailLength) {
        World world = worldOf(head);
        if (world == null) return;
        List<Location> cluster = projectileHead(head, clusterRadius, clusterPoints);
        ElementPalette.Palette palette = paletteOf(spell);
        Location scratch = new Location(world, 0.0, 0.0, 0.0);
        for (Location point : cluster) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.coreDust());
        }
        List<Location> tail = projectileTail(head, direction, tailPoints, tailLength);
        int emitted = 0;
        for (Location point : tail) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            // Darken down the tail: the oldest tail particle is the darkest.
            double age = tail.isEmpty() ? 0.0 : (double) emitted / tail.size();
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0,
                    age < 0.5 ? palette.accentDust() : palette.deepDust());
            emitted++;
        }
    }

    /**
     * The head cluster only, so a caller can draw the head and the tail on different ticks.
     *
     * @param head          the cluster's centre
     * @param clusterRadius radius of the head cluster, in blocks
     * @param clusterPoints number of particles; zero yields an empty list
     * @return up to {@code clusterPoints} points, each exactly {@code clusterRadius} from the head
     */
    static List<Location> projectileHead(Location head, double clusterRadius, int clusterPoints) {
        if (!hasCentre(head) || clampOptional(clusterPoints) == 0) return new ArrayList<>();
        List<Location> points = new ArrayList<>();
        World world = head.getWorld();
        points.addAll(ParticleUtils.fibonacciSphere(head, Math.max(0.0, clusterRadius), clusterPoints));
        return points;
    }

    /**
     * The tail only: a straight run behind the head along {@code direction} with a slight
     * downward drift so the trail reads as lagging behind rather than as a rigid pipe. The
     * horizontal budget is {@code tailLength}; the drift adds a little vertical reach on top,
     * which is why a tail point can sit slightly further than {@code tailLength} from the head
     * in three dimensions.
     *
     * @param head       the travelling position the tail hangs off
     * @param direction  travel direction; a zero vector falls back to straight up
     * @param tailPoints number of tail particles; zero yields an empty list
     * @param tailLength how far behind the head the tail reaches horizontally, in blocks
     * @return up to {@code tailPoints} points, each within {@code tailLength} horizontally of the head
     */
    static List<Location> projectileTail(Location head, Vector direction, int tailPoints, double tailLength) {
        if (!hasCentre(head)) return new ArrayList<>();
        List<Location> points = new ArrayList<>();
        int count = clampOptional(tailPoints);
        if (count == 0) return points;
        World world = head.getWorld();

        Vector dir = (direction == null) ? new Vector(0.0, 1.0, 0.0) : direction.clone();
        if (dir.lengthSquared() < 1e-12) {
            dir = new Vector(0.0, 1.0, 0.0);
        } else {
            dir.normalize();
        }
        double span = Math.max(0.0, tailLength);
        for (int i = 0; i < count; i++) {
            double t = (double) (i + 1) / count;
            double back = span * t;
            double drop = -0.12 * back * back;
            Vector point = head.toVector().add(dir.clone().multiply(-back));
            point.setY(point.getY() + drop);
            points.add(point.toLocation(world));
        }
        return points;
    }

    // ================================================================
    // NOVA / SPHERE
    // ================================================================

    /**
     * An expanding spherical shell.
     *
     * <p><b>Shades.</b> The outer shell is {@code core} and is the visible surface of the blast.
     * The inner ring is {@code deep} and reads as thickness: a hollow-looking shell with a dark
     * interior reads as a sphere, where a single flat ring of particles reads as a circle.
     * A data-less {@code END_ROD} flash at the centre adds the leading edge without needing a
     * version-sensitive particle name.
     *
     * <p><b>Not {@code ParticleUtils.sphere}.</b> That helper returns an <b>empty</b> list when
     * the centre has no world. That is correct for a spell (a worldless location is never cast
     * at) but it silently turns the whole shell into nothing in a unit test, so this uses
     * {@code fibonacciSphere}, which does not inspect the world.
     *
     * @param spell   the casting spell; supplies the palette
     * @param caster  the entity casting, used for density falloff
     * @param center  the shell's centre
     * @param radius  shell radius in blocks
     * @param points  number of shell particles; the inner ring is half of it
     */
    public static void nova(Spell spell, LivingEntity caster, Location center, double radius, int points) {
        World world = worldOf(center);
        if (world == null) return;
        List<Location> shell = novaGeometry(center, radius, points);
        if (shell.isEmpty()) return;

        ElementPalette.Palette palette = paletteOf(spell);
        ParticleCaster.spawn(caster, world, Utils.END_ROD, center, Math.max(1, clampPoints(points) / 8));
        Location scratch = new Location(world, 0.0, 0.0, 0.0);
        for (Location point : shell) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.coreDust());
        }
        for (Location point : novaInnerRing(center, radius, points)) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.deepDust());
        }
    }

    /**
     * The shell points only, on a Fibonacci sphere: evenly distributed with no polar clumping,
     * so a low point count still covers the whole sphere.
     *
     * @param center the shell's centre
     * @param radius shell radius in blocks; negative values are treated as zero
     * @param points number of shell particles; zero yields an empty list
     * @return up to {@code points} points, each exactly {@code radius} from the centre
     */
    static List<Location> novaGeometry(Location center, double radius, int points) {
        if (!hasCentre(center) || clampOptional(points) == 0) return new ArrayList<>();
        List<Location> shell = new ArrayList<>();
        World world = center.getWorld();
        shell.addAll(ParticleUtils.fibonacciSphere(center, Math.max(0.0, radius), points));
        return shell;
    }

    /**
     * The shell's inner ring: a single flat circle at half the shell radius, giving the blast
     * visible depth.
     *
     * @param center the shell's centre
     * @param radius shell radius in blocks
     * @param points shell point count; the ring uses half of it, at least three
     * @return a flat ring of points, each exactly {@code radius / 2} horizontally from the centre
     */
    static List<Location> novaInnerRing(Location center, double radius, int points) {
        int ringPoints = Math.max(3, clampPoints(points) / 2);
        return ringGeometry(center, Math.max(0.0, radius) * 0.5, ringPoints, 0.0, 0.0);
    }

    // ================================================================
    // RING
    // ================================================================

    /**
     * A flat ground ring.
     *
     * <p><b>Shades.</b> The ring itself is {@code core}. Passing a non-zero
     * {@code shadowOffset} lays a second ring at the same radius underneath in {@code deep},
     * counter-rotated, which reads as a ground shadow and stops a flat ring looking like a
     * drawn-on circle. Pass {@code 0.0} to skip it.
     *
     * @param spell         the casting spell; supplies the palette
     * @param caster        the entity casting, used for density falloff
     * @param center        the ring's centre, normally a ground point
     * @param radius        ring radius in blocks
     * @param points        number of ring particles
     * @param rotation      phase offset in radians, for spinning the ring
     * @param shadowOffset  downward offset for the {@code deep} ground ring, in blocks; zero skips it
     */
    public static void ring(Spell spell, LivingEntity caster, Location center, double radius, int points,
                            double rotation, double shadowOffset) {
        World world = worldOf(center);
        if (world == null) return;
        ElementPalette.Palette palette = paletteOf(spell);
        Location scratch = new Location(world, 0.0, 0.0, 0.0);

        for (Location point : ringGeometry(center, radius, points, rotation, 0.0)) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.coreDust());
        }
        if (shadowOffset != 0.0) {
            for (Location point : ringGeometry(center, radius, points, -rotation, shadowOffset)) {
                scratch.setX(point.getX());
                scratch.setY(point.getY());
                scratch.setZ(point.getZ());
                ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.deepDust());
            }
        }
    }

    /**
     * A flat ring in the XZ plane, exactly {@code radius} from the centre.
     *
     * @param center         the ring's centre
     * @param radius         ring radius in blocks
     * @param points         number of ring particles; clamped to at least three so the ring closes
     * @param rotation       phase offset in radians
     * @param verticalOffset vertical displacement applied to every point, for a floating ring
     * @return exactly {@code points} points, all at the same y and the same horizontal radius
     */
    static List<Location> ringGeometry(Location center, double radius, int points, double rotation, double verticalOffset) {
        if (!hasCentre(center)) return new ArrayList<>();
        List<Location> ring = new ArrayList<>();
        World world = center.getWorld();
        int count = Math.max(3, clampPoints(points));
        double r = Math.max(0.0, radius);
        for (int i = 0; i < count; i++) {
            double angle = TAU * i / count + rotation;
            ring.add(new Location(world,
                    center.getX() + Math.cos(angle) * r,
                    center.getY() + verticalOffset,
                    center.getZ() + Math.sin(angle) * r));
        }
        return ring;
    }

    /**
     * The radius a ring should have at a given progress, for the expanding/contracting case.
     *
     * <p>The <b>sign is the whole point</b>: a positive sign grows the ring from the centre
     * outward (a blast wave), a negative sign pulls it back inward (an implosion or a
     * retracting telegraph). Spells used to hand-roll this by inverting a subtraction, which is
     * how an implosion ended up growing.
     *
     * @param baseRadius radius at progress 0.0
     * @param progress   animation progress; clamped to {@code [0, 1]}
     * @param sign       {@code +1} to expand, {@code -1} to contract; zero stays at the base
     * @return the radius to draw at that progress, never negative
     */
    static double ringRadius(double baseRadius, double progress, double sign) {
        double base = Math.max(0.0, baseRadius);
        double t = Math.min(1.0, Math.max(0.0, progress));
        double direction = (sign > 0.0) ? 1.0 : (sign < 0.0 ? -1.0 : 0.0);
        return Math.max(0.0, base * (1.0 + direction * t));
    }

    // ================================================================
    // CONE / SECTOR
    // ================================================================

    /**
     * A filled angular sector on the ground - the shape a breath, a cone of flame or a sweep
     * needs, and the shape no {@link ParticleUtils} helper provides.
     *
     * <p><b>Shades.</b> The outer arc is {@code core} because it is the edge a player must
     * recognise to know they are being hit. The interior fill is {@code deep}: a bright interior
     * would wash the arc out, and a dark interior is what makes the arc legible as a boundary.
     *
     * @param spell            the casting spell; supplies the palette
     * @param caster           the entity casting, used for density falloff
     * @param center           the sector's apex, normally on the ground
     * @param radius           sector radius in blocks
     * @param arcDegrees       total angular width in degrees; values at or above 360 close the sector
     * @param startAngleDegrees where the arc begins, in degrees, measured anticlockwise from +X
     * @param arcSteps         divisions along the arc
     * @param radialSteps      divisions from apex to rim
     */
    public static void cone(Spell spell, LivingEntity caster, Location center, double radius,
                            double arcDegrees, double startAngleDegrees, int arcSteps, int radialSteps) {
        World world = worldOf(center);
        if (world == null) return;
        ElementPalette.Palette palette = paletteOf(spell);
        Location scratch = new Location(world, 0.0, 0.0, 0.0);

        List<Location> sector = sectorGeometry(center, radius, arcDegrees, startAngleDegrees, arcSteps, radialSteps);
        int boundary = (clampPoints(arcSteps) + 1) * (clampPoints(radialSteps) + 1);
        int index = 0;
        for (Location point : sector) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            boolean isRim = index >= boundary - (clampPoints(radialSteps) + 1);
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0,
                    isRim ? palette.coreDust() : palette.deepDust());
            index++;
        }
    }

    /**
     * The sector's points, generated column by column: for each angular division, every
     * division from apex to rim. The last {@code radialSteps + 1} points of each column are the
     * rim, which is what {@link #cone} keys its {@code core} shade off.
     *
     * @param center            the sector's apex
     * @param radius            sector radius in blocks
     * @param arcDegrees        total angular width; clamped into {@code (0, 360]}
     * @param startAngleDegrees arc start in degrees
     * @param arcSteps          angular divisions; clamped to at least one
     * @param radialSteps       radial divisions; clamped to at least one
     * @return {@code (arcSteps + 1) * (radialSteps + 1)} points, all within {@code radius} of the apex
     */
    static List<Location> sectorGeometry(Location center, double radius, double arcDegrees,
                                         double startAngleDegrees, int arcSteps, int radialSteps) {
        if (!hasCentre(center)) return new ArrayList<>();
        List<Location> sector = new ArrayList<>();
        World world = center.getWorld();

        int arcs = clampPoints(arcSteps);
        int radials = clampPoints(radialSteps);
        double arc = Math.min(360.0, Math.max(1.0, arcDegrees));
        double start = Math.toRadians(startAngleDegrees);
        double r = Math.max(0.0, radius);

        for (int a = 0; a <= arcs; a++) {
            double angle = start + Math.toRadians(arc) * a / arcs;
            for (int s = 0; s <= radials; s++) {
                double reach = r * s / radials;
                sector.add(new Location(world,
                        center.getX() + Math.cos(angle) * reach,
                        center.getY(),
                        center.getZ() + Math.sin(angle) * reach));
            }
        }
        return sector;
    }

    // ================================================================
    // COLUMN / VERTICAL
    // ================================================================

    /**
     * A vertical column revealed slice by slice upward.
     *
     * <p>This is the builder that generalises {@code heal} / {@code lesser_heal} (a rising helix
     * plus concentric circles in the same yellow) and the {@code earthen_wall} /
     * {@code fire_wall} {@code WallSegment} loop (an identical segment-at-a-time riser). The
     * difference between a healing column and a wall is only the radius, the height and the pace
     * of the reveal - not the code, and certainly not the colour.
     *
     * <p><b>Shades.</b> The leading slice - the topmost layer, the part a player reads as
     * "this is finished" - is {@code core}. Everything below it steps toward {@code deep}, so
     * the column reads as rising rather than as a uniformly lit tube. The horizontal phase step
     * rotates each layer against the one below it, which is what turns a stack of rings into
     * something that looks like it is being wound up.
     *
     * @param spell            the casting spell; supplies the palette
     * @param caster           the entity casting, used for density falloff
     * @param base             the column's foot; {@code columnGeometry} starts here
     * @param height           total column height in blocks
     * @param radius           column radius in blocks
     * @param layers           number of vertical slices; zero yields an empty column
     * @param pointsPerLayer   particles per slice; clamped to at least one
     * @param phaseStepRadians rotation applied between consecutive slices
     */
    public static void column(Spell spell, LivingEntity caster, Location base, double height, double radius,
                              int layers, int pointsPerLayer, double phaseStepRadians) {
        World world = worldOf(base);
        if (world == null) return;
        List<Location> points = columnGeometry(base, height, radius, layers, pointsPerLayer, phaseStepRadians);
        if (points.isEmpty()) return;

        ElementPalette.Palette palette = paletteOf(spell);
        int perLayer = clampPoints(pointsPerLayer);
        Location scratch = new Location(world, 0.0, 0.0, 0.0);
        for (int i = 0; i < points.size(); i++) {
            Location point = points.get(i);
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            // Layers are ordered bottom-up, so a high index is a leading edge and wants core.
            double layerFraction = (double) (i / perLayer) / Math.max(1, clampOptional(layers) - 1);
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, shadeFor(palette, layerFraction));
        }
    }

    /**
     * Picks the dust for a column layer: {@code deep} at the foot, {@code accent} through the
     * body, {@code core} at the leading slice.
     *
     * @param palette the element palette
     * @param t       how far up the column the layer sits, 0.0 at the foot and 1.0 at the top
     * @return the matching pre-built dust, never null
     */
    private static Particle.DustOptions shadeFor(ElementPalette.Palette palette, double t) {
        if (t < 0.25) return palette.deepDust();
        if (t < 0.75) return palette.accentDust();
        return palette.coreDust();
    }

    /**
     * The column's points, bottom slice first, so a caller can reveal the column by drawing a
     * growing prefix of the returned list - one point per tick, or {@link #columnRevealed} per
     * frame, without recomputing the geometry.
     *
     * @param base             the column's foot
     * @param height           total column height in blocks
     * @param radius           column radius in blocks
     * @param layers           number of vertical slices; zero or negative yields an empty column
     * @param pointsPerLayer   particles per slice; clamped to at least one
     * @param phaseStepRadians rotation applied between consecutive slices
     * @return {@code layers * pointsPerLayer} points, in ascending y then around the ring
     */
    static List<Location> columnGeometry(Location base, double height, double radius,
                                         int layers, int pointsPerLayer, double phaseStepRadians) {
        if (!hasCentre(base)) return new ArrayList<>();
        List<Location> points = new ArrayList<>();
        int layerCount = clampOptional(layers);
        if (layerCount == 0) return points;
        World world = base.getWorld();

        int perLayer = clampPoints(pointsPerLayer);
        double h = Math.max(0.0, height);
        double r = Math.max(0.0, radius);
        for (int layer = 0; layer < layerCount; layer++) {
            double y = (layerCount == 1) ? h : h * layer / (layerCount - 1);
            double phase = phaseStepRadians * layer;
            for (int i = 0; i < perLayer; i++) {
                double angle = TAU * i / perLayer + phase;
                points.add(new Location(world,
                        base.getX() + Math.cos(angle) * r,
                        base.getY() + y,
                        base.getZ() + Math.sin(angle) * r));
            }
        }
        return points;
    }

    /**
     * How many slices of a column are revealed at a given progress.
     *
     * @param layers   the column's total slice count
     * @param progress animation progress; clamped to {@code [0, 1]}
     * @return a slice count in {@code [0, layers]}, so an unrevealed column draws nothing
     */
    static int columnRevealed(int layers, double progress) {
        int total = clampOptional(layers);
        if (total == 0) return 0;
        double t = Math.min(1.0, Math.max(0.0, progress));
        return Math.min(total, (int) Math.round(t * total));
    }

    // ================================================================
    // ORBIT
    // ================================================================

    /**
     * {@code n} bodies circling a centre at varying radius and phase.
     *
     * <p><b>Shades.</b> Bodies alternate {@code core} and {@code accent} by index parity, so a
     * ring of orbiting motes has internal rhythm and reads as several objects rather than one
     * uniform ring. The centre itself gets a {@code deep} marker so there is something to orbit.
     *
     * @param spell        the casting spell; supplies the palette
     * @param caster       the entity casting, used for density falloff
     * @param center       the orbit's centre
     * @param minRadius    radius of the innermost body, in blocks
     * @param maxRadius    radius of the outermost body; below {@code minRadius} the two are swapped
     * @param bodies       number of orbiting bodies; zero yields no orbits
     * @param phaseRadians overall phase offset in radians, for spinning the whole formation
     */
    public static void orbit(Spell spell, LivingEntity caster, Location center, double minRadius, double maxRadius,
                             int bodies, double phaseRadians) {
        World world = worldOf(center);
        if (world == null) return;
        List<Location> points = orbitGeometry(center, minRadius, maxRadius, bodies, phaseRadians);
        if (points.isEmpty()) return;

        ElementPalette.Palette palette = paletteOf(spell);
        Location scratch = new Location(world, center.getX(), center.getY(), center.getZ());
        ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.deepDust());
        for (int i = 0; i < points.size(); i++) {
            Location point = points.get(i);
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0,
                    (i % 2 == 0) ? palette.coreDust() : palette.accentDust());
        }
    }

    /**
     * The orbiting bodies' positions, spread evenly in phase and evenly in radius from
     * {@code minRadius} to {@code maxRadius}. Even radius spacing matters: bunching them at one
     * radius produces a plain ring, which is what {@link #ring} is for.
     *
     * @param center       the orbit's centre
     * @param minRadius    radius of the innermost body
     * @param maxRadius    radius of the outermost body; swapped with {@code minRadius} if lower
     * @param bodies       number of bodies; zero or negative yields an empty list
     * @param phaseRadians overall phase offset
     * @return exactly {@code bodies} points, each between {@code minRadius} and {@code maxRadius}
     */
    static List<Location> orbitGeometry(Location center, double minRadius, double maxRadius,
                                       int bodies, double phaseRadians) {
        if (!hasCentre(center)) return new ArrayList<>();
        List<Location> points = new ArrayList<>();
        int count = clampOptional(bodies);
        if (count == 0) return points;
        World world = center.getWorld();

        double inner = Math.max(0.0, Math.min(minRadius, maxRadius));
        double outer = Math.max(0.0, Math.max(minRadius, maxRadius));
        for (int i = 0; i < count; i++) {
            double fraction = count == 1 ? 0.0 : (double) i / (count - 1);
            double angle = TAU * i / count + phaseRadians;
            double r = inner + (outer - inner) * fraction;
            points.add(new Location(world,
                    center.getX() + Math.cos(angle) * r,
                    center.getY(),
                    center.getZ() + Math.sin(angle) * r));
        }
        return points;
    }

    // ================================================================
    // FIELD / DOME
    // ================================================================

    /**
     * A persistent ground dome with an interior volume.
     *
     * <p><b>Shades.</b> The shell is {@code core}: it is the boundary, and the boundary is what
     * a player inside a persistent field needs to see. The interior volume is {@code deep}, so
     * being inside reads as enclosed - filled with dark element - rather than as a second
     * surface. This is a per-frame shape for a field with a lifetime, not a one-shot burst.
     *
     * @param spell      the casting spell; supplies the palette
     * @param caster     the entity casting, used for density falloff
     * @param center     the dome's base, on the ground
     * @param radius     dome radius in blocks
     * @param height     dome height in blocks
     * @param shellPoints particles on the surface; zero yields no shell
     * @param fillPoints  particles in the interior volume; zero yields an empty interior
     * @param phaseRadians spin applied to both shell and fill
     */
    public static void field(Spell spell, LivingEntity caster, Location center, double radius, double height,
                             int shellPoints, int fillPoints, double phaseRadians) {
        World world = worldOf(center);
        if (world == null) return;
        ElementPalette.Palette palette = paletteOf(spell);
        Location scratch = new Location(world, 0.0, 0.0, 0.0);

        for (Location point : fieldGeometry(center, radius, height, shellPoints, 0, phaseRadians)) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.coreDust());
        }
        for (Location point : fieldGeometry(center, radius, height, 0, fillPoints, phaseRadians)) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.deepDust());
        }
    }

    /**
     * The dome's points: the requested number of shell points over the upper half of an
     * ellipsoid, or the requested number of interior points, depending on which count is
     * non-zero. Call {@link #field} rather than this twice if both are wanted.
     *
     * <p>The shell is a hemisphere, so every point satisfies {@code 0 <= y <= height} and
     * {@code horizontal distance <= radius}. The interior is kept strictly inside half the
     * radius and below the shell so it is never mistaken for the boundary.
     *
     * @param center      the dome's base
     * @param radius      dome radius in blocks
     * @param height      dome height in blocks
     * @param shellPoints number of shell points; zero skips the shell
     * @param fillPoints  number of interior points; zero skips the interior
     * @param phaseRadians spin applied to the distribution
     * @return up to {@code shellPoints + fillPoints} points inside the dome's bounds
     */
    static List<Location> fieldGeometry(Location center, double radius, double height,
                                        int shellPoints, int fillPoints, double phaseRadians) {
        if (!hasCentre(center)) return new ArrayList<>();
        List<Location> points = new ArrayList<>();
        World world = center.getWorld();

        double r = Math.max(0.0, radius);
        double h = Math.max(0.0, height);
        int shell = clampOptional(shellPoints);
        int fill = clampOptional(fillPoints);

        // Golden-angle stride over a half sphere: no pole clumping, and the shell stays closed
        // at the base because t starts at 0 rather than being sampled from the full sphere.
        for (int i = 0; i < shell; i++) {
            double t = (double) i / shell;
            double y = h * Math.sin(Math.PI * 0.5 * t);
            double ringRadius = r * Math.sqrt(Math.max(0.0, 1.0 - t * t));
            double angle = phaseRadians + TAU * i * 0.6180339887498949;
            points.add(new Location(world,
                    center.getX() + Math.cos(angle) * ringRadius,
                    center.getY() + y,
                    center.getZ() + Math.sin(angle) * ringRadius));
        }

        // Interior volume, kept inside half the radius and below the shell height.
        double inner = r * 0.5;
        for (int i = 0; i < fill; i++) {
            double y = h * 0.75 * ((i + 0.5) / fill);
            double angle = phaseRadians * 1.7 + TAU * i * 0.7548776662466927;
            double reach = inner * Math.sqrt(Math.max(0.0, 1.0 - Math.pow((i + 0.5) / fill, 2)));
            points.add(new Location(world,
                    center.getX() + Math.cos(angle) * reach,
                    center.getY() + y,
                    center.getZ() + Math.sin(angle) * reach));
        }
        return points;
    }

    // ================================================================
    // GROUND / SHAPE
    // ================================================================

    /**
     * Advancing or retreating ground geometry - a crack, a furrow, a scoured line.
     *
     * <p>The ground shape differs from {@link #ring} in that it is <i>elongated</i> and it
     * <i>reveals progressively</i>. Pass a rising {@code advance} as a crack opens, or a falling
     * one as it closes, and the same builder does both.
     *
     * <p><b>Shades.</b> The leading end is {@code core} - the furthest point reached is the
     * information the player needs. Everything behind it steps toward {@code deep}, so the crack
     * looks like it is healing behind its own front.
     *
     * @param spell       the casting spell; supplies the palette
     * @param caster      the entity casting, used for density falloff
     * @param center      where the shape starts, on the ground
     * @param length      how far the shape runs, in blocks
     * @param width       total lateral width, in blocks
     * @param samples     steps along the length; zero yields no geometry
     * @param lanes       lateral divisions across the width; clamped to at least one
     * @param yawDegrees  direction the shape runs, in degrees
     * @param advance     how much of the shape is revealed, {@code [0, 1]}; see {@link #groundRevealed}
     */
    public static void ground(Spell spell, LivingEntity caster, Location center, double length, double width,
                              int samples, int lanes, double yawDegrees, double advance) {
        World world = worldOf(center);
        if (world == null) return;
        List<Location> points = groundGeometry(center, length, width, samples, lanes, yawDegrees);
        if (points.isEmpty()) return;

        ElementPalette.Palette palette = paletteOf(spell);
        int laneCount = clampPoints(lanes);
        int stationCount = clampOptional(samples);
        int revealed = groundRevealed(samples, advance) * laneCount;

        Location scratch = new Location(world, 0.0, 0.0, 0.0);
        for (int i = 0; i < revealed && i < points.size(); i++) {
            Location point = points.get(i);
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            double t = (double) (i / laneCount) / Math.max(1, stationCount - 1);
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, shadeFor(palette, t));
        }
    }

    /**
     * The ground shape's points: {@code samples} stations along the run direction, each with
     * {@code lanes} points across the width, ordered front to back so a prefix is the revealed
     * portion.
     *
     * @param center      where the shape starts
     * @param length      how far the shape runs
     * @param width       total lateral width; negative is treated as zero
     * @param samples     steps along the length; zero or negative yields an empty list
     * @param lanes       lateral divisions; clamped to at least one
     * @param yawDegrees  direction the shape runs, in degrees
     * @return {@code samples * lanes} points, all within {@code length} and {@code width / 2} of the centre line
     */
    static List<Location> groundGeometry(Location center, double length, double width,
                                         int samples, int lanes, double yawDegrees) {
        if (!hasCentre(center)) return new ArrayList<>();
        List<Location> points = new ArrayList<>();
        int sampleCount = clampOptional(samples);
        if (sampleCount == 0) return points;
        World world = center.getWorld();

        int laneCount = clampPoints(lanes);
        double run = Math.max(0.0, length);
        double halfWidth = Math.max(0.0, width) * 0.5;
        double yaw = Math.toRadians(yawDegrees);
        double forwardX = Math.cos(yaw);
        double forwardZ = Math.sin(yaw);
        double leftX = -forwardZ;
        double leftZ = forwardX;

        for (int s = 0; s < sampleCount; s++) {
            double along = (sampleCount == 1) ? run : run * s / (sampleCount - 1);
            for (int l = 0; l < laneCount; l++) {
                double across = (laneCount == 1) ? 0.0 : halfWidth * (2.0 * l / (laneCount - 1) - 1.0);
                points.add(new Location(world,
                        center.getX() + forwardX * along + leftX * across,
                        center.getY(),
                        center.getZ() + forwardZ * along + leftZ * across));
            }
        }
        return points;
    }

    /**
     * How much of a ground shape is revealed at a given progress.
     *
     * <p>Increasing progress advances the leading edge; decreasing it retracts from that same
     * edge, so a crack closes in the direction it opened. Clamped to {@code [0, samples]}.
     *
     * @param samples      the shape's total station count
     * @param advanceRatio animation progress; clamped to {@code [0, 1]}
     * @return a station count in {@code [0, samples]}
     */
    static int groundRevealed(int samples, double advanceRatio) {
        int total = clampOptional(samples);
        if (total == 0) return 0;
        double t = Math.min(1.0, Math.max(0.0, advanceRatio));
        return Math.min(total, (int) Math.round(t * total));
    }

    // ================================================================
    // SELF-AURA
    // ================================================================

    /**
     * A pair of counter-rotating rings around a single entity, following it.
     *
     * <p><b>Must be re-anchored every frame</b>: the subject's location is read at draw time, so
     * this is a per-tick call and not a one-shot. {@link ParticleCaster} handles the per-player
     * distance falloff from there.
     *
     * <p><b>Shades.</b> The outer ring is {@code core} - it is the aura's boundary. The inner
     * ring is {@code accent} and turns the other way, so the aura has motion even before the
     * caller adds its own spin.
     *
     * @param spell         the casting spell; supplies the palette
     * @param caster        the entity casting, used for density falloff
     * @param subject       the entity the aura follows
     * @param radius        outer ring radius in blocks
     * @param points        particles per ring; each ring uses this count
     * @param rotationRadians outer ring phase offset, in radians; the inner ring uses the negative
     */
    public static void selfAura(Spell spell, LivingEntity caster, LivingEntity subject, double radius,
                                int points, double rotationRadians) {
        if (subject == null) return;
        World world = worldOf(subject.getLocation());
        if (world == null) return;

        ElementPalette.Palette palette = paletteOf(spell);
        Location center = subject.getLocation();
        Location scratch = new Location(world, 0.0, 0.0, 0.0);

        int ringPoints = Math.max(3, clampPoints(points));
        for (Location point : ringGeometry(center, radius, ringPoints, rotationRadians, 0.0)) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.coreDust());
        }
        for (Location point : ringGeometry(center, Math.max(0.0, radius) * 0.55, ringPoints, -rotationRadians, 0.0)) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.accentDust());
        }
    }

    /**
     * The aura's two rings: the outer ring first, then the inner one at 0.55 of the outer radius
     * with the phase negated. Both are flat and centred on the subject, so the caller can lift
     * them by adding to the centre's y before drawing.
     *
     * @param subjectLocation where the subject currently is
     * @param radius          outer ring radius in blocks
     * @param points          particles per ring; clamped to at least three
     * @param rotationRadians outer ring phase offset
     * @return exactly {@code 2 * points} points, split outer-then-inner
     */
    static List<Location> selfAuraGeometry(Location subjectLocation, double radius, int points, double rotationRadians) {
        List<Location> aura = new ArrayList<>();
        int ringPoints = Math.max(3, clampPoints(points));
        List<Location> outer = ringGeometry(subjectLocation, radius, ringPoints, rotationRadians, 0.0);
        if (outer.isEmpty()) return aura;
        aura.addAll(outer);
        aura.addAll(ringGeometry(subjectLocation, Math.max(0.0, radius) * 0.55, ringPoints, -rotationRadians, 0.0));
        return aura;
    }

    // ================================================================
    // CHANNEL GLYPHS  (the warp / skeletal_knights pair)
    // ================================================================

    /**
     * The channel ritual floor: a counter-rotating pair of rings plus a ring of glyph markers.
     *
     * <p>This is the builder that breaks the plugin's worst clone pair. {@code warp} and
     * {@code skeletal_knights} each carried their own copy of {@code spawnFloorCircle},
     * {@code spawnHexGlyphs} and {@code ellipsePoint}, and their eleven colour constants were
     * byte-identical - so a neutral teleportation and a DARK skeleton summoning were the same
     * aqua picture. Everything coloured here comes from the spell's element, which is the entire
     * point: {@code warp} now draws grey, {@code skeletal_knights} draws purple, off one code
     * path.
     *
     * <p><b>Shades.</b> The outer ring is {@code core} and the inner one {@code accent}, turning
     * opposite ways, so the ritual reads as spinning even on a static frame. The glyph markers
     * are {@code deep}: they are slow structural detail on the floor and must not compete with
     * the rings above them.
     *
     * @param spell         the casting spell; supplies the palette
     * @param caster        the entity casting, used for density falloff
     * @param feet          the channel's floor point
     * @param radius        outer ring radius in blocks
     * @param ringPoints    particles per ring
     * @param glyphCount    glyph markers around the ring; zero draws none
     * @param rotationRadians phase offset in radians for the outer ring
     */
    public static void channelGlyphs(Spell spell, LivingEntity caster, Location feet, double radius,
                                     int ringPoints, int glyphCount, double rotationRadians) {
        World world = worldOf(feet);
        if (world == null) return;
        ElementPalette.Palette palette = paletteOf(spell);
        Location scratch = new Location(world, 0.0, 0.0, 0.0);
        int ringSize = Math.max(3, clampPoints(ringPoints));

        for (Location point : ringGeometry(feet, radius, ringSize, rotationRadians, 0.0)) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.coreDust());
        }
        for (Location point : ringGeometry(feet, Math.max(0.0, radius) * 0.7, ringSize, -rotationRadians * 1.4, 0.0)) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.accentDust());
        }
        for (Location point : channelGlyphGeometry(feet, radius, glyphCount, rotationRadians)) {
            scratch.setX(point.getX());
            scratch.setY(point.getY());
            scratch.setZ(point.getZ());
            ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, palette.deepDust());
        }
    }

    /**
     * The glyph markers only: {@code glyphCount} evenly spaced points on a ring at 0.85 of the
     * outer radius. Six of them is a hex, which is what both original implementations drew.
     *
     * @param feet       the channel's floor point
     * @param radius     outer ring radius in blocks
     * @param glyphCount number of markers; zero or negative yields an empty list
     * @param rotation   phase offset in radians
     * @return exactly {@code glyphCount} points, each 0.85 * {@code radius} from the centre
     */
    static List<Location> channelGlyphGeometry(Location feet, double radius, int glyphCount, double rotation) {
        if (!hasCentre(feet)) return new ArrayList<>();
        List<Location> points = new ArrayList<>();
        int count = clampOptional(glyphCount);
        if (count == 0) return points;
        World world = feet.getWorld();

        double glyphRadius = Math.max(0.0, radius) * 0.85;
        for (int i = 0; i < count; i++) {
            double angle = TAU * i / count + rotation;
            points.add(new Location(world,
                    feet.getX() + Math.cos(angle) * glyphRadius,
                    feet.getY(),
                    feet.getZ() + Math.sin(angle) * glyphRadius));
        }
        return points;
    }
}