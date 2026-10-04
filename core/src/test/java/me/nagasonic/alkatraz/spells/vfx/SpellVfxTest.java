package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.util.ParticleUtils;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the pure geometry of every builder in {@link SpellVfx}.
 *
 * <h2>Why this test never mocks anything</h2>
 * {@code Utils}'s static initialiser reaches {@code MinecraftVersion}'s, which calls
 * {@code Alkatraz.getInstance().getServer()} and NPEs without a live server. So this suite
 * asserts a property of the production code rather than working around it: every builder is
 * split into a public draw method (which touches {@code Utils}) and a package-private geometry
 * method (which touches nothing), and <b>only the geometry methods are called here</b>. If a
 * future change routes a geometry method through {@code Utils}, every test in this class fails
 * loudly with that NPE rather than silently needing a mock.
 *
 * <p>That is also why {@link #geometryMethodsDoNotInitialiseUtils()} exists as an explicit test
 * rather than as an implicit accident: the separation is the whole reason this suite is cheap.
 *
 * <h2>Worldless locations are deliberate</h2>
 * Every centre is built with a {@code null} world. Geometry methods must ignore the world, which
 * is what makes them testable at all. Note that {@code ParticleUtils.sphere} returns an
 * <b>empty</b> list for a worldless centre - the reason {@link SpellVfx#nova} uses
 * {@code fibonacciSphere} instead - so a geometry method built on {@code sphere} would silently
 * pass a "no points out of range" assertion.
 */
class SpellVfxTest {

    private static final double EPSILON = 1e-9;
    private static final double TOLERANCE = 1e-6;

    private static Location at(double x, double y, double z) {
        return new Location(null, x, y, z);
    }

    /**
     * Three-dimensional distance between two locations.
     *
     * <p>Not {@link Location#distance(Location)}: that method refuses to measure when either
     * side has a {@code null} world, which is exactly what a unit test's centre looks like.
     */
    private static double distance3d(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ================================================================
    // beam
    // ================================================================

    @Test
    void beamEmitsTwoRibbonPointsPerSpineStep() {
        List<Location> points = SpellVfx.beamGeometry(at(0, 0, 0), at(0, 10, 0), 4, 0.5, 1.0);

        assertEquals(2 * (4 + 1), points.size(),
                "a beam with 4 steps draws 5 spine samples with a flanking point each side");
    }

    @Test
    void beamSpineRunsFromOriginToTarget() {
        Location origin = at(0, 64, 0);
        Location target = at(8, 64, 0);

        List<Location> points = SpellVfx.beamGeometry(origin, target, 8, 0.0, 0.0);

        assertEquals(0.0, horizontalDistance(points.get(0), origin), TOLERANCE,
                "with zero width both ribbon points collapse onto the first spine sample, at the origin");
        assertEquals(0.0, horizontalDistance(points.get(points.size() - 1), target), TOLERANCE,
                "the last ribbon point must land on the target");
        for (int i = 0; i < points.size(); i++) {
            assertEquals(64.0, points.get(i).getY(), TOLERANCE,
                    "beam point " + i + " must stay on the origin/target plane");
        }
    }

    @Test
    void beamClampsAZeroStepCountInsteadOfDividingByZero() {
        List<Location> points = SpellVfx.beamGeometry(at(0, 0, 0), at(5, 0, 0), 0, 0.2, 0.2);

        assertEquals(4, points.size(),
                "zero steps clamps to one step, which is two spine samples and two ribbon points");
    }

    @Test
    void beamWidthInterpolatesAndNeverGoesNegative() {
        assertEquals(1.0, SpellVfx.beamWidthAt(0.0, 1.0, 0.25), EPSILON, "width at the origin is the start width");
        assertEquals(0.25, SpellVfx.beamWidthAt(1.0, 1.0, 0.25), EPSILON, "width at the target is the end width");
        assertEquals(0.625, SpellVfx.beamWidthAt(0.5, 1.0, 0.25), EPSILON, "width interpolates linearly");
        assertEquals(0.0, SpellVfx.beamWidthAt(0.5, 0.5, -2.0), EPSILON,
                "a negative interpolated width clamps at zero rather than inverting the ribbon");
    }

    @Test
    void beamRibbonPairsArePerpendicularToTheTravelDirection() {
        List<Location> points = SpellVfx.beamGeometry(at(0, 0, 0), at(0, 10, 0), 3, 2.0, 2.0);

        // The pair for a spine sample straddles it: midpoint on the spine, separation the width.
        for (int i = 0; i < points.size(); i += 2) {
            Location left = points.get(i);
            Location right = points.get(i + 1);
            double midpointY = (left.getY() + right.getY()) * 0.5;
            assertTrue(Math.abs(midpointY - left.getY()) < TOLERANCE,
                    "ribbon pair " + (i / 2) + " must straddle its spine sample symmetrically");
            assertEquals(2.0, distance3d(left, right), TOLERANCE,
                    "ribbon pair " + (i / 2) + " separation must equal the requested width");
        }
    }

    @Test
    void beamWithAVerticalTravelStillHasAWidth() {
        // The degenerate case a fixed-axis ribbon collapses on: travelling straight up.
        List<Location> points = SpellVfx.beamGeometry(at(0, 0, 0), at(0, 10, 0), 2, 1.0, 1.0);

        assertEquals(6, points.size(), "a vertical beam still draws its full ribbon");
        assertEquals(1.0, distance3d(points.get(0), points.get(1)), TOLERANCE,
                "the first ribbon pair is one block apart, so a vertical beam is not a flat line");
    }

    // ================================================================
    // projectile
    // ================================================================

    @Test
    void projectileHeadPutsEveryPointExactlyOnTheClusterRadius() {
        Location head = at(3, 4, 5);

        List<Location> points = SpellVfx.projectileHead(head, 0.35, 12);

        assertEquals(12, points.size(), "the head cluster draws exactly the requested points");
        for (int i = 0; i < points.size(); i++) {
            assertEquals(0.35, distance3d(points.get(i), head), TOLERANCE,
                    "head particle " + i + " must sit exactly on the cluster radius");
        }
    }

    @Test
    void projectileHeadWithZeroPointsDrawsNothing() {
        assertTrue(SpellVfx.projectileHead(at(0, 0, 0), 0.4, 0).isEmpty(),
                "a zero cluster count must yield an empty cluster, not a default one");
    }

    @Test
    void projectileTailRunsBackwardsAlongTheTravelDirection() {
        Location head = at(0, 64, 0);
        Vector forward = new Vector(0.0, 0.0, 1.0);

        List<Location> tail = SpellVfx.projectileTail(head, forward, 4, 2.0);

        assertEquals(4, tail.size(), "the tail draws exactly the requested points");
        for (int i = 0; i < tail.size(); i++) {
            assertTrue(tail.get(i).getZ() < head.getZ(),
                    "tail point " + i + " must sit behind the head along -Z, not ahead of it");
            assertTrue(horizontalDistance(tail.get(i), head) <= 2.0 + TOLERANCE,
                    "tail point " + i + " must stay within the horizontal tail length");
            assertTrue(tail.get(i).getY() < head.getY(),
                    "tail point " + i + " must drift below the head so the trail reads as lagging");
        }
        // The tail widens away from the head, so later points are further back.
        assertTrue(tail.get(3).getZ() < tail.get(0).getZ(),
                "the last tail particle must be the furthest behind the head");
    }

    @Test
    void projectileTailWithAZeroDirectionFallsBackToStraightUp() {
        List<Location> tail = SpellVfx.projectileTail(at(0, 64, 0), new Vector(0, 0, 0), 3, 1.0);

        assertEquals(3, tail.size(), "a zero direction still draws the tail");
        for (int i = 0; i < tail.size(); i++) {
            assertTrue(tail.get(i).getY() < 64.0,
                    "a zero direction falls back to +Y, so the tail hangs below the head at point " + i);
        }
    }

    // ================================================================
    // nova
    // ================================================================

    @Test
    void novaShellPutsEveryPointExactlyOnTheShellRadius() {
        Location center = at(0, 64, 0);

        List<Location> shell = SpellVfx.novaGeometry(center, 2.5, 40);

        assertEquals(40, shell.size(), "the shell draws exactly the requested points");
        for (int i = 0; i < shell.size(); i++) {
            assertEquals(2.5, distance3d(shell.get(i), center), TOLERANCE,
                    "shell particle " + i + " must sit exactly on the shell radius");
        }
    }

    @Test
    void novaGeometryIsNotEmptyForAWorldlessCentre() {
        // ParticleUtils.sphere would return an empty list here; fibonacciSphere must not.
        List<Location> shell = SpellVfx.novaGeometry(at(0, 64, 0), 1.0, 10);

        assertFalse(shell.isEmpty(),
                "novaGeometry must not depend on the centre having a world, or it silently draws nothing");
    }

    @Test
    void novaInnerRingSitsAtHalfTheShellRadiusAndIsFlat() {
        Location center = at(1, 2, 3);

        List<Location> ring = SpellVfx.novaInnerRing(center, 4.0, 40);

        assertEquals(20, ring.size(), "the inner ring uses half the shell count");
        for (int i = 0; i < ring.size(); i++) {
            assertEquals(2.0, horizontalDistance(ring.get(i), center), TOLERANCE,
                    "inner ring particle " + i + " must sit at half the shell radius");
            assertEquals(2.0, ring.get(i).getY(), EPSILON,
                    "inner ring particle " + i + " must stay in the horizontal plane through the centre");
        }
    }

    @Test
    void novaGeometryWithZeroPointsDrawsNothing() {
        assertTrue(SpellVfx.novaGeometry(at(0, 0, 0), 3.0, 0).isEmpty(),
                "a zero shell count must yield an empty shell");
    }

    // ================================================================
    // ring
    // ================================================================

    @Test
    void ringEmitsExactlyItsPointCountAtAFlatRadius() {
        Location center = at(5, 70, -5);

        List<Location> ring = SpellVfx.ringGeometry(center, 3.0, 24, 0.0, 0.0);

        assertEquals(24, ring.size(), "the ring draws exactly the requested points");
        for (int i = 0; i < ring.size(); i++) {
            assertEquals(3.0, horizontalDistance(ring.get(i), center), TOLERANCE,
                    "ring particle " + i + " must sit exactly on the ring radius");
            assertEquals(70.0, ring.get(i).getY(), EPSILON,
                    "ring particle " + i + " must stay flat at the centre's y");
        }
    }

    @Test
    void ringClampsToThreePointsSoItAlwaysCloses() {
        assertEquals(3, SpellVfx.ringGeometry(at(0, 0, 0), 1.0, 1, 0.0, 0.0).size(),
                "one point cannot close a ring, so it clamps up to three");
        assertEquals(3, SpellVfx.ringGeometry(at(0, 0, 0), 1.0, 0, 0.0, 0.0).size(),
                "zero points must clamp to a closed triangle rather than divide by zero");
    }

    @Test
    void ringHonoursTheVerticalOffset() {
        List<Location> ring = SpellVfx.ringGeometry(at(0, 64, 0), 2.0, 12, 0.0, 1.5);

        for (int i = 0; i < ring.size(); i++) {
            assertEquals(65.5, ring.get(i).getY(), EPSILON,
                    "ring particle " + i + " must be lifted by the requested vertical offset");
        }
    }

    @Test
    void ringRadiusGrowsForAPositiveSign() {
        assertEquals(2.0, SpellVfx.ringRadius(2.0, 0.0, 1.0), EPSILON,
                "an expanding ring starts at the base radius");
        assertEquals(3.0, SpellVfx.ringRadius(2.0, 0.5, 1.0), EPSILON,
                "an expanding ring grows with progress");
        assertEquals(4.0, SpellVfx.ringRadius(2.0, 1.0, 1.0), EPSILON,
                "an expanding ring doubles at full progress");
    }

    @Test
    void ringRadiusShrinksForANegativeSign() {
        assertEquals(1.0, SpellVfx.ringRadius(2.0, 0.5, -1.0), EPSILON,
                "a contracting ring halves at half progress");
        assertEquals(0.0, SpellVfx.ringRadius(2.0, 1.0, -1.0), EPSILON,
                "a contracting ring collapses to zero at full progress");
    }

    @Test
    void ringRadiusNeverGoesNegativeOrPastFullProgress() {
        assertEquals(4.0, SpellVfx.ringRadius(2.0, 5.0, 1.0), EPSILON,
                "progress beyond 1.0 clamps to full progress, which for an expanding ring doubles the base");
        assertEquals(0.0, SpellVfx.ringRadius(2.0, 5.0, -1.0), EPSILON,
                "an over-clamped contraction stays at zero rather than going negative");
        assertEquals(0.0, SpellVfx.ringRadius(-4.0, 0.0, 1.0), EPSILON,
                "a negative base radius is treated as zero, so the ring collapses rather than inverting");
    }

    // ================================================================
    // cone / sector
    // ================================================================

    @Test
    void sectorEmitsAGridOfArcAndRadialSteps() {
        List<Location> sector = SpellVfx.sectorGeometry(at(0, 64, 0), 4.0, 90.0, 0.0, 6, 3);

        assertEquals((6 + 1) * (3 + 1), sector.size(),
                "the sector is a grid of (arcSteps + 1) columns by (radialSteps + 1) rows");
    }

    @Test
    void sectorStaysWithinItsRadiusAndSpansTheRequestedArc() {
        Location apex = at(0, 64, 0);

        List<Location> sector = SpellVfx.sectorGeometry(apex, 5.0, 120.0, 0.0, 12, 4);

        double minAngle = Double.MAX_VALUE;
        double maxAngle = -Double.MAX_VALUE;
        for (int i = 0; i < sector.size(); i++) {
            Location point = sector.get(i);
            double reach = horizontalDistance(point, apex);
            assertTrue(reach <= 5.0 + TOLERANCE,
                    "sector particle " + i + " at reach " + reach + " must stay within the 5 block radius");
            double angle = Math.toDegrees(Math.atan2(point.getZ() - apex.getZ(), point.getX() - apex.getX()));
            if (reach > TOLERANCE) {
                minAngle = Math.min(minAngle, angle);
                maxAngle = Math.max(maxAngle, angle);
            }
        }
        assertEquals(120.0, maxAngle - minAngle, 1e-6,
                "the sector must span exactly the requested 120 degree arc");
    }

    @Test
    void sectorIncludesItsApex() {
        Location apex = at(2, 64, 2);

        List<Location> sector = SpellVfx.sectorGeometry(apex, 3.0, 60.0, 0.0, 4, 3);

        boolean hasApex = sector.stream().anyMatch(p -> horizontalDistance(p, apex) < TOLERANCE
                && Math.abs(p.getY() - apex.getY()) < TOLERANCE);
        assertTrue(hasApex, "a sector's first radial station is its apex, so a filled sector must include it");
    }

    @Test
    void sectorClampsDegenerateStepCountsAndArcWidths() {
        assertEquals(4, SpellVfx.sectorGeometry(at(0, 0, 0), 1.0, 90.0, 0.0, 0, 0).size(),
                "zero steps clamp to one each, giving a 2x2 grid");
        assertFalse(SpellVfx.sectorGeometry(at(0, 0, 0), 1.0, 0.0, 0.0, 4, 4).isEmpty(),
                "a zero-degree arc clamps to a minimum width rather than dividing by zero");
        assertFalse(SpellVfx.sectorGeometry(at(0, 0, 0), 1.0, 720.0, 0.0, 4, 4).isEmpty(),
                "an arc of 360 or more clamps to a full circle rather than winding twice");
    }

    // ================================================================
    // column
    // ================================================================

    @Test
    void columnEmitsLayersTimesPointsPerLayer() {
        List<Location> column = SpellVfx.columnGeometry(at(0, 64, 0), 4.0, 1.0, 5, 8, 0.0);

        assertEquals(5 * 8, column.size(), "the column draws layers * pointsPerLayer points");
    }

    @Test
    void columnSpansItsHeightAndRadius() {
        Location base = at(1, 64, 1);

        List<Location> column = SpellVfx.columnGeometry(base, 4.0, 1.0, 5, 8, 0.0);

        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < column.size(); i++) {
            Location point = column.get(i);
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
            assertEquals(1.0, horizontalDistance(point, base), TOLERANCE,
                    "column particle " + i + " must sit exactly on the column radius");
        }
        assertEquals(64.0, minY, TOLERANCE, "the first slice sits on the column foot");
        assertEquals(68.0, maxY, TOLERANCE, "the last slice sits at the requested height");
    }

    @Test
    void columnLayersAreOrderedBottomUpSoAPrefixRevealsUpward() {
        List<Location> column = SpellVfx.columnGeometry(at(0, 64, 0), 4.0, 1.0, 5, 1, 0.0);

        for (int i = 0; i < column.size(); i++) {
            assertEquals(64.0 + i, column.get(i).getY(), TOLERANCE,
                    "slice " + i + " must be one step above the previous one, so a growing prefix rises");
        }
    }

    @Test
    void columnPhaseStepRotatesEachSliceAgainstTheOneBelow() {
        // Two slices, four points each: slice 0 spans 0/90/180/270 degrees, slice 1 is the same
        // four positions rotated by the requested 90 degree phase step.
        List<Location> column = SpellVfx.columnGeometry(at(0, 64, 0), 4.0, 1.0, 2, 4, Math.PI / 2);

        double lowerAngle = angleOf(column.get(0));
        double upperAngle = angleOf(column.get(4));

        assertEquals(64.0, column.get(0).getY(), TOLERANCE, "the first slice is on the column foot");
        assertEquals(68.0, column.get(4).getY(), TOLERANCE, "the second slice is at the requested height");
        assertEquals(90.0, normalise(upperAngle - lowerAngle), 1e-6,
                "each slice must be rotated by exactly the requested phase step relative to the one below");
    }

    private static double angleOf(Location point) {
        return Math.toDegrees(Math.atan2(point.getZ(), point.getX()));
    }

    private static double normalise(double degrees) {
        double value = degrees % 360.0;
        if (value < 0.0) value += 360.0;
        return value;
    }

    @Test
    void columnWithZeroLayersDrawsNothing() {
        assertTrue(SpellVfx.columnGeometry(at(0, 64, 0), 4.0, 1.0, 0, 8, 0.0).isEmpty(),
                "zero layers means an unrevealed column, which must draw nothing");
    }

    @Test
    void columnRevealCountTracksProgressAndIsBounded() {
        assertEquals(0, SpellVfx.columnRevealed(5, 0.0), "no progress reveals no slices");
        assertEquals(3, SpellVfx.columnRevealed(5, 0.5), "half progress rounds to half the slices");
        assertEquals(5, SpellVfx.columnRevealed(5, 1.0), "full progress reveals every slice");
        assertEquals(5, SpellVfx.columnRevealed(5, 9.0), "over-progress clamps to the slice count");
        assertEquals(0, SpellVfx.columnRevealed(0, 1.0), "a column with no slices reveals none at full progress");
    }

    // ================================================================
    // orbit
    // ================================================================

    @Test
    void orbitEmitsExactlyOnePointPerBody() {
        List<Location> orbit = SpellVfx.orbitGeometry(at(0, 64, 0), 1.0, 3.0, 6, 0.0);

        assertEquals(6, orbit.size(), "the orbit draws exactly one point per body");
    }

    @Test
    void orbitBodiesSpreadAcrossTheRadiusRangeAndAreFlat() {
        Location center = at(0, 64, 0);

        List<Location> orbit = SpellVfx.orbitGeometry(center, 1.0, 3.0, 5, 0.0);

        for (int i = 0; i < orbit.size(); i++) {
            double reach = horizontalDistance(orbit.get(i), center);
            assertTrue(reach >= 1.0 - TOLERANCE && reach <= 3.0 + TOLERANCE,
                    "body " + i + " at radius " + reach + " must lie between the min and max radius");
            assertEquals(64.0, orbit.get(i).getY(), EPSILON, "body " + i + " must be flat");
        }
        assertEquals(1.0, horizontalDistance(orbit.get(0), center), TOLERANCE,
                "the first body sits on the inner radius");
        assertEquals(3.0, horizontalDistance(orbit.get(orbit.size() - 1), center), TOLERANCE,
                "the last body sits on the outer radius");
    }

    @Test
    void orbitSwapsInvertedRadiusBounds() {
        List<Location> orbit = SpellVfx.orbitGeometry(at(0, 64, 0), 3.0, 1.0, 3, 0.0);

        for (int i = 0; i < orbit.size(); i++) {
            double reach = horizontalDistance(orbit.get(i), at(0, 64, 0));
            assertTrue(reach >= 1.0 - TOLERANCE && reach <= 3.0 + TOLERANCE,
                    "body " + i + " at radius " + reach + " must lie between the two bounds whichever order they were given in");
        }
    }

    @Test
    void orbitWithZeroBodiesDrawsNothing() {
        assertTrue(SpellVfx.orbitGeometry(at(0, 64, 0), 1.0, 3.0, 0, 0.0).isEmpty(),
                "zero bodies means no orbits, which must draw nothing");
    }

    // ================================================================
    // field / dome
    // ================================================================

    @Test
    void fieldShellStaysInsideTheDomeBounds() {
        Location base = at(0, 64, 0);

        List<Location> shell = SpellVfx.fieldGeometry(base, 4.0, 3.0, 30, 0, 0.0);

        assertEquals(30, shell.size(), "the shell draws exactly the requested points");
        for (int i = 0; i < shell.size(); i++) {
            Location point = shell.get(i);
            assertTrue(horizontalDistance(point, base) <= 4.0 + TOLERANCE,
                    "shell particle " + i + " must stay within the dome radius");
            assertTrue(point.getY() >= 64.0 - TOLERANCE && point.getY() <= 67.0 + TOLERANCE,
                    "shell particle " + i + " must stay between the ground and the dome height");
        }
    }

    @Test
    void fieldInteriorVolumeStaysInsideTheShell() {
        Location base = at(0, 64, 0);

        List<Location> fill = SpellVfx.fieldGeometry(base, 4.0, 3.0, 0, 20, 0.0);

        assertEquals(20, fill.size(), "the interior draws exactly the requested points");
        for (int i = 0; i < fill.size(); i++) {
            assertTrue(horizontalDistance(fill.get(i), base) <= 2.0 + TOLERANCE,
                    "interior particle " + i + " must stay inside half the dome radius");
            assertTrue(fill.get(i).getY() >= 64.0 - TOLERANCE && fill.get(i).getY() <= 67.0 + TOLERANCE,
                    "interior particle " + i + " must stay under the dome height");
        }
    }

    @Test
    void fieldWithNeitherShellNorInteriorDrawsNothing() {
        assertTrue(SpellVfx.fieldGeometry(at(0, 64, 0), 4.0, 3.0, 0, 0, 0.0).isEmpty(),
                "a field with no requested points must draw nothing rather than defaulting");
    }

    @Test
    void fieldWithAZeroRadiusAndHeightCollapsesToItsBase() {
        List<Location> shell = SpellVfx.fieldGeometry(at(1, 64, 1), 0.0, 0.0, 8, 0, 0.0);

        for (int i = 0; i < shell.size(); i++) {
            assertEquals(0.0, distance3d(shell.get(i), at(1, 64, 1)), TOLERANCE,
                    "a zero-radius zero-height dome degenerates to its base point " + i);
        }
    }

    // ================================================================
    // ground
    // ================================================================

    @Test
    void groundEmitsSamplesTimesLanes() {
        List<Location> ground = SpellVfx.groundGeometry(at(0, 64, 0), 6.0, 2.0, 8, 3, 0.0);

        assertEquals(8 * 3, ground.size(), "the ground shape draws samples * lanes points");
    }

    @Test
    void groundRunsAlongItsYawWithinItsLengthAndWidth() {
        Location centre = at(0, 64, 0);

        List<Location> ground = SpellVfx.groundGeometry(centre, 6.0, 2.0, 6, 3, 0.0);

        for (int i = 0; i < ground.size(); i++) {
            Location point = ground.get(i);
            assertTrue(Math.abs(point.getX()) <= 6.0 + TOLERANCE,
                    "ground particle " + i + " at x " + point.getX() + " must stay within the run length");
            assertTrue(Math.abs(point.getZ()) <= 1.0 + TOLERANCE,
                    "ground particle " + i + " at z " + point.getZ() + " must stay within half the width");
            assertEquals(64.0, point.getY(), EPSILON, "ground particle " + i + " must be flat on the ground");
        }
        assertEquals(6.0, ground.get(ground.size() - 1).getX(), TOLERANCE,
                "the final station sits at the requested length, so an advancing prefix grows outward");
    }

    @Test
    void groundRotatesWithItsYaw() {
        // Yaw is measured anticlockwise from +X, so yaw 0 runs along +X and yaw 90 along +Z.
        List<Location> east = SpellVfx.groundGeometry(at(0, 64, 0), 4.0, 1.0, 2, 1, 0.0);
        List<Location> north = SpellVfx.groundGeometry(at(0, 64, 0), 4.0, 1.0, 2, 1, 90.0);

        assertEquals(4.0, east.get(east.size() - 1).getX(), TOLERANCE, "yaw 0 runs along +X");
        assertEquals(0.0, east.get(east.size() - 1).getZ(), TOLERANCE, "yaw 0 has no lateral reach");
        assertEquals(4.0, north.get(north.size() - 1).getZ(), TOLERANCE, "yaw 90 runs along +Z");
        assertEquals(0.0, north.get(north.size() - 1).getX(), TOLERANCE, "yaw 90 has no lateral reach");
    }

    @Test
    void groundWithZeroSamplesDrawsNothing() {
        assertTrue(SpellVfx.groundGeometry(at(0, 64, 0), 6.0, 2.0, 0, 3, 0.0).isEmpty(),
                "zero samples means an unrevealed ground shape, which must draw nothing");
    }

    @Test
    void groundRevealCountAdvancesAndRetracts() {
        assertEquals(0, SpellVfx.groundRevealed(8, 0.0), "no advance reveals no stations");
        assertEquals(4, SpellVfx.groundRevealed(8, 0.5), "half advance reveals half the stations");
        assertEquals(8, SpellVfx.groundRevealed(8, 1.0), "full advance reveals every station");
        assertEquals(8, SpellVfx.groundRevealed(8, 4.0), "over-advance clamps to the station count");
        assertEquals(4, SpellVfx.groundRevealed(8, 0.5), "the same value retracts as readily as it advances");
        assertEquals(0, SpellVfx.groundRevealed(0, 1.0), "a shape with no stations reveals none");
    }

    // ================================================================
    // selfAura
    // ================================================================

    @Test
    void selfAuraEmitsTwoCounterRotatingRings() {
        Location subject = at(0, 64, 0);

        List<Location> aura = SpellVfx.selfAuraGeometry(subject, 2.0, 16, 0.0);

        assertEquals(2 * 16, aura.size(), "the aura draws an outer ring and an inner ring of equal count");
        for (int i = 0; i < 16; i++) {
            assertEquals(2.0, horizontalDistance(aura.get(i), subject), TOLERANCE,
                    "outer ring particle " + i + " must sit on the aura radius");
            assertEquals(1.1, horizontalDistance(aura.get(16 + i), subject), TOLERANCE,
                    "inner ring particle " + i + " must sit at 0.55 of the aura radius");
        }
    }

    @Test
    void selfAuraRingCountClampsSoBothRingsClose() {
        assertEquals(6, SpellVfx.selfAuraGeometry(at(0, 64, 0), 1.0, 1, 0.0).size(),
                "a single point cannot close a ring, so both aura rings clamp to three");
    }

    @Test
    void selfAuraGeometryIsEmptyWithoutASubjectLocation() {
        assertTrue(SpellVfx.selfAuraGeometry(null, 2.0, 16, 0.0).isEmpty(),
                "an aura with no subject location has no centre to draw around");
    }

    // ================================================================
    // channelGlyphs
    // ================================================================

    @Test
    void channelGlyphsEmitsOneMarkerPerGlyph() {
        List<Location> glyphs = SpellVfx.channelGlyphGeometry(at(0, 64, 0), 4.0, 6, 0.0);

        assertEquals(6, glyphs.size(), "six glyphs is the hex both original implementations drew");
        for (int i = 0; i < glyphs.size(); i++) {
            assertEquals(3.4, horizontalDistance(glyphs.get(i), at(0, 64, 0)), TOLERANCE,
                    "glyph " + i + " must sit at 0.85 of the outer ring radius");
            assertEquals(64.0, glyphs.get(i).getY(), EPSILON, "glyph " + i + " must lie on the floor");
        }
    }

    @Test
    void channelGlyphsWithNoGlyphsDrawsOnlyRings() {
        assertTrue(SpellVfx.channelGlyphGeometry(at(0, 64, 0), 4.0, 0, 0.0).isEmpty(),
                "a channel with no glyph markers must still be drawable as bare rings");
    }

    // ================================================================
    // Helpers and the Utils-init guard
    // ================================================================

    @Test
    void pointCountsClampUpwardAndOptionalCountsClampToZero() {
        assertEquals(1, SpellVfx.clampPoints(0), "a zero point count clamps up to one usable point");
        assertEquals(1, SpellVfx.clampPoints(-5), "a negative point count clamps up to one usable point");
        assertEquals(7, SpellVfx.clampPoints(7), "a valid point count passes through unchanged");
        assertEquals(0, SpellVfx.clampOptional(0), "an optional count may legitimately be zero");
        assertEquals(0, SpellVfx.clampOptional(-3), "an optional count clamps at zero, never negative");
    }

    @Test
    void paletteOfToleratesANullSpell() {
        assertEquals(me.nagasonic.alkatraz.api.Element.NONE, SpellVfx.paletteOf(null).element(),
                "a null spell must resolve to the neutral palette rather than throwing mid-cast");
    }

    @Test
    void worldOfRejectsWorldlessAndNullLocations() {
        assertNull(SpellVfx.worldOf(null), "a null location has no world");
        assertNull(SpellVfx.worldOf(at(0, 0, 0)),
                "a worldless location has no world, which the draw methods must check");
    }

    @Test
    void perpendicularIsAlwaysUnitLengthAndOrthogonalToTheInput() {
        Vector[] directions = {
                new Vector(0, 1, 0),
                new Vector(0, 0, 1),
                new Vector(1, 1, 1),
                new Vector(-0.3, 0.2, -0.9),
                new Vector(1, 0, 0),
        };
        for (Vector direction : directions) {
            Vector perp = SpellVfx.perpendicularTo(direction);
            assertEquals(1.0, perp.length(), TOLERANCE,
                    "the perpendicular to " + direction + " must be a unit vector");
            assertEquals(0.0, direction.dot(perp), TOLERANCE,
                    "the perpendicular to " + direction + " must be orthogonal to it");
        }
    }

    @Test
    void perpendicularToAZeroVectorStillReturnsAUnitVector() {
        Vector perp = SpellVfx.perpendicularTo(new Vector(0, 0, 0));

        assertEquals(1.0, perp.length(), TOLERANCE,
                "a zero direction has no plane to be perpendicular to, so a usable unit fallback is returned");
    }

    @Test
    void geometryMethodsDoNotInitialiseUtils() {
        // Reaching Utils' static initialiser calls MinecraftVersion's, which calls
        // Alkatraz.getInstance().getServer() and NPEs. Every test above already ran the geometry
        // methods, so if any of them touched Utils this class would never have got this far.
        // That is the property under test; there is nothing to mock and nothing to assert.
        //
        // What IS worth asserting is the other half of the same design: SpellVfx carries no
        // mutable static state, so two concurrent casts cannot share a scratch buffer. Forcing
        // the class to load here is harmless - it has no static initialiser of its own.
        assertNotNull(SpellVfx.class.getName(),
                "SpellVfx must load without initialising Utils, or the geometry half is not separable");
    }

    @Test
    void spellVfxHoldsNoMutableStaticState() {
        Field[] fields = SpellVfx.class.getDeclaredFields();
        assertTrue(fields.length > 0, "SpellVfx declares its clamp constants as static finals");
        for (Field field : fields) {
            assertTrue(Modifier.isStatic(field.getModifiers()),
                    "SpellVfx must be stateless, but declares the instance field " + field.getName());
            assertTrue(Modifier.isFinal(field.getModifiers()),
                    "SpellVfx field " + field.getName() + " must be final, or a shared builder "
                            + "could be mutated by a concurrent cast");
        }
    }

    // ================================================================
    // createHelix / spiral guard
    // ================================================================

    /**
     * {@link ParticleUtils#createHelix(Location, int, double, double, int)} divides by
     * {@code pointsPerLoop} and {@link ParticleUtils#spiral(Location, double, double, double, double, double)}
     * divides by {@code degreesPerPoint}; with a zero argument either loops forever and takes
     * the server thread with it. {@link SpellVfx} therefore computes its own geometry rather
     * than calling them, and this guard fails the build if either call is ever reintroduced with
     * a parameter that is not provably clamped.
     */
    @Test
    void neverCallsTheTwoParticleUtilsHelpersThatLoopForeverOnZero() {
        Path source = sourceOfSpellVfx();
        Assumptions.assumeTrue(source != null,
                "SpellVfx.java source not reachable from " + Paths.get("").toAbsolutePath()
                        + "; the source guard is skipped and only the behavioural clamps below are checked");

        String text;
        try {
            text = Files.readString(source, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("SpellVfx.java was found at " + source + " but could not be read", e);
        }

        String code = stripComments(text);

        // Guard against a stripComments() that silently blanks everything: if the stripped
        // source no longer contains real code, the two assertions below would pass vacuously.
        assertTrue(code.contains("static List<Location> ringGeometry"),
                "stripComments must leave the code intact, or this guard tests nothing");

        assertFalse(code.contains("createHelix("),
                "SpellVfx must not call ParticleUtils.createHelix, which loops forever when "
                        + "pointsPerLoop is not positive");
        assertFalse(code.contains("spiral("),
                "SpellVfx must not call ParticleUtils.spiral, which loops forever when "
                        + "degreesPerPoint is not positive");
    }

    /**
     * Removes Javadoc and line comments, so the guard matches only real call sites. Without this,
     * the class Javadoc - which has to <i>name</i> the two helpers in order to explain why they
     * are banned - would trip the guard on its own documentation.
     *
     * @param source the raw file contents
     * @return the same source with comment bodies blanked out
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inBlock = false;
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (!inBlock && (trimmed.startsWith("*") || trimmed.startsWith("//"))) {
                out.append('\n');
                continue;
            }
            if (!inBlock && trimmed.startsWith("/*")) {
                inBlock = !trimmed.contains("*/");
                out.append('\n');
                continue;
            }
            if (inBlock) {
                if (trimmed.endsWith("*/")) inBlock = false;
                out.append('\n');
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * The behavioural half of the same guard: for the parameters a caller will actually pass,
     * every builder terminates and produces a bounded list. A builder that forwarded a raw
     * {@code pointsPerLoop} straight into {@code createHelix} would hang here rather than fail,
     * which is why the source scan above exists alongside it.
     */
    @Test
    void everyBuilderTerminatesOnDegenerateParameters() {
        Location centre = at(0, 64, 0);

        assertEquals(4, SpellVfx.beamGeometry(centre, at(5, 70, 5), 0, 0.0, 0.0).size(),
                "a beam with zero steps must terminate at its clamped single step");
        assertTrue(SpellVfx.projectileHead(centre, 0.5, 0).isEmpty(),
                "a projectile with a zero cluster count must terminate immediately");
        assertTrue(SpellVfx.projectileTail(centre, new Vector(0, 0, 0), 0, 1.0).isEmpty(),
                "a projectile with a zero tail count must terminate immediately");
        assertTrue(SpellVfx.novaGeometry(centre, 1.0, 0).isEmpty(),
                "a nova with a zero shell count must terminate immediately");
        assertEquals(3, SpellVfx.ringGeometry(centre, 1.0, 0, 0.0, 0.0).size(),
                "a ring with a zero point count must terminate immediately at its clamped minimum");
        assertEquals(4, SpellVfx.sectorGeometry(centre, 1.0, 90.0, 0.0, 0, 0).size(),
                "a sector with zero steps must terminate immediately at its clamped minimum grid");
        assertTrue(SpellVfx.columnGeometry(centre, 4.0, 1.0, 0, 8, 0.0).isEmpty(),
                "a column with zero layers must terminate immediately");
        assertEquals(5, SpellVfx.columnGeometry(centre, 4.0, 1.0, 5, 0, 0.0).size(),
                "a column with zero points per layer must clamp to one per slice");
        assertTrue(SpellVfx.orbitGeometry(centre, 1.0, 3.0, 0, 0.0).isEmpty(),
                "an orbit with zero bodies must terminate immediately");
        assertTrue(SpellVfx.fieldGeometry(centre, 4.0, 3.0, 0, 0, 0.0).isEmpty(),
                "a field with no points must terminate immediately");
        assertTrue(SpellVfx.groundGeometry(centre, 6.0, 2.0, 0, 3, 0.0).isEmpty(),
                "a ground shape with zero samples must terminate immediately");
        assertEquals(5, SpellVfx.groundGeometry(centre, 6.0, 2.0, 5, 0, 0.0).size(),
                "a ground shape with zero lanes must clamp to one lane per station");
        assertEquals(6, SpellVfx.selfAuraGeometry(centre, 1.0, 0, 0.0).size(),
                "an aura with a zero point count must clamp to three per ring");
        assertTrue(SpellVfx.channelGlyphGeometry(centre, 4.0, 0, 0.0).isEmpty(),
                "a channel with zero glyphs must terminate immediately");
    }

    private static Path sourceOfSpellVfx() {
        String relative = "src/main/java/me/nagasonic/alkatraz/spells/vfx/SpellVfx.java";
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