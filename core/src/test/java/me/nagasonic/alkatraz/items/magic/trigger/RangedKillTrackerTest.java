package me.nagasonic.alkatraz.items.magic.trigger;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangedKillTrackerTest {

    private static final long TTL = 10_000L;
    private static final UUID VICTIM = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID SHOOTER = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    @Test
    void resolveReturnsShooterAndWeaponWithinTtl() {
        RangedKillTracker<String> tracker = new RangedKillTracker<>(TTL);
        tracker.record(VICTIM, SHOOTER, "bow#1");
        long now = System.currentTimeMillis();
        assertTrue(tracker.resolve(VICTIM, now).isPresent());
        assertEquals(SHOOTER, tracker.resolve(VICTIM, now).get().shooter());
        assertEquals("bow#1", tracker.resolve(VICTIM, now).get().weapon());
    }

    @Test
    void resolveReturnsEmptyAfterTtl() {
        RangedKillTracker<String> tracker = new RangedKillTracker<>(TTL);
        tracker.record(VICTIM, SHOOTER, "bow#1");
        assertFalse(tracker.resolve(VICTIM, System.currentTimeMillis() + TTL + 1).isPresent());
    }

    @Test
    void resolveRemovesExpiredEntry() {
        RangedKillTracker<String> tracker = new RangedKillTracker<>(TTL);
        tracker.record(VICTIM, SHOOTER, "bow#1");
        tracker.resolve(VICTIM, System.currentTimeMillis() + TTL + 1);
        assertFalse(tracker.resolve(VICTIM, System.currentTimeMillis()).isPresent());
    }

    @Test
    void cleanupRemovesExpiredEntries() {
        RangedKillTracker<String> tracker = new RangedKillTracker<>(TTL);
        tracker.record(VICTIM, SHOOTER, "bow#1");
        tracker.cleanup(System.currentTimeMillis() + TTL + 1);
        assertFalse(tracker.resolve(VICTIM, System.currentTimeMillis()).isPresent());
    }

    @Test
    void unknownVictimResolvesEmpty() {
        RangedKillTracker<String> tracker = new RangedKillTracker<>(TTL);
        assertFalse(tracker.resolve(UUID.randomUUID(), System.currentTimeMillis()).isPresent());
    }

    @Test
    void laterRecordOverwritesPrevious() {
        RangedKillTracker<String> tracker = new RangedKillTracker<>(TTL);
        tracker.record(VICTIM, SHOOTER, "bow#1");
        UUID otherShooter = UUID.fromString("00000000-0000-0000-0000-0000000000dd");
        tracker.record(VICTIM, otherShooter, "crossbow#2");
        long now = System.currentTimeMillis();
        assertEquals(otherShooter, tracker.resolve(VICTIM, now).get().shooter());
        assertEquals("crossbow#2", tracker.resolve(VICTIM, now).get().weapon());
    }
}
