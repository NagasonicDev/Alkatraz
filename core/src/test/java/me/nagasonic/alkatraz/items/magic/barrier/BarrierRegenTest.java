package me.nagasonic.alkatraz.items.magic.barrier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BarrierRegenTest {

    private static final double EPS = 1e-9;

    @Test
    void disabledReturnsZero() {
        BarrierRegen.RegenResult r = BarrierRegen.step(
                false, true, 10, 100, 5, 2, 50, true);
        assertEquals(0, r.hpGained(), EPS);
        assertEquals(0, r.manaSpent(), EPS);
    }

    @Test
    void inactiveReturnsZero() {
        BarrierRegen.RegenResult r = BarrierRegen.step(
                true, false, 10, 100, 5, 2, 50, true);
        assertEquals(0, r.hpGained(), EPS);
        assertEquals(0, r.manaSpent(), EPS);
    }

    @Test
    void noRoomReturnsZero() {
        BarrierRegen.RegenResult r = BarrierRegen.step(
                true, true, 100, 100, 5, 2, 50, true);
        assertEquals(0, r.hpGained(), EPS);
        assertEquals(0, r.manaSpent(), EPS);
    }

    @Test
    void noRoomWhenAboveMaxReturnsZero() {
        BarrierRegen.RegenResult r = BarrierRegen.step(
                true, true, 110, 100, 5, 2, 50, true);
        assertEquals(0, r.hpGained(), EPS);
        assertEquals(0, r.manaSpent(), EPS);
    }

    @Test
    void fullRegenWithEnoughMana() {
        BarrierRegen.RegenResult r = BarrierRegen.step(
                true, true, 50, 100, 5, 2, 50, true);
        assertEquals(5, r.hpGained(), EPS);
        assertEquals(10, r.manaSpent(), EPS);
    }

    @Test
    void regenClampedByRoom() {
        BarrierRegen.RegenResult r = BarrierRegen.step(
                true, true, 97, 100, 5, 2, 50, true);
        assertEquals(3, r.hpGained(), EPS);
        assertEquals(6, r.manaSpent(), EPS);
    }

    @Test
    void freeRegenWhenManaCostPerHpIsZero() {
        BarrierRegen.RegenResult r = BarrierRegen.step(
                true, true, 50, 100, 5, 0, 50, true);
        assertEquals(5, r.hpGained(), EPS);
        assertEquals(0, r.manaSpent(), EPS);
    }

    @Test
    void freeRegenWhenManaCostPerHpIsNegative() {
        BarrierRegen.RegenResult r = BarrierRegen.step(
                true, true, 50, 100, 5, -1, 50, true);
        assertEquals(5, r.hpGained(), EPS);
        assertEquals(0, r.manaSpent(), EPS);
    }

    @Test
    void partialRegenOnLowMana() {
        BarrierRegen.RegenResult r = BarrierRegen.step(
                true, true, 50, 100, 5, 2, 6, true);
        assertEquals(3, r.hpGained(), EPS);
        assertEquals(6, r.manaSpent(), EPS);
    }

    @Test
    void lowManaWithPartialDisabledReturnsZero() {
        BarrierRegen.RegenResult r = BarrierRegen.step(
                true, true, 50, 100, 5, 2, 6, false);
        assertEquals(0, r.hpGained(), EPS);
        assertEquals(0, r.manaSpent(), EPS);
    }

    @Test
    void zeroManaReturnsZero() {
        BarrierRegen.RegenResult r = BarrierRegen.step(
                true, true, 50, 100, 5, 2, 0, true);
        assertEquals(0, r.hpGained(), EPS);
        assertEquals(0, r.manaSpent(), EPS);
    }
}
