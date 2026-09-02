package me.nagasonic.alkatraz.spells.types.barrier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrierCombatTest {

    @Test
    void damageKillingExactlyBreaksButIsCounteredBelowThreshold() {
        BarrierCombat.BarrierHit hit = BarrierCombat.resolveHit(20, 20, 1.25);
        assertEquals(0, hit.newHitpoints(), 1e-9);
        assertTrue(hit.broken());
        assertTrue(hit.countered());
        assertFalse(hit.punchedThrough());
    }

    @Test
    void survivingHitIsAlwaysCountered() {
        BarrierCombat.BarrierHit hit = BarrierCombat.resolveHit(20, 10, 1.25);
        assertEquals(10, hit.newHitpoints(), 1e-9);
        assertFalse(hit.broken());
        assertTrue(hit.countered());
        assertFalse(hit.punchedThrough());
    }

    @Test
    void overkillReachingThresholdPunchesThrough() {
        BarrierCombat.BarrierHit hit = BarrierCombat.resolveHit(20, 30, 1.25);
        assertEquals(0, hit.newHitpoints(), 1e-9);
        assertTrue(hit.broken());
        assertFalse(hit.countered());
        assertTrue(hit.punchedThrough());
    }

    @Test
    void overkillExactlyAtThresholdPunchesThrough() {
        BarrierCombat.BarrierHit hit = BarrierCombat.resolveHit(20, 25, 1.25);
        assertEquals(0, hit.newHitpoints(), 1e-9);
        assertTrue(hit.broken());
        assertFalse(hit.countered());
        assertTrue(hit.punchedThrough());
    }

    @Test
    void overkillJustBelowThresholdIsCountered() {
        BarrierCombat.BarrierHit hit = BarrierCombat.resolveHit(20, 24, 1.25);
        assertEquals(0, hit.newHitpoints(), 1e-9);
        assertTrue(hit.broken());
        assertTrue(hit.countered());
        assertFalse(hit.punchedThrough());
    }

    @Test
    void zeroDamageSurvivesAndIsCounteredWithUntouchedHp() {
        BarrierCombat.BarrierHit hit = BarrierCombat.resolveHit(20, 0, 1.25);
        assertEquals(20, hit.newHitpoints(), 1e-9);
        assertFalse(hit.broken());
        assertTrue(hit.countered());
        assertFalse(hit.punchedThrough());
    }
}
