package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave C, group FIRE: {@code flaming_volley} moved onto the shared VFX library.
 *
 * <h2>What this suite can and cannot prove</h2>
 * {@code flaming_volley} was one of the plugin's worst FIRE colour offenders: it drew a pure
 * {@code (255, 0, 0)} cast circle on an {@code element: FIRE} spell. That is a property of the
 * source, so most assertions here are source assertions - and every source assertion strips
 * comments first, because the class now <i>documents</i> the old literal in its Javadoc and a
 * naive grep would match its own changelog.
 *
 * <h2>Why this suite needs no Bukkit mock</h2>
 * {@code Utils}'s static initialiser reaches {@code MinecraftVersion}'s, which calls
 * {@code Alkatraz.getInstance().getServer()} and NPEs without a live server. Nothing here
 * touches a {@code Utils} static: the yml half reads bundled resources, the colour half goes
 * through {@link ElementPalette} - which is deliberately the one VFX class that depends on
 * nothing but {@code Color}, {@code Particle.DustOptions} and {@code Element} - and the
 * geometry half calls only {@code SpellVfx}'s pure twins.
 *
 * <h2>Why the geometry twins are reached by reflection</h2>
 * {@code orbitGeometry}, {@code sectorGeometry}, {@code groundGeometry} and
 * {@code groundRevealed} are <b>package-private</b> in {@code spells.vfx}, and this suite lives
 * in {@code spells.implementation}. Reflection is used rather than moving the test, because
 * widening those methods to {@code public} purely for a test in another package would weaken
 * the boundary the library draws deliberately. If a signature changes, these fail loudly.
 */
class FireVfxMigrationTest {

    private static final double EPSILON = 1e-6;

    /** The circle each spell is promoted to; drives the cast-time budget assertions. */
    private static final int VOLLEY_CIRCLE = 4;

    /**
     * A raw {@code Particle.X} where {@code X} is not {@code DustOptions}. DustOptions is the
     * Bukkit type of a dust option, not a particle constant, so it is the one legitimate
     * {@code Particle.} reference left in these files.
     */
    private static final Pattern RAW_PARTICLE = Pattern.compile("Particle\\.(?!DustOptions\\b)[A-Z_]");

    /** A hardcoded colour literal, in either the builder or the {@code Color.RED} shorthand. */
    private static final Pattern HARDCODED_COLOUR =
            Pattern.compile("Color\\.(fromRGB|RED)\\s*\\(|Color\\.RED\\b");

    /** Same as above but tolerant of the exact pure-red triple, however it is spaced. */
    private static final Pattern PURE_RED_TRIPLE = Pattern.compile("255\\s*,\\s*0\\s*,\\s*0\\s*\\)");

    // ================================================================
    // Bundled yml
    // ================================================================

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = FireVfxMigrationTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void flamingVolleyCastTimeSitsInsideTheCircleFourWindow() throws IOException {
        SpellBudgets.CastTimeRange range = SpellBudgets.castTimeRange(VOLLEY_CIRCLE);
        int ticks = secondsToTicks(load("flaming_volley").getDouble("cast_time"));
        assertTrue(range.contains(ticks),
                "flaming_volley's cast_time is " + ticks + " ticks, outside the circle-"
                        + VOLLEY_CIRCLE + " window " + range.minTicks() + "-" + range.maxTicks());
    }

    // ================================================================
    // Source guards: colours
    // ================================================================

    @Test
    void flamingVolleyDeclaresTheSharedFirePalette() {
        String code = code("FlamingVolley");
        assumeSourceFound("FlamingVolley");
        assertTrue(code.contains("ElementPalette.of(Element.FIRE)"),
                "FlamingVolley must source its colours from ElementPalette's FIRE palette");
        assertTrue(Pattern.compile("static final ElementPalette\\.Palette PALETTE\\b").matcher(code).find(),
                "FlamingVolley must declare PALETTE as a static final, so the palette is resolved "
                        + "once per class rather than per particle");
    }

    @Test
    void flamingVolleyConstructsNoDustOptionsInline() {
        String code = code("FlamingVolley");
        assumeSourceFound("FlamingVolley");
        assertFalse(code.contains("new Particle.DustOptions"),
                "FlamingVolley constructs a Particle.DustOptions. ElementPalette pre-builds its three "
                        + "dusts exactly once; building one here would allocate a Color plus a "
                        + "DustOptions on a per-tick path.");
    }

    @Test
    void flamingVolleyNamesExactlyOneRgbLiteral() {
        String code = code("FlamingVolley");
        assumeSourceFound("FlamingVolley");
        Matcher matcher = HARDCODED_COLOUR.matcher(code);
        int literals = 0;
        while (matcher.find()) {
            literals++;
        }
        assertEquals(1, literals,
                "FlamingVolley must name exactly one colour, its CAST_COLOR. Every dust must come "
                        + "from PALETTE, which is derived from that one declaration.");
    }

    @Test
    void flamingVolleyNoLongerDrawsPureRed() {
        String code = code("FlamingVolley");
        assumeSourceFound("FlamingVolley");
        assertFalse(code.contains("Color.RED"),
                "Color.RED on an element: FIRE spell is the pure-red bug. It is the one colour that "
                        + "can never be mistaken for fire, and fireball already draws orange.");
        assertFalse(PURE_RED_TRIPLE.matcher(code).find(),
                "FlamingVolley still spells out a saturated-red triple, which is Color.RED by hand");
    }

    @Test
    void theFirePalettesThreeShadesAreAllDistinctAndNoneArePureRed() {
        ElementPalette.Palette fire = ElementPalette.of(Element.FIRE);
        assertNotEquals(fire.core().asRGB(), fire.accent().asRGB(),
                "core and accent must differ, or a two-tone effect has one tone");
        assertNotEquals(fire.accent().asRGB(), fire.deep().asRGB(),
                "accent and deep must differ, or the ground detail is invisible");
        assertNotEquals(fire.core().asRGB(), fire.deep().asRGB(),
                "core and deep must differ, or the effect has no internal structure");

        for (ElementPalette.Palette palette : new ElementPalette.Palette[]{
                ElementPalette.of(Element.FIRE), ElementPalette.of("fire")}) {
            for (org.bukkit.Color shade : new org.bukkit.Color[]{
                    palette.core(), palette.accent(), palette.deep()}) {
                boolean pureRed = shade.getRed() == 255 && shade.getGreen() == 0 && shade.getBlue() == 0;
                assertFalse(pureRed,
                        "FIRE shade " + shade.asRGB() + " is pure red: red saturated with green and "
                                + "blue both zero. That is the FlamingVolley cast-circle bug, and the "
                                + "palette must never reintroduce it.");
            }
        }
    }

    // ================================================================
    // Source guards: particles and cast circle
    // ================================================================

    @Test
    void flamingVolleyNamesNoRawParticleConstant() {
        String code = code("FlamingVolley");
        assumeSourceFound("FlamingVolley");
        assertFalse(RAW_PARTICLE.matcher(code).find(),
                "FlamingVolley still references a raw Particle constant. Minecraft 1.20.5 renamed a "
                        + "large batch of them and this plugin supports 1.19 to 1.26, so a bare "
                        + "Particle.X throws at class-initialisation time on the old end. "
                        + "Use the Utils aliases.");
    }

    @Test
    void flamingVolleyCastCircleDelegatesToTheSharedCircle() {
        String code = code("FlamingVolley");
        assumeSourceFound("FlamingVolley");
        assertTrue(code.contains("return CastCircle.start(this, caster, e, 3.0, 0);"),
                "FlamingVolley must delegate its cast circle to CastCircle at size 3, which is what it "
                        + "passed to ParticleUtils.magicCircle before. A hand-rolled circle also "
                        + "leaks its repeating task on cancellation.");
    }

    @Test
    void theCastCircleStillNamesNeitherTheSharedTickConfigNorAMagicCircleCall() {
        String code = code("FlamingVolley");
        assumeSourceFound("FlamingVolley");
        assertFalse(code.contains("ParticleUtils.magicCircle"),
                "FlamingVolley still calls magicCircle directly; CastCircle reproduces that layout "
                        + "offset for offset and owns its scheduling");
        assertFalse(code.contains("Configs.CIRCLE_TICKS"),
                "FlamingVolley still reads the circle period from Configs; CastCircle reads it, with "
                        + "the same floor of one tick");
    }

    // ================================================================
    // Pure geometry: SpellVfx.orbit, the volley's rising flame rings
    // ================================================================

    @Test
    @SuppressWarnings("unchecked")
    void orbitSpreadsEveryBodyBetweenTheInnerAndOuterRadius() throws Exception {
        List<Location> bodies = (List<Location>) invokeGeometry("orbitGeometry", new Class<?>[]{Location.class, double.class, double.class, int.class, double.class}, at(0.0, 0.0, 0.0), 1.5, 3.5, 6, 0.0);

        assertEquals(6, bodies.size(), "orbit must return exactly one point per body");
        for (Location body : bodies) {
            double radius = Math.hypot(body.getX(), body.getZ());
            assertTrue(radius >= 1.5 - EPSILON && radius <= 3.5 + EPSILON,
                    "every orbiting body must sit between minRadius and maxRadius, but one sits at "
                            + radius);
        }
        // Even radius spacing is the whole difference from a plain ring: bodies bunched at one
        // radius would just be SpellVfx.ring, which a spell must not be re-implementing.
        assertEquals(1.5, Math.hypot(bodies.get(0).getX(), bodies.get(0).getZ()), EPSILON,
                "the first body sits on the inner radius");
        assertEquals(3.5, Math.hypot(bodies.get(5).getX(), bodies.get(5).getZ()), EPSILON,
                "the last body sits on the outer radius");
    }

    @Test
    @SuppressWarnings("unchecked")
    void orbitSwapsInvertedRadiiInsteadOfDrawingNothing() throws Exception {
        List<Location> bodies = (List<Location>) invokeGeometry("orbitGeometry", new Class<?>[]{Location.class, double.class, double.class, int.class, double.class}, at(0.0, 0.0, 0.0), 3.5, 1.5, 4, 0.0);

        assertEquals(4, bodies.size(), "inverted radii must still produce a full set of bodies");
        for (Location body : bodies) {
            double radius = Math.hypot(body.getX(), body.getZ());
            assertTrue(radius >= 1.5 - EPSILON && radius <= 3.5 + EPSILON,
                    "a caller that passes maxRadius below minRadius must get them swapped, not a "
                            + "ring outside both bounds; one body sits at " + radius);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void orbitPhaseRotatesTheWholeFormationRigidly() throws Exception {
        List<Location> unrotated = (List<Location>) invokeGeometry("orbitGeometry", new Class<?>[]{Location.class, double.class, double.class, int.class, double.class}, at(0.0, 0.0, 0.0), 1.0, 1.0, 4, 0.0);
        List<Location> rotated = (List<Location>) invokeGeometry("orbitGeometry", new Class<?>[]{Location.class, double.class, double.class, int.class, double.class}, at(0.0, 0.0, 0.0), 1.0, 1.0, 4, Math.PI / 2.0);

        for (int i = 0; i < unrotated.size(); i++) {
            double expected = Math.atan2(unrotated.get(i).getZ(), unrotated.get(i).getX())
                    + Math.PI / 2.0;
            double actual = Math.atan2(rotated.get(i).getZ(), rotated.get(i).getX());
            double delta = Math.abs(Math.atan2(Math.sin(expected - actual), Math.cos(expected - actual)));
            assertEquals(0.0, delta, 1e-6,
                    "a phase offset must rotate every body by the same angle, preserving the "
                            + "formation rather than scrambling it");
        }
    }

    // ================================================================
    // Pure geometry: SpellVfx.cone, the volley's spread sector
    // ================================================================

    @Test
    @SuppressWarnings("unchecked")
    void sectorRimSpansExactlyTheArcDegrees() throws Exception {
        // The cone draw keys its core shade off the last radialSteps + 1 points of each column,
        // so "the rim" is only meaningful as a set. Collect every point on the outer radius.
        List<Location> sector = (List<Location>) invokeGeometry("sectorGeometry", new Class<?>[]{Location.class, double.class, double.class, double.class, int.class, int.class}, at(0.0, 0.0, 0.0), 50.0, 45.0, 30.0, 12, 4);

        assertEquals((12 + 1) * (4 + 1), sector.size(),
                "a sector is (arcSteps + 1) columns of (radialSteps + 1) points");

        double minAngle = Double.MAX_VALUE;
        double maxAngle = -Double.MAX_VALUE;
        int rimCount = 0;
        for (Location point : sector) {
            double radius = Math.hypot(point.getX(), point.getZ());
            assertTrue(radius <= 50.0 + EPSILON,
                    "no sector point may sit outside the requested radius; one sits at " + radius);
            if (Math.abs(radius - 50.0) > EPSILON) continue;
            rimCount++;
            double angle = Math.toDegrees(Math.atan2(point.getZ(), point.getX()));
            minAngle = Math.min(minAngle, angle);
            maxAngle = Math.max(maxAngle, angle);
        }

        assertEquals(12 + 1, rimCount,
                "the rim must have one point per angular division, which is what FlameVolley's "
                        + "arc rim is drawn from");
        assertEquals(30.0, minAngle, 1e-6, "the rim starts at startAngleDegrees");
        assertEquals(75.0, maxAngle, 1e-6,
                "the rim ends at startAngleDegrees + arcDegrees: 30.0 + 45.0. FlamingVolley relies on "
                        + "that span being exactly arrow_spread, because it is the spread the arrows fan across");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sectorRimContainsTheDirectionOfEveryArrowTheVolleyLaunches() throws Exception {
        // FlamingVolley.drawSpread sets startAngle = heading - arc/2 and sweeps arc degrees, while
        // rotateAroundY maps a yaw offset A to the plane angle phi - A and fireVolley hands out
        // offsets from -arc/2 to +arc/2. This asserts the two agree: the drawn rim is exactly the
        // set of directions the arrows travel, which is what lets a player read the volley and
        // step out of it. Getting the sign wrong draws the mirror image of the volley and tells
        // the player they are safe on the side they are about to be hit on.
        double heading = 30.0;
        double spread = 45.0;
        double startAngle = heading - spread / 2.0;
        int count = 3;

        List<Location> sector = (List<Location>) invokeGeometry("sectorGeometry", new Class<?>[]{Location.class, double.class, double.class, double.class, int.class, int.class}, at(0.0, 0.0, 0.0), 50.0, spread, startAngle, 12, 4);

        for (int i = 0; i < count; i++) {
            double yawOffset = -spread / 2.0 + spread * i / (count - 1);
            double arrowAngle = heading - yawOffset;

            boolean onRim = false;
            for (Location point : sector) {
                if (Math.abs(Math.hypot(point.getX(), point.getZ()) - 50.0) > 1e-6) continue;
                double angle = Math.toDegrees(Math.atan2(point.getZ(), point.getX()));
                if (Math.abs(angle - arrowAngle) < 1e-6) {
                    onRim = true;
                    break;
                }
            }
            assertTrue(onRim,
                    "arrow " + i + " is launched at plane angle " + arrowAngle
                            + " but no rim point sits there, so the drawn sector does not match the "
                            + "directions the volley actually travels");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void sectorClampsDegenerateArcsRatherThanSpinningForever() throws Exception {
        // A single arrow has no spread at all. FlamingVolley passes 0.0 for it, which must clamp
        // to the minimum arc rather than divide by zero or loop without bound.
        List<Location> sector = (List<Location>) invokeGeometry("sectorGeometry", new Class<?>[]{Location.class, double.class, double.class, double.class, int.class, int.class}, at(0.0, 0.0, 0.0), 50.0, 0.0, 0.0, 12, 4);
        assertEquals((12 + 1) * (4 + 1), sector.size(),
                "a zero-width arc must still produce a bounded, drawable sliver");
    }

    // ================================================================
    // Pure geometry: SpellVfx.ground, the ground scorch mark
    // ================================================================

    @Test
    @SuppressWarnings("unchecked")
    void groundStaysInsideItsLengthAndHalfWidth() throws Exception {
        double length = 10.0;
        double width = 6.0;
        double yawDegrees = 30.0;

        List<Location> points = (List<Location>) invokeGeometry("groundGeometry", new Class<?>[]{Location.class, double.class, double.class, int.class, int.class, double.class}, at(0.0, 0.0, 0.0), length, width, 12, 3, yawDegrees);

        assertEquals(12 * 3, points.size(), "a ground shape is samples * lanes points");

        double yaw = Math.toRadians(yawDegrees);
        double forwardX = Math.cos(yaw);
        double forwardZ = Math.sin(yaw);
        for (Location point : points) {
            double along = point.getX() * forwardX + point.getZ() * forwardZ;
            double across = -point.getX() * forwardZ + point.getZ() * forwardX;
            assertTrue(along >= -EPSILON && along <= length + EPSILON,
                    "a station sits " + along + " along the run, outside 0.." + length);
            assertTrue(Math.abs(across) <= width / 2.0 + EPSILON,
                    "a lane sits " + across + " across the run, outside the half-width "
                            + width / 2.0);
        }
    }

    @Test
    void groundRevealsByFractionOfTheShapeNotByDirection() throws Exception {
        Method revealed = SpellVfx.class.getDeclaredMethod("groundRevealed", int.class, double.class);
        revealed.setAccessible(true);

        assertEquals(0, (int) revealed.invoke(null, 12, 0.0),
                "progress 0.0 reveals nothing, so a ground mark that has not started yet draws nothing");
        assertEquals(6, (int) revealed.invoke(null, 12, 0.5),
                "advance is the FRACTION of the shape revealed, not a sign - half progress reveals "
                        + "half the stations");
        assertEquals(12, (int) revealed.invoke(null, 12, 1.0),
                "progress 1.0 reveals the whole shape");
        assertEquals(12, (int) revealed.invoke(null, 12, 5.0),
                "progress past 1.0 clamps to the full shape rather than overshooting");
        assertEquals(0, (int) revealed.invoke(null, 12, -1.0),
                "negative progress clamps to nothing rather than inverting the reveal");
    }

    // ================================================================
    // Source access
    // ================================================================

    /**
     * Calls a package-private pure-geometry method on {@link SpellVfx} by reflection, because
     * this suite is in another package.
     *
     * <p>Only the geometry twins are ever called, never a draw method: a draw method touches
     * {@code Utils} and would NPE here, which is precisely the separation the library documents.
     */
    private static Object invokeGeometry(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = SpellVfx.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Location at(double x, double y, double z) {
        return new Location(null, x, y, z);
    }

    private static int secondsToTicks(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }

    private static void assumeSourceFound(String className) {
        Assumptions.assumeTrue(sourceOf(className) != null,
                className + ".java source not reachable from " + Paths.get("").toAbsolutePath()
                        + "; the source guards are skipped and only the geometry and yml checks run");
    }

    private static String code(String className) {
        Path source = sourceOf(className);
        assumeSourceFound(className);
        try {
            return stripComments(Files.readString(source, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError(className + ".java was found at " + source + " but not readable", e);
        }
    }

    /**
     * Removes comments so that a class documenting the literal it just deleted cannot fail its own
     * grep. Both these spells name their old colours in Javadoc; without this the suite would be
     * asserting that the changelog is empty.
     */
    private static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inBlock = false;
        boolean inLine = false;
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = (i + 1 < text.length()) ? text.charAt(i + 1) : '\0';
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (c == '\\') {
                    i++;
                    if (i < text.length()) out.append(text.charAt(i));
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                out.append(c);
                if (c == '\\') {
                    i++;
                    if (i < text.length()) out.append(text.charAt(i));
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '/' && next == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '/' && next == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == '\'') {
                inChar = true;
                out.append(c);
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static Path sourceOf(String className) {
        String relative = "src/main/java/me/nagasonic/alkatraz/spells/implementation/" + className + ".java";
        Path[] candidates = {
                Paths.get(relative),
                Paths.get("core").resolve(relative),
                Paths.get("..").resolve("core").resolve(relative),
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }
}
