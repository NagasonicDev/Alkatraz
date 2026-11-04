package me.nagasonic.alkatraz.progression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoosterSchedulerTest {
    @AfterEach
    void cleanup() {
        BoosterScheduler.clear();
        BoosterManager.clear();
    }

    @Test
    void recurringScheduleFiresAndExpires() {
        long now = 1_000_000L;
        BoosterScheduler.Schedule schedule = new BoosterScheduler.Schedule(
                "test_schedule", "test_booster", BoosterManager.Scope.GLOBAL, "", "progression",
                2.0, 1_000L, 5_000L,
                BoosterScheduler.StackPolicy.REPLACE, true, now, 0L);
        BoosterScheduler.register(schedule);
        BoosterScheduler.tick(now);
        assertEquals(1, BoosterScheduler.listActive().size());
        BoosterScheduler.tick(now + 1_500L);
        assertEquals(0, BoosterScheduler.listActive().size());
    }

    @Test
    void longPauseSemanticsConstant() {
        assertEquals("wall-clock-no-pause; missed-occurrences-skipped",
                BoosterScheduler.PAUSE_SEMANTICS);
    }
}
