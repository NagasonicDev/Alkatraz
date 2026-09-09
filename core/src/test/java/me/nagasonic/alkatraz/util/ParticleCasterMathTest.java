package me.nagasonic.alkatraz.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParticleCasterMathTest {

    @Test
    void fullDensityInsideDenseRadius() {
        assertEquals(1.0, ParticleCaster.scaleForDistanceSq(0.0, 144.0, 1024.0, 0.25), "at the origin");
        assertEquals(1.0, ParticleCaster.scaleForDistanceSq(10.0 * 10.0, 144.0, 1024.0, 0.25), "just inside dense radius");
        assertEquals(1.0, ParticleCaster.scaleForDistanceSq(144.0, 144.0, 1024.0, 0.25), "exactly at dense radius");
    }

    @Test
    void minimumDensityAtMaxRadius() {
        assertEquals(0.25, ParticleCaster.scaleForDistanceSq(1024.0, 144.0, 1024.0, 0.25), 1e-9, "exactly at max radius");
    }

    @Test
    void floorsToMinScaleBeyondMaxRadius() {
        assertEquals(0.25, ParticleCaster.scaleForDistanceSq(50.0 * 50.0, 144.0, 1024.0, 0.25), "beyond max radius");
    }

    @Test
    void linearFalloffAtMidpoint() {
        double dense = 12.0;
        double max = 32.0;
        double mid = (dense + max) / 2.0;
        assertEquals(0.625, ParticleCaster.scaleForDistanceSq(mid * mid, dense * dense, max * max, 0.25), 1e-9, "midpoint scale");
    }

    @Test
    void scaledCountIsFloor() {
        assertEquals(25, ParticleCaster.scaledCount(100, 0.25));
        assertEquals(10, ParticleCaster.scaledCount(20, 0.5));
        assertEquals(3, ParticleCaster.scaledCount(100, 0.03));
    }

    @Test
    void scaledCountNeverDropsBelowOne() {
        assertEquals(1, ParticleCaster.scaledCount(1, 0.05), "a single particle survives any scale");
        assertEquals(1, ParticleCaster.scaledCount(10, 0.01), "floor at one, never zero");
    }

    @Test
    void zeroCountStaysZero() {
        assertEquals(0, ParticleCaster.scaledCount(0, 0.25), "colored-particle count zero stays zero");
        assertEquals(0, ParticleCaster.scaledCount(0, 1.0));
    }

    @Test
    void degenerateConfigDenseAtLeastMaxNeverNaNs() {
        assertEquals(1.0, ParticleCaster.scaleForDistanceSq(4.0, 144.0, 144.0, 0.25), "inside dense");
        assertEquals(0.25, ParticleCaster.scaleForDistanceSq(1600.0, 144.0, 144.0, 0.25), "beyond dense uses min scale");
    }

    @Test
    void fullScaleKeepsCount() {
        assertEquals(100, ParticleCaster.scaledCount(100, 1.0));
    }
}
