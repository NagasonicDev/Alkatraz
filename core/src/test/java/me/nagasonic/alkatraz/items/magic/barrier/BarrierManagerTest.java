package me.nagasonic.alkatraz.items.magic.barrier;

import me.nagasonic.alkatraz.items.magic.condition.implementation.CooldownTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrierManagerTest {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final String ITEM = "00000000-0000-0000-0000-00000000010a";
    private static final String OTHER_ITEM = "00000000-0000-0000-0000-00000000010b";

    @AfterEach
    void tearDown() {
        CooldownTracker.clearAll();
    }

    @Test
    void marksCooldownUntilNowPlusSeconds() {
        long now = 1_000_000L;
        BarrierManager.markShatterCooldown(ACTOR, ITEM, now, 90);
        assertTrue(CooldownTracker.isOnCooldown(ACTOR, ITEM, now));
        assertTrue(CooldownTracker.isOnCooldown(ACTOR, ITEM, now + 89_999));
        assertFalse(CooldownTracker.isOnCooldown(ACTOR, ITEM, now + 90_000));
        assertFalse(CooldownTracker.isOnCooldown(ACTOR, ITEM, now + 91_000));
    }

    @Test
    void oneSecondCooldownExpiresExactlyAfterOneSecond() {
        long now = 5_000L;
        BarrierManager.markShatterCooldown(ACTOR, ITEM, now, 1);
        assertTrue(CooldownTracker.isOnCooldown(ACTOR, ITEM, now + 999));
        assertFalse(CooldownTracker.isOnCooldown(ACTOR, ITEM, now + 1_000));
    }

    @Test
    void zeroSecondsExpiresImmediately() {
        long now = 7_000L;
        BarrierManager.markShatterCooldown(ACTOR, ITEM, now, 0);
        assertFalse(CooldownTracker.isOnCooldown(ACTOR, ITEM, now));
        assertFalse(CooldownTracker.isOnCooldown(ACTOR, ITEM, now + 1));
    }

    @Test
    void negativeSecondsExpiresImmediately() {
        long now = 9_000L;
        BarrierManager.markShatterCooldown(ACTOR, ITEM, now, -5);
        assertFalse(CooldownTracker.isOnCooldown(ACTOR, ITEM, now));
    }

    @Test
    void cooldownsAreTrackedPerActor() {
        long now = System.currentTimeMillis();
        BarrierManager.markShatterCooldown(ACTOR, ITEM, now, 60);
        assertTrue(CooldownTracker.isOnCooldown(ACTOR, ITEM, now));
        assertFalse(CooldownTracker.isOnCooldown(OTHER_ACTOR, ITEM, now));
    }

    @Test
    void cooldownsAreTrackedPerItem() {
        long now = System.currentTimeMillis();
        BarrierManager.markShatterCooldown(ACTOR, ITEM, now, 60);
        assertTrue(CooldownTracker.isOnCooldown(ACTOR, ITEM, now));
        assertFalse(CooldownTracker.isOnCooldown(ACTOR, OTHER_ITEM, now));
    }
}