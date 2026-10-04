package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.util.ParticleUtils;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the pure geometry of {@link CastCircle}.
 *
 * <p>The scheduling path is deliberately NOT covered here. {@code CastCircle.start} reaches
 * {@code Bukkit.getServer().getScheduler()} and {@code Alkatraz.getInstance()}, both statics that
 * need a live server; mocking them would assert the shape of the mock rather than the behaviour of
 * the class. Cancellation and repaint cadence are verified in-game instead.
 *
 * <p>The critical assertion is {@link #matchesMagicCircleExactly(double, float, float, int)}: it
 * pins {@code CastCircle}'s pre-computed offsets to {@code ParticleUtils.magicCircle} point for point,
 * so the visual provably cannot drift away from the 36 implementations being replaced.
 */
class CastCircleGeometryTest {

    /** magicCircle emits 4 + 9 ring points, 2 x 20 square points, and 6 x 6 cluster points. */
    private static final int EXPECTED_POINT_COUNT = 89;

    /** The inner ring is drawn at frequency 80, which {@code (int) (360 / 80)} floors to four. */
    private static final int INNER_RING_POINT_COUNT = 4;

    /** magicCircle scales with {@code sizeMult = 5 / size}, so radii are fractions of {@code size}. */
    private static final double INNER_RADIUS_RATIO = 0.30;
    private static final double OUTER_RADIUS_RATIO = 0.89;

    private static final double EPSILON = 1e-9;

    @Test
    void eachSizeProducesTheSamePointCount() {
        for (double size : new double[]{2.0, 3.0, 4.0}) {
            assertEquals(EXPECTED_POINT_COUNT, CastCircle.ringOffsets(size).size(),
                    "magicCircle layout point count is size independent at size " + size);
        }
    }

    @Test
    void everyOffsetFallsBetweenTheInnerAndOuterRadius() {
        for (double size : new double[]{2.0, 3.0, 4.0}) {
            double inner = INNER_RADIUS_RATIO * size;
            double outer = OUTER_RADIUS_RATIO * size;
            List<Vector> offsets = CastCircle.ringOffsets(size);
            for (int i = 0; i < offsets.size(); i++) {
                double radius = offsets.get(i).length();
                assertTrue(radius >= inner - 1e-6 && radius <= outer + 1e-6,
                        "offset " + i + " at size " + size + " has radius " + radius
                                + ", outside [" + inner + ", " + outer + "]");
            }
        }
    }

    @Test
    void theInnerRingSitsExactlyAtThirtyPercentOfSize() {
        for (double size : new double[]{2.0, 3.0, 4.0}) {
            List<Vector> rings = List.of(CastCircle.geometryFor(size).rings);
            assertEquals(13, rings.size(), "both rings together emit thirteen points at size " + size);
            for (Vector ring : rings.subList(0, INNER_RING_POINT_COUNT)) {
                assertEquals(INNER_RADIUS_RATIO * size, ring.length(), 1e-9,
                        "inner ring radius at size " + size);
            }
        }
    }

    @Test
    void theOuterRingSitsExactlyAtEightyNinePercentOfSize() {
        for (double size : new double[]{2.0, 3.0, 4.0}) {
            List<Vector> rings = List.of(CastCircle.geometryFor(size).rings);
            for (Vector ring : rings.subList(INNER_RING_POINT_COUNT, rings.size())) {
                assertEquals(OUTER_RADIUS_RATIO * size, ring.length(), 1e-9,
                        "outer ring radius at size " + size);
            }
        }
    }

    @Test
    void geometryIsSharedPerSizeAndRebuiltForANewSize() {
        CastCircle.RingGeometry two = CastCircle.geometryFor(2.0);
        CastCircle.RingGeometry three = CastCircle.geometryFor(3.0);

        assertSame(two, CastCircle.geometryFor(2.0), "same size must reuse the cached offsets");
        assertTrue(two != three, "a different size must build its own offsets");
        assertEquals(EXPECTED_POINT_COUNT, two.pointCount(), "cached geometry point count at size 2");
        assertEquals(EXPECTED_POINT_COUNT, three.pointCount(), "cached geometry point count at size 3");
    }

    @Test
    void invalidSizesFallBackToTheDefaultLayout() {
        CastCircle.RingGeometry fallback = CastCircle.geometryFor(0.0);

        assertSame(fallback, CastCircle.geometryFor(Double.NaN), "NaN size uses the default layout");
        assertSame(fallback, CastCircle.geometryFor(-4.0), "negative size uses the default layout");
        assertEquals(EXPECTED_POINT_COUNT, fallback.pointCount(), "fallback layout point count");
        assertEquals(INNER_RADIUS_RATIO * CastCircle.defaultSize(), firstRingRadius(fallback),
                1e-9, "fallback uses the default size of 3.0");
    }

    @Test
    void placementIsARotationSoItPreservesLength() {
        Vector offset = new Vector(0.6, 0.8, 0.0);
        for (double yaw : new double[]{0.0, 37.5, 180.0, -122.0}) {
            for (double pitch : new double[]{0.0, 90.0, 180.0, -64.0}) {
                Vector placed = CastCircle.place(offset, yaw, pitch);
                assertEquals(offset.length(), placed.length(), 1e-9,
                        "rotation by yaw " + yaw + " pitch " + pitch + " must preserve length");
            }
        }
    }

    @Test
    void placementLeavesTheSourceOffsetUntouched() {
        Vector offset = new Vector(1.5, 0.25, -0.75);
        Vector before = offset.clone();

        CastCircle.place(offset, 63.0, 41.0);

        assertEquals(before.getX(), offset.getX(), EPSILON, "x unchanged by placement");
        assertEquals(before.getY(), offset.getY(), EPSILON, "y unchanged by placement");
        assertEquals(before.getZ(), offset.getZ(), EPSILON, "z unchanged by placement");
    }

    @Test
    void matchesMagicCircleExactlyAtSizeTwoLookingNorth() {
        matchesMagicCircleExactly(2.0, 0.0f, 0.0f, 0);
    }

    @Test
    void matchesMagicCircleExactlyAtSizeThreeTurnedAndPitched() {
        matchesMagicCircleExactly(3.0, 137.5f, -22.5f, 0);
    }

    @Test
    void matchesMagicCircleExactlyAtSizeFourWithARotationStep() {
        matchesMagicCircleExactly(4.0, -80.0f, 41.0f, 45);
    }

    /**
     * Asserts that every offset {@code CastCircle} produces lands on the same point as the
     * {@code magicCircle} call it replaces, in the same order.
     *
     * @param size          the circle scale under test
     * @param yaw           the caster's yaw
     * @param pitch         the caster's pitch
     * @param rotationStep  degrees added to the yaw to spin the layout
     */
    private void matchesMagicCircleExactly(double size, float yaw, float pitch, int rotationStep) {
        Location origin = new Location(null, 10.0, 70.0, -5.0, yaw, pitch);
        List<Location> expected = ParticleUtils.magicCircle(
                origin, yaw, pitch, new Vector(0.0, 0.0, 0.0), size, rotationStep);
        List<Vector> actual = CastCircle.worldOffsets(size, yaw, pitch, rotationStep);

        assertNotNull(expected, "magicCircle returned a layout to compare against");
        assertEquals(expected.size(), actual.size(),
                "CastCircle must emit exactly as many points as magicCircle at size " + size);

        // magicCircle returns absolute world coordinates; CastCircle returns offsets from the
        // circle centre, which is the origin here because a zero offset vector was passed.
        for (int i = 0; i < expected.size(); i++) {
            Location want = expected.get(i);
            Vector got = actual.get(i);
            assertEquals(want.getX() - origin.getX(), got.getX(), 1e-9,
                    "x of point " + i + " at size " + size + " yaw " + yaw + " pitch " + pitch);
            assertEquals(want.getY() - origin.getY(), got.getY(), 1e-9,
                    "y of point " + i + " at size " + size + " yaw " + yaw + " pitch " + pitch);
            assertEquals(want.getZ() - origin.getZ(), got.getZ(), 1e-9,
                    "z of point " + i + " at size " + size + " yaw " + yaw + " pitch " + pitch);
        }
    }

    private double firstRingRadius(CastCircle.RingGeometry geometry) {
        return geometry.rings[0].length();
    }
}