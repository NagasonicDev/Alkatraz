package me.nagasonic.alkatraz.spells.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P0-2/P0-3 numeric contract. The affinity input is capped at the published
 * circle-9 budget, and a player's hit is capped AFTER {@code getPower} so
 * mastery cannot magnify a spell's PvP total.
 */
class CombatSafetyAffinityAndPvpTest {

    @Test
    void publishedAffinityCeilingIsTheCircleNineBudget() {
        assertEquals(215.0, AttackSpell.MAX_AFFINITY, 1e-9);
        assertEquals(3.15, 1.0 + AttackSpell.MAX_AFFINITY / 100.0, 1e-9);
    }

    @Test
    void affinityAndResistanceInputsAreCappedAtThePublishedCeiling() {
        assertEquals(215.0, AttackSpell.clampAffinity(600.0), 1e-9,
                "mastery points share the pool with circle points; damage still caps at 215");
        assertEquals(215.0, AttackSpell.clampAffinity(215.0), 1e-9);
        assertEquals(37.0, AttackSpell.clampAffinity(37.0), 1e-9,
                "a normal profile value is untouched");
        assertEquals(0.0, AttackSpell.clampAffinity(0.0), 1e-9);
    }

    @Test
    void starfallPvpTotalStaysAtTwelveRegardlessOfAffinity() {
        // starfall_cataclysm.yml: power 10, hit_count 3, PLAYER_DAMAGE_FACTOR 0.4.
        double base = 10.0;
        double pvpFactor = 0.4;
        int hits = 3;
        double fullHitAtMaxAffinity = base * 3.15; // 31.5
        double uncappedTotal = fullHitAtMaxAffinity * hits; // 94.5

        double cappedHit = AttackSpell.capPvpDamage(fullHitAtMaxAffinity, base, pvpFactor, true);
        assertEquals(4.0, cappedHit, 1e-9);
        assertEquals(12.0, cappedHit * hits, 1e-9,
                "the published PvP total is 12 even at max affinity, not " + uncappedTotal);
        assertEquals(fullHitAtMaxAffinity,
                AttackSpell.capPvpDamage(fullHitAtMaxAffinity, base, pvpFactor, false), 1e-9,
                "mobs still take the full affinity-scaled hit");
    }

    @Test
    void leviathanPvPHitStaysAtSixRegardlessOfAffinity() {
        // leviathans_testament.yml: power 12, hit_count 1, PLAYER_DAMAGE_FACTOR 0.5.
        double base = 12.0;
        double fullHitAtMaxAffinity = base * 3.15; // 37.8
        assertEquals(6.0, AttackSpell.capPvpDamage(fullHitAtMaxAffinity, base, 0.5, true), 1e-9);
        assertEquals(fullHitAtMaxAffinity,
                AttackSpell.capPvpDamage(fullHitAtMaxAffinity, base, 0.5, false), 1e-9);
    }
}
