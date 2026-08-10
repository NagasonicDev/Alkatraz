package me.nagasonic.alkatraz.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaCrossingTest {

    @Test
    void crossingFromPositiveToZeroFires() {
        assertTrue(StatUtils.crossedIntoZero(10, 0), "spending mana down to exactly zero is a crossing");
    }

    @Test
    void crossingFromPositiveToNegativeFires() {
        assertTrue(StatUtils.crossedIntoZero(10, -3), "spending below zero (clamped to zero) is a crossing");
    }

    @Test
    void stayingPositiveDoesNotFire() {
        assertFalse(StatUtils.crossedIntoZero(10, 5), "still having mana left is not a crossing");
    }

    @Test
    void stayingAtZeroDoesNotFire() {
        assertFalse(StatUtils.crossedIntoZero(0, 0), "staying at zero must not spam the empty trigger");
    }

    @Test
    void startingPositiveAndGainingDoesNotFire() {
        assertFalse(StatUtils.crossedIntoZero(5, 10), "gaining mana is never an empty crossing");
    }
}
