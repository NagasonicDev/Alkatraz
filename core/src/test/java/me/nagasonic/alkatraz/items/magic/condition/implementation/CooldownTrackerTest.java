package me.nagasonic.alkatraz.items.magic.condition.implementation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownTrackerTest {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ITEM = "00000000-0000-0000-0000-00000000000a";

    @AfterEach
    void tearDown() {
        CooldownTracker.clearAll();
    }

    @Test
    void freshActorIsNotOnCooldown() {
        assertFalse(CooldownTracker.isOnCooldown(ACTOR, ITEM, System.currentTimeMillis()));
    }

    @Test
    void markedCooldownIsActiveUntilEndTime() {
        long now = System.currentTimeMillis();
        CooldownTracker.markCooldown(ACTOR, ITEM, now + 5000);
        assertTrue(CooldownTracker.isOnCooldown(ACTOR, ITEM, now));
        assertFalse(CooldownTracker.isOnCooldown(ACTOR, ITEM, now + 6000));
    }

    @Test
    void expiredCooldownIsNotActive() {
        long now = System.currentTimeMillis();
        CooldownTracker.markCooldown(ACTOR, ITEM, now - 1);
        assertFalse(CooldownTracker.isOnCooldown(ACTOR, ITEM, now));
    }

    @Test
    void clearedCooldownIsNotActive() {
        CooldownTracker.markCooldown(ACTOR, ITEM, Long.MAX_VALUE);
        CooldownTracker.clearCooldown(ACTOR, ITEM);
        assertFalse(CooldownTracker.isOnCooldown(ACTOR, ITEM, System.currentTimeMillis()));
    }

    @Test
    void differentItemsTrackCooldownsIndependently() {
        String otherItem = "00000000-0000-0000-0000-00000000000b";
        CooldownTracker.markCooldown(ACTOR, ITEM, System.currentTimeMillis() + 5000);
        assertTrue(CooldownTracker.isOnCooldown(ACTOR, ITEM, System.currentTimeMillis()));
        assertFalse(CooldownTracker.isOnCooldown(ACTOR, otherItem, System.currentTimeMillis()));
    }

    @Test
    void differentActorsTrackCooldownsIndependently() {
        UUID otherActor = UUID.fromString("00000000-0000-0000-0000-000000000002");
        CooldownTracker.markCooldown(ACTOR, ITEM, System.currentTimeMillis() + 5000);
        assertTrue(CooldownTracker.isOnCooldown(ACTOR, ITEM, System.currentTimeMillis()));
        assertFalse(CooldownTracker.isOnCooldown(otherActor, ITEM, System.currentTimeMillis()));
    }
}
