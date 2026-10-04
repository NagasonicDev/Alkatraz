package me.nagasonic.alkatraz.items.magic.attribute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The stat-point curve moved to {@code perPoint * points ^ exponent} in T4, so
 * every call here passes an explicit exponent. The shipped diminishing curve is
 * pinned by {@link AffinityCurveTest}; this file only covers the per-point
 * multiplier and the linear escape hatch.
 */
class StatPointsAttributeSourceTest {

    private static final double LINEAR = 1.0;

    @Test
    void affinityIsPointsTimesPerPoint() {
        assertEquals(10.0, StatPointsAttributeSource.affinityFromPoints(5, 2.0, LINEAR));
        assertEquals(0.0, StatPointsAttributeSource.affinityFromPoints(0, 2.0, LINEAR));
    }

    @Test
    void resistanceIsPointsTimesPerPoint() {
        assertEquals(4.0, StatPointsAttributeSource.resistanceFromPoints(2, 2.0, LINEAR));
        assertEquals(0.0, StatPointsAttributeSource.resistanceFromPoints(0, 1.5, LINEAR));
    }

    @Test
    void perPointScalesTheCurve() {
        assertEquals(2.0 * StatPointsAttributeSource.affinityFromPoints(37, 10.0, 0.65),
                StatPointsAttributeSource.affinityFromPoints(37, 20.0, 0.65), 1e-9,
                "doubling affinity_per_point doubles the result at every point count");
    }
}
