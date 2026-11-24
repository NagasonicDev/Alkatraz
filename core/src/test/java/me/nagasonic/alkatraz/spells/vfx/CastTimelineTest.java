package me.nagasonic.alkatraz.spells.vfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec section 5.1 and acceptance criterion 6. Pins the numbers the spectacle arc is
 * built from so a change to one circle's budget is a deliberate, reviewed edit.
 */
class CastTimelineTest {

    @Test
    void circleNineBaseSpectacleIsTwoHundredTicks() {
        assertEquals(70, CastTimeline.castTicks(9), "circle 9 casts for 70 ticks");
        assertEquals(130, CastTimeline.aftermathTicks(9), "circle 9 aftermath is 130 ticks");
        assertEquals(200, CastTimeline.totalSpectacleTicks(9), "circle 9 arc is 200 ticks");
    }

    @Test
    void noAftermathBelowCircleSix() {
        for (int circle = 0; circle <= 5; circle++) {
            assertEquals(0, CastTimeline.aftermathTicks(circle),
                    "circle " + circle + " must not gain an aftermath");
            assertFalse(CastTimeline.hasAftermath(circle),
                    "circle " + circle + " must report no aftermath");
        }
        for (int circle = 6; circle <= 9; circle++) {
            assertTrue(CastTimeline.hasAftermath(circle),
                    "circle " + circle + " must have an aftermath");
        }
    }

    @Test
    void aftermathLengthIsMonotonic() {
        int previous = -1;
        for (int circle = 0; circle <= 9; circle++) {
            int ticks = CastTimeline.aftermathTicks(circle);
            assertTrue(ticks >= previous,
                    "aftermath shrank from circle " + (circle - 1) + " to " + circle);
            previous = ticks;
        }
    }

    @Test
    void particleBudgetIsMonotonic() {
        int previous = -1;
        for (int circle = 0; circle <= 9; circle++) {
            int budget = CastTimeline.particleBudget(circle);
            assertTrue(budget >= previous,
                    "particle budget shrank from circle " + (circle - 1) + " to " + circle);
            previous = budget;
        }
    }

    @Test
    void tierBoundariesFollowTheSpecTable() {
        assertEquals(0, CastTimeline.tier(0));
        assertEquals(0, CastTimeline.tier(1));
        assertEquals(1, CastTimeline.tier(2));
        assertEquals(1, CastTimeline.tier(3));
        assertEquals(2, CastTimeline.tier(4));
        assertEquals(2, CastTimeline.tier(5));
        assertEquals(3, CastTimeline.tier(6));
        assertEquals(3, CastTimeline.tier(7));
        assertEquals(4, CastTimeline.tier(8));
        assertEquals(5, CastTimeline.tier(9));
    }

    @Test
    void tierNeverDecreasesWithCircle() {
        int previous = -1;
        for (int circle = 0; circle <= 9; circle++) {
            int tier = CastTimeline.tier(circle);
            assertTrue(tier >= previous, "tier shrank from circle " + (circle - 1) + " to " + circle);
            previous = tier;
        }
    }

    @Test
    void layerCountNeverDecreasesWithCircle() {
        int previous = -1;
        for (int circle = 0; circle <= 9; circle++) {
            int layers = CastTimeline.layerCount(circle);
            assertTrue(layers >= previous, "layer count shrank from circle " + (circle - 1) + " to " + circle);
            previous = layers;
        }
    }

    @Test
    void outOfRangeCirclesClamp() {
        assertEquals(CastTimeline.aftermathTicks(0), CastTimeline.aftermathTicks(-5));
        assertEquals(CastTimeline.aftermathTicks(9), CastTimeline.aftermathTicks(42));
        assertEquals(CastTimeline.tier(9), CastTimeline.tier(42));
    }
}
