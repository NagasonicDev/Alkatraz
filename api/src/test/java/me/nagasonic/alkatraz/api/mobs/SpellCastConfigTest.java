package me.nagasonic.alkatraz.api.mobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SpellCastConfig, covering record accessors and range-keeping behavior.
 */
class SpellCastConfigTest {

    @Test
    void accessors_roundTripConstructorArguments() {
        SpellCastConfig config = new SpellCastConfig(2.5, 12.0, 10.0, 40);
        assertEquals(2.5, config.minCastDist());
        assertEquals(12.0, config.maxCastDist());
        assertEquals(10.0, config.castRange());
        assertEquals(40, config.cooldownTicks());
    }

    @Test
    void hasRangeKeeping_returnsFalseWhenEitherBoundIsZero() {
        assertFalse(new SpellCastConfig(0, 0, 10.0, 40).hasRangeKeeping());
        assertFalse(new SpellCastConfig(0, 5, 10.0, 40).hasRangeKeeping());
        assertFalse(new SpellCastConfig(5, 0, 10.0, 40).hasRangeKeeping());
    }

    @Test
    void hasRangeKeeping_returnsTrueWhenBothBoundsArePositive() {
        assertTrue(new SpellCastConfig(5, 12, 10.0, 40).hasRangeKeeping());
        assertTrue(new SpellCastConfig(2.5, 8.5, 10.0, 40).hasRangeKeeping());
    }
}