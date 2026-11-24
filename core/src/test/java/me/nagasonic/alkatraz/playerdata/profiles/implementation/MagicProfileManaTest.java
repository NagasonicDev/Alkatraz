package me.nagasonic.alkatraz.playerdata.profiles.implementation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MagicProfileManaTest {

    private static MagicProfile profile() {
        return new MagicProfile(UUID.randomUUID());
    }

    @Test
    void setManaRejectsNaN() {
        MagicProfile p = profile();
        p.setMana(Double.NaN);
        assertEquals(0.0, p.getMana());
    }

    @Test
    void setManaRejectsInfinity() {
        MagicProfile p = profile();
        p.setMana(Double.POSITIVE_INFINITY);
        assertEquals(0.0, p.getMana());
        p.setMana(Double.NEGATIVE_INFINITY);
        assertEquals(0.0, p.getMana());
    }

    @Test
    void setManaClampsNegativeToZero() {
        MagicProfile p = profile();
        p.setMana(-50);
        assertEquals(0.0, p.getMana());
    }

    @Test
    void setManaClampsToMax() {
        MagicProfile p = profile();
        p.setMaxMana(200);
        p.setMana(9999);
        assertEquals(200.0, p.getMana());
    }

    @Test
    void setMaxManaRejectsNonPositive() {
        MagicProfile p = profile();
        p.setMaxMana(Double.NaN);
        assertEquals(1.0, p.getMaxMana());
        p.setMaxMana(0);
        assertEquals(1.0, p.getMaxMana());
        p.setMaxMana(-10);
        assertEquals(1.0, p.getMaxMana());
    }

    @Test
    void regenIsOnePercentOfMaxManaPerSecond() {
        assertEquals(1.0, MagicProfile.manaRegenFor(100, 1.0), 1e-9);
        assertEquals(20.0, MagicProfile.manaRegenFor(2000, 1.0), 1e-9);
        assertEquals(100.0, MagicProfile.manaRegenFor(10000, 1.0), 1e-9);
    }

    @Test
    void regenPercentComesFromProgressionConfig() {
        assertEquals(200.0, MagicProfile.manaRegenFor(10000, 2.0), 1e-9);
        assertEquals(0.5, MagicProfile.manaRegenFor(100, 0.5), 1e-9);
    }

    @Test
    void regenRejectsNonFiniteAndNonPositiveInputs() {
        assertEquals(0.0, MagicProfile.manaRegenFor(Double.NaN, 1.0), 1e-9);
        assertEquals(0.0, MagicProfile.manaRegenFor(Double.POSITIVE_INFINITY, 1.0), 1e-9);
        assertEquals(0.0, MagicProfile.manaRegenFor(-100, 1.0), 1e-9);
        assertEquals(0.0, MagicProfile.manaRegenFor(100, Double.NaN), 1e-9);
        assertEquals(0.0, MagicProfile.manaRegenFor(100, -1.0), 1e-9);
    }
}
