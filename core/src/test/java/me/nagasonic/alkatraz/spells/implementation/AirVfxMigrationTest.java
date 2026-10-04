package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the AIR wave's migration onto the shared VFX library for the four spells it owns:
 * {@code swift}, {@code tornado}, {@code wind_vortex} and {@code wind_barrier}.
 *
 * <h2>Why most of this asserts on source text rather than on behaviour</h2>
 * Almost none of the four spells' new VFX is reachable without a live server: the cast circle
 * needs a scheduler and an entity's eye location, and {@code SpellVfx}'s draw methods touch
 * {@code Utils}, whose static initialiser reaches {@code MinecraftVersion} and calls
 * {@code Alkatraz.getInstance().getServer()} - it NPEs in a unit test. So the assertions that
 * matter here are the structural ones, and they are read straight off the {@code .java} file:
 * a hardcoded {@code new Particle.DustOptions} or {@code Color.fromRGB} in a spell is the exact
 * waste this migration removes, and no runtime assertion can catch its return.
 *
 * <p>The one part that <em>is</em> runtime-assertable is {@code SpellVfx}'s geometry half. Each
 * builder has a package-private twin that touches nothing Bukkit-static, so this calls those
 * reflectively and checks the shapes the four spells now depend on.
 *
 * <h2>What would silently pass if it broke</h2>
 * A spell could delete its {@code circleAction} body entirely, leave the yml untouched, and pass
 * every yml assertion in this class. That is why {@link #everyAirSpellDelegatesItsCastCircle()}
 * and {@link #everyAirSpellBuildsNoColoursByHand()} read the source instead.
 */
class AirVfxMigrationTest {

    /**
     * The four spells this wave owns, paired with the magic circle each one requires. The circle
     * is what {@code SpellBudgets.castTimeRange} is indexed by, so it is asserted from the yml
     * rather than hardcoded twice.
     */
    private static final String[] AIR_SPELLS = {"swift", "wind_vortex", "tornado", "wind_barrier"};

    /** The Java sources this wave migrated, read from disk for the structural assertions. */
    private static final String[] AIR_SOURCES = {"Swift.java", "WindVortex.java", "Tornado.java", "WindBarrier.java"};

    // ==================================================================
    // YML assertions - no Bukkit mock needed
    // ==================================================================

    /**
     * Every migrated spell declares an element the plugin actually knows. This is the key
     * {@link me.nagasonic.alkatraz.spells.vfx.ElementPalette} keys off, so a typo here would not
     * throw - it would silently repaint the spell in neutral grey mid-cast.
     */
    @Test
    void everyMigratedAirSpellDeclaresAKnownElement() throws IOException {
        for (String id : AIR_SPELLS) {
            String declared = load(id).getString("element");
            assertNotNull(declared, id + " must declare an element");
            Element resolved = resolveElement(declared);
            assertSame(Element.AIR, resolved,
                    id + " declares element '" + declared + "', which resolves to " + resolved
                            + ". These four spells are the AIR wave and must resolve to AIR, or the "
                            + "shared palette paints them the wrong element entirely");
        }
    }

    /**
     * Each spell's base cast time sits inside the budget for the circle it requires. Nothing in
     * this migration touches cast time, so this is a guard: the wave must not have nudged a
     * yml and silently pushed a spell past its circle's window.
     */
    @Test
    void everyMigratedAirSpellKeepsItsCastTimeInsideItsCircleBudget() throws IOException {
        for (String id : AIR_SPELLS) {
            YamlConfiguration yml = load(id);
            int circle = yml.getInt("level");
            assertTrue(circle >= 1 && circle <= 5,
                    id + " declares level " + circle + "; only circles 1-5 have a cast-time budget");

            SpellBudgets.CastTimeRange range = SpellBudgets.castTimeRange(circle);
            int ticks = (int) Math.round(yml.getDouble("cast_time") * 20.0);
            assertTrue(ticks >= range.minTicks() && ticks <= range.maxTicks(),
                    id + " casts in " + yml.getDouble("cast_time") + "s (" + ticks + " ticks) but "
                            + "circle " + circle + " allows " + range.minTicks() + ".." + range.maxTicks());
        }
    }

    // ==================================================================
    // Source assertions - the structural half of the migration
    // ==================================================================

    /**
     * Each spell delegates its cast circle to {@code CastCircle.start}. The hand-rolled
     * {@code scheduleSyncRepeatingTask} copies this migration removed also rebuilt a
     * {@code Color} and a {@code DustOptions} on every tick of every cast.
     */
    @Test
    void everyAirSpellDelegatesItsCastCircle() {
        for (String source : AIR_SOURCES) {
            String code = readSource(source);
            assertTrue(code.contains("CastCircle.start(this, caster, e"),
                    source + " must call CastCircle.start(this, caster, e, <size>, 0) from circleAction");
            assertFalse(code.contains("ParticleUtils.magicCircle"),
                    source + " still hand-rolls the cast circle via ParticleUtils.magicCircle");
            assertFalse(code.contains("scheduleSyncRepeatingTask"),
                    source + " still schedules its own cast circle; the shared CastCircle owns that");
        }
    }

    /**
     * The cast circle keeps the size it had before the migration. Sizes were deliberately not
     * normalised: a circle that quietly resizes is a visual regression nobody would notice in
     * a diff. Tornado and wind_barrier drew at 3, swift and wind_vortex at 2.
     */
    @Test
    void everyAirSpellKeepsItsPreMigrationCircleSize() {
        assertTrue(readSource("Tornado.java").contains("CastCircle.start(this, caster, e, 3.0, 0)"),
                "tornado drew its circle at size 3 before the migration");
        assertTrue(readSource("WindBarrier.java").contains("CastCircle.start(this, caster, e, 3.0, 0)"),
                "wind_barrier drew its circle at size 3 before the migration");
        assertTrue(readSource("Swift.java").contains("CastCircle.start(this, caster, e, 2.0, 0)"),
                "swift drew its circle at size 2 before the migration");
        assertTrue(readSource("WindVortex.java").contains("CastCircle.start(this, caster, e, 2.0, 0)"),
                "wind_vortex drew its circle at size 2 before the migration");
    }

    /**
     * Each spell resolves its colours once, from the AIR palette, into a {@code PALETTE} constant.
     *
     * <p>A cached palette is the whole point: {@code ElementPalette} builds each element's three
     * {@code DustOptions} exactly once in a {@code static final}, so asking for one per tick
     * allocates nothing. A spell that constructs its own dust has not actually been migrated, and
     * {@link #everyAirSpellBuildsNoColoursByHand()} is what would catch that - this assertion is
     * about the positive half, that the constant exists at all.
     */
    @Test
    void everyAirSpellDeclaresACachedAirPalette() {
        for (String source : AIR_SOURCES) {
            String code = readSource(source);
            assertTrue(code.contains("private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.AIR);"),
                    source + " must cache the AIR palette once in a PALETTE constant");
        }
    }

    /**
     * No migrated spell constructs a colour by hand.
     *
     * <p>This is the assertion that actually pins the migration. Before it, {@code Tornado} held
     * a {@code CORE_DUST} plus two {@code DustOptions[]} gradient arrays - eighteen
     * {@code new Particle.DustOptions} built at class-init from literal RGB - and every spell
     * built one more inside its cast circle lambda, every tick, for every casting player.
     */
    @Test
    void everyAirSpellBuildsNoColoursByHand() {
        for (String source : AIR_SOURCES) {
            String code = stripComments(readSource(source));
            assertFalse(code.contains("new Particle.DustOptions"),
                    source + " still constructs a Particle.DustOptions; colours come from PALETTE");
            assertFalse(code.contains("Color.fromRGB"),
                    source + " still hardcodes an RGB colour; colours come from PALETTE");
            assertFalse(code.contains("Color.WHITE"),
                    source + " still uses a Bukkit colour constant; colours come from PALETTE");
        }
    }

    /**
     * No migrated spell names a {@code Particle} constant directly.
     *
     * <p>Minecraft 1.20.5 renamed a large batch of them, and this plugin supports 1.19 through
     * 1.26, so a bare {@code Particle.SWEEP_ATTACK} throws at class-initialisation time on the
     * old end of the range. Every particle must go through the {@code Utils} alias. Comments are
     * stripped first, because the Javadoc legitimately names the constants it removed.
     */
    @Test
    void everyAirSpellUsesOnlyAliasedParticles() {
        for (String source : AIR_SOURCES) {
            String code = stripComments(readSource(source));
            assertFalse(code.contains("Particle."),
                    source + " references a raw org.bukkit.Particle constant; use the Utils alias");
        }
    }

    /**
     * Each migrated spell actually draws through the shared library. A spell can satisfy every
     * structural assertion above by caching a palette and delegating its circle while still
     * hand-rolling its body effect; this is the assertion that the body moved too.
     */
    @Test
    void everyAirSpellDrawsThroughTheSharedVfxLibrary() {
        assertTrue(readSource("Swift.java").contains("SpellVfx.beam("),
                "swift's dash trail is a SpellVfx beam from the previous position to the current one");
        assertTrue(readSource("Tornado.java").contains("SpellVfx.column("),
                "tornado's funnel is a SpellVfx column");
        assertTrue(readSource("WindVortex.java").contains("SpellVfx.orbit("),
                "wind_vortex's descending rings are SpellVfx orbit bands");
        assertTrue(readSource("WindBarrier.java").contains("SpellVfx.nova("),
                "wind_barrier's shell is a SpellVfx nova");
    }

    // ==================================================================
    // Geometry - the pure twins, called reflectively
    // ==================================================================

    /**
     * {@code beamGeometry} lays a spine of {@code steps + 1} samples with a ribbon point on each
     * side, so swift's dash trail is {@code 2 * (steps + 1)} points long and starts and ends
     * exactly on the two positions it was handed.
     *
     * <p>The end points are asserted exactly and the ribbon only approximately, because the
     * ribbon is offset along a perpendicular whose axis depends on the beam's direction.
     */
    @Test
    void beamGeometrySpansOriginToTargetWithARibbon() {
        Location origin = new Location(null, 0.0, 0.0, 0.0);
        Location target = new Location(null, 0.0, 0.0, 10.0);

        List<Location> points = beamGeometry(origin, target, 6, 0.6, 0.1);

        assertEquals(2 * (6 + 1), points.size(),
                "a 6 step beam is a 7 sample spine plus a ribbon point either side of each sample");
        assertEquals(0.0, points.get(0).getZ(), 1e-9,
                "the first ribbon point sits on the origin's end of the spine");
        assertEquals(10.0, points.get(points.size() - 1).getZ(), 1e-9,
                "the last ribbon point sits on the target end of the spine");

        // Every point is within half the start width of the straight line between the two ends,
        // which is what keeps the ribbon from collapsing into something wider than it claims.
        // The spine runs along z here, so the ribbon is offset purely sideways.
        for (Location point : points) {
            assertTrue(Math.abs(point.getX()) <= 0.6 * 0.5 + 1e-9,
                    "a ribbon point sat further from the spine than the declared start width: x="
                            + point.getX());
            assertTrue(point.getZ() >= -1e-9 && point.getZ() <= 10.0 + 1e-9,
                    "a beam point sat outside the segment it interpolates: z=" + point.getZ());
        }
    }

    /**
     * {@code beamGeometry} clamps a non-positive step count to one segment rather than dividing by
     * zero. A spell yml or a mistyped constant must not take the whole cast down.
     */
    @Test
    void beamGeometryClampsAZeroStepRequest() {
        Location origin = new Location(null, 1.0, 2.0, 3.0);
        Location target = new Location(null, 4.0, 5.0, 6.0);

        assertEquals(4, beamGeometry(origin, target, 0, 0.4, 0.4).size(),
                "zero steps is clamped to one segment, giving 2 samples x 2 ribbon points");
        assertEquals(4, beamGeometry(origin, target, -5, 0.4, 0.4).size(),
                "a negative step count is clamped the same way");
    }

    /**
     * {@code novaGeometry} puts every shell point exactly the requested radius from the centre,
     * on a Fibonacci sphere. wind_barrier's barrier shell is the consumer, so a point that drifts
     * off the radius is a lumpy dome rather than a barrier.
     *
     * <p>This also pins the reason {@code SpellVfx.nova} does not reuse {@code ParticleUtils.sphere}:
     * that helper returns an <b>empty</b> list when the centre has no world, and a unit test can
     * only build a worldless {@code Location}. Here the centre is worldless on purpose.
     */
    @Test
    void novaGeometryPlacesEveryShellPointOnTheRadius() {
        Location centre = new Location(null, 10.0, 64.0, -20.0);
        double radius = 3.0;

        List<Location> shell = novaGeometry(centre, radius, 100);

        assertEquals(100, shell.size(), "wind_barrier's shell asks for 100 points");
        for (Location point : shell) {
            // Measured by hand rather than with Location.distance, which refuses to compare two
            // worldless locations - and a worldless centre is the whole point of this test.
            double dx = point.getX() - centre.getX();
            double dy = point.getY() - centre.getY();
            double dz = point.getZ() - centre.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            assertEquals(radius, distance, 1e-6,
                    "a shell point sat off the barrier radius, which makes the dome lumpy");
        }
    }

    /**
     * {@code orbitGeometry} spreads bodies evenly in radius between the two bounds, so
     * wind_vortex's ring bands widen toward the mouth of the funnel instead of collapsing into
     * one flat hoop. Swapped bounds are tolerated rather than producing a negative radius.
     */
    @Test
    void orbitGeometrySpreadsBodiesAcrossTheRadiusBand() {
        Location centre = new Location(null, 0.0, 0.0, 0.0);

        List<Location> bodies = orbitGeometry(centre, 2.0, 6.0, 5, 0.0);

        assertEquals(5, bodies.size(), "one location per orbiting body");
        double[] expected = {2.0, 3.0, 4.0, 5.0, 6.0};
        for (int i = 0; i < bodies.size(); i++) {
            double reach = Math.hypot(bodies.get(i).getX(), bodies.get(i).getZ());
            assertEquals(expected[i], reach, 1e-6,
                    "body " + i + " should sit at the evenly spaced radius " + expected[i]);
        }

        List<Location> swapped = orbitGeometry(centre, 6.0, 2.0, 5, 0.0);
        for (int i = 0; i < swapped.size(); i++) {
            double reach = Math.hypot(swapped.get(i).getX(), swapped.get(i).getZ());
            assertEquals(expected[i], reach, 1e-6,
                    "bounds passed the wrong way round must be swapped, not turned negative");
        }
    }

    /**
     * {@code columnGeometry} stacks layers bottom-up at evenly spaced heights, each rotated
     * against the one below. tornado's funnel is built from it, so the ordering is what makes
     * {@code SpellVfx.column} shade the top slice as the leading edge.
     */
    @Test
    void columnGeometryStacksLayersBottomUpAtEvenlySpacedHeights() {
        Location base = new Location(null, 5.0, 60.0, 5.0);

        List<Location> column = columnGeometry(base, 12.0, 2.0, 4, 3, 0.0);

        assertEquals(12, column.size(), "4 layers x 3 particles each");
        double[] expectedHeights = {60.0, 64.0, 68.0, 72.0};
        for (int layer = 0; layer < 4; layer++) {
            for (int point = 0; point < 3; point++) {
                assertEquals(expectedHeights[layer], column.get(layer * 3 + point).getY(), 1e-9,
                        "layer " + layer + " point " + point + " is at the wrong height");
            }
        }
        for (Location point : column) {
            double reach = Math.hypot(point.getX() - base.getX(), point.getZ() - base.getZ());
            assertEquals(2.0, reach, 1e-6, "every column point must sit on the column radius");
        }
    }

    /**
     * A zero-layer column draws nothing at all, rather than one degenerate layer. tornado's
     * funnel is revealed during a wind-up, so "not yet revealed" has to be expressible.
     */
    @Test
    void columnGeometryDrawsNothingForZeroLayers() {
        Location base = new Location(null, 0.0, 0.0, 0.0);

        assertTrue(columnGeometry(base, 12.0, 2.0, 0, 8, 0.5).isEmpty(),
                "zero layers means no column yet, not a single layer at the cap height");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = AirVfxMigrationTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /**
     * Resolves a spell yml's {@code element} key the way the plugin does, without touching
     * {@code ElementPalette} - going through the palette would silently map a typo to
     * {@code NONE} and make a misspelt element look like a passing test.
     *
     * @param declared the raw yml value, e.g. {@code "AIR"}
     * @return the matching constant
     */
    private static Element resolveElement(String declared) {
        for (Element candidate : Element.values()) {
            if (candidate.name().equalsIgnoreCase(declared.trim())) {
                return candidate;
            }
        }
        throw new AssertionError("no Element constant named '" + declared + "'");
    }

    /**
     * Calls a package-private {@code SpellVfx} geometry method.
     *
     * <p>Reflection rather than a same-package test because this class belongs to the spell
     * migration, not to the library. The methods are deliberately package-private and touch
     * nothing Bukkit-static, so invoking them needs no {@code MockedStatic} and no live server -
     * which is exactly the property being relied on here.
     *
     * @param name   the geometry method's name, e.g. {@code "beamGeometry"}
     * @param params the method's declared parameter types
     * @param args   the arguments to pass
     * @param <T>    {@code List}, which every geometry method returns
     * @return the method's result
     */
    @SuppressWarnings("unchecked")
    private static <T> T callGeometry(String name, Class<?>[] params, Object... args) {
        try {
            Method method = SpellVfx.class.getDeclaredMethod(name, params);
            method.setAccessible(true);
            return (T) method.invoke(null, args);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Could not invoke SpellVfx." + name + "; if it was renamed or its signature "
                            + "changed, update AirVfxMigrationTest to match", ex);
        }
    }

    private static List<Location> beamGeometry(Location origin, Location target,
                                               int steps, double startWidth, double endWidth) {
        return callGeometry("beamGeometry",
                new Class<?>[]{Location.class, Location.class, int.class, double.class, double.class},
                origin, target, steps, startWidth, endWidth);
    }

    private static List<Location> novaGeometry(Location centre, double radius, int points) {
        return callGeometry("novaGeometry",
                new Class<?>[]{Location.class, double.class, int.class}, centre, radius, points);
    }

    private static List<Location> orbitGeometry(Location centre, double minRadius, double maxRadius,
                                                int bodies, double phaseRadians) {
        return callGeometry("orbitGeometry",
                new Class<?>[]{Location.class, double.class, double.class, int.class, double.class},
                centre, minRadius, maxRadius, bodies, phaseRadians);
    }

    private static List<Location> columnGeometry(Location base, double height, double radius,
                                                 int layers, int pointsPerLayer, double phaseStep) {
        return callGeometry("columnGeometry",
                new Class<?>[]{Location.class, double.class, double.class, int.class, int.class, double.class},
                base, height, radius, layers, pointsPerLayer, phaseStep);
    }

    private static String readSource(String fileName) {
        Path relative = Paths.get("src", "main", "java", "me", "nagasonic", "alkatraz",
                "spells", "implementation", fileName);
        Path source = locate(relative);
        try {
            return new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read " + source, ex);
        }
    }

    /**
     * Removes comments so a structural assertion is about code, not prose. These files'
     * Javadoc legitimately names {@code Particle.DustOptions} and {@code Color.fromRGB} while
     * describing exactly what the migration deleted; without stripping, those comments would
     * read as violations.
     *
     * @param source the raw file text
     * @return the same text with line and block comments blanked out
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inBlock = false;
        boolean inLine = false;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = (i + 1 < source.length()) ? source.charAt(i + 1) : '\0';

            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                } else if (c == '\n') {
                    out.append(c);
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static Path locate(Path relative) {
        List<String> tried = new ArrayList<>();
        Path dir = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 5 && dir != null; depth++) {
            Path candidate = dir.resolve(relative);
            tried.add(candidate.toString());
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate " + relative
                + " relative to the working directory. Tried: " + tried);
    }
}