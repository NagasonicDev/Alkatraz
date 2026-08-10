package me.nagasonic.alkatraz.items.magic.trigger;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorldTimeTrackerTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void crossingDuskTriggersNight() {
        assertEquals("on_night", WorldTimeTracker.transitionTrigger(12950, 13000));
    }

    @Test
    void wrapThroughMidnightTriggersDay() {
        assertEquals("on_day", WorldTimeTracker.transitionTrigger(23999, 5));
    }

    @Test
    void normalTimeAdvanceTriggersNothing() {
        assertNull(WorldTimeTracker.transitionTrigger(500, 600));
    }

    @Test
    void stayingBeforeDuskTriggersNothing() {
        assertNull(WorldTimeTracker.transitionTrigger(1000, 1200));
    }

    @Test
    void firstUpdateRecordsWithoutTriggering() {
        WorldTimeTracker tracker = new WorldTimeTracker();
        assertNull(tracker.update(WORLD, 6000));
    }

    @Test
    void updateFiresNightThenDayOnWrappingWorld() {
        WorldTimeTracker tracker = new WorldTimeTracker();
        tracker.update(WORLD, 12900);
        assertEquals("on_night", tracker.update(WORLD, 13000));
        assertEquals("on_day", tracker.update(WORLD, 10));
    }
}
