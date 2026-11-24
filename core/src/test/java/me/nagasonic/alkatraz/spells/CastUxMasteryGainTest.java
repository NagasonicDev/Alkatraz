package me.nagasonic.alkatraz.spells;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Each successful cast pays its circle in mastery, never less than one point. */
class CastUxMasteryGainTest {

    @Test
    void lowCircleSpellsStillEarnOnePoint() {
        for (int circle = 0; circle <= 1; circle++) {
            assertEquals(1, Spell.masteryGainPerCast(circle),
                    "circle " + circle + " earns the one-point minimum");
        }
    }

    @Test
    void higherCircleSpellsEarnTheirCircle() {
        for (int circle = 2; circle <= 9; circle++) {
            assertEquals(circle, Spell.masteryGainPerCast(circle),
                    "circle " + circle + " earns " + circle + " points per cast");
        }
    }
}
