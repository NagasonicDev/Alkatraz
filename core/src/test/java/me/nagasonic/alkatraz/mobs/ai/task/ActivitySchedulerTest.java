package me.nagasonic.alkatraz.mobs.ai.task;

import me.nagasonic.alkatraz.api.ai.task.ActivitySpec;
import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.ai.task.Sensor;
import me.nagasonic.alkatraz.api.ai.task.TaskBrain;
import me.nagasonic.alkatraz.api.ai.task.TimeOfDayPredicate;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActivitySchedulerTest {

    private static ActivitySpec act(String id, int priority, TimeOfDayPredicate schedule,
                                    List<MemoryKey<?>> activation, List<MemoryKey<?>> deactivation) {
        return new ActivitySpec(id, priority, List.of(), List.of(), activation, deactivation,
                List.of(), List.of(), schedule);
    }

    private static TaskBrain brain(ActivitySpec... activities) {
        TaskBrain.Builder builder = TaskBrain.builder();
        LinkedHashSet<MemoryKey<?>> memories = new LinkedHashSet<>();
        for (ActivitySpec a : activities) {
            builder.addActivity(a);
            memories.addAll(a.activationMemories());
            memories.addAll(a.deactivationMemories());
            memories.addAll(a.memoriesOnExit());
        }
        for (MemoryKey<?> memory : memories) {
            builder.addCoreMemory(memory);
        }
        return builder.build();
    }

    @Test
    void pinnedActivityWinsRegardlessOfMemory() {
        TaskBrain b = brain(
                act("FIGHT", 10, TimeOfDayPredicate.DAY, List.of(MemoryKey.NEAREST_HOSTILES), List.of()),
                act("IDLE", 1, TimeOfDayPredicate.DAY, List.of(), List.of()));
        assertEquals("FIGHT", ActivityScheduler.select(b, "FIGHT", key -> false, TimeOfDayPredicate.NIGHT));
    }

    @Test
    void unknownPinFallsBackToNormalSelection() {
        TaskBrain b = brain(act("IDLE", 1, TimeOfDayPredicate.DAY, List.of(), List.of()));
        assertEquals("IDLE", ActivityScheduler.select(b, "missing", key -> false, TimeOfDayPredicate.DAY));
    }

    @Test
    void highestEligiblePriorityWins() {
        TaskBrain b = brain(
                act("A", 1, TimeOfDayPredicate.DAY, List.of(), List.of()),
                act("B", 5, TimeOfDayPredicate.DAY, List.of(), List.of()),
                act("C", 9, TimeOfDayPredicate.NIGHT, List.of(), List.of()));
        assertEquals("B", ActivityScheduler.select(b, null, key -> false, TimeOfDayPredicate.DAY));
    }

    @Test
    void tieBreaksToRegistrationOrder() {
        TaskBrain b = brain(
                act("first", 4, TimeOfDayPredicate.DAY, List.of(), List.of()),
                act("second", 4, TimeOfDayPredicate.DAY, List.of(), List.of()));
        assertEquals("first", ActivityScheduler.select(b, null, key -> false, TimeOfDayPredicate.DAY));
    }

    @Test
    void activationMemoriesMustAllBePresent() {
        TaskBrain b = brain(
                act("FIGHT", 10, TimeOfDayPredicate.DAY, List.of(MemoryKey.NEAREST_HOSTILES), List.of()),
                act("IDLE", 1, TimeOfDayPredicate.DAY, List.of(), List.of()));
        assertEquals("IDLE", ActivityScheduler.select(b, null, key -> false, TimeOfDayPredicate.DAY));
        assertEquals("FIGHT", ActivityScheduler.select(b, null, key -> key.equals(MemoryKey.NEAREST_HOSTILES), TimeOfDayPredicate.DAY));
    }

    @Test
    void deactivationMemoriesBlockWhenPresent() {
        TaskBrain b = brain(
                act("FIGHT", 10, TimeOfDayPredicate.DAY, List.of(), List.of(MemoryKey.IS_HURT)),
                act("IDLE", 1, TimeOfDayPredicate.DAY, List.of(), List.of()));
        assertEquals("FIGHT", ActivityScheduler.select(b, null, key -> false, TimeOfDayPredicate.DAY));
        assertEquals("IDLE", ActivityScheduler.select(b, null, key -> key.equals(MemoryKey.IS_HURT), TimeOfDayPredicate.DAY));
    }

    @Test
    void scheduleGatesEligibility() {
        TaskBrain b = brain(
                act("IDLE", 1, TimeOfDayPredicate.DAY, List.of(), List.of()),
                act("NIGHTLY", 9, TimeOfDayPredicate.NIGHT, List.of(), List.of()));
        assertEquals("IDLE", ActivityScheduler.select(b, null, key -> false, TimeOfDayPredicate.DAY));
        assertEquals("NIGHTLY", ActivityScheduler.select(b, null, key -> false, TimeOfDayPredicate.NIGHT));
    }

    @Test
    void defaultActivityIsFallback() {
        TaskBrain b = brain(act("IDLE", 1, TimeOfDayPredicate.DAY, List.of(), List.of()));
        assertEquals("IDLE", ActivityScheduler.select(b, null, key -> false, TimeOfDayPredicate.NIGHT));
    }

    @Test
    void emptyBrainReturnsDefaultActivity() {
        TaskBrain b = TaskBrain.builder().build();
        assertEquals("IDLE", ActivityScheduler.select(b, null, key -> false, TimeOfDayPredicate.DAY));
    }

    @Test
    void bucketOfLadderIsExhaustive() {
        assertEquals(TimeOfDayPredicate.DAY, ActivityScheduler.bucketOf(0));
        assertEquals(TimeOfDayPredicate.DAY, ActivityScheduler.bucketOf(6000));
        assertEquals(TimeOfDayPredicate.DAY, ActivityScheduler.bucketOf(11999));
        assertEquals(TimeOfDayPredicate.DUSK, ActivityScheduler.bucketOf(13000));
        assertEquals(TimeOfDayPredicate.NIGHT, ActivityScheduler.bucketOf(15000));
        assertEquals(TimeOfDayPredicate.MIDNIGHT, ActivityScheduler.bucketOf(18000));
        assertEquals(TimeOfDayPredicate.NIGHT, ActivityScheduler.bucketOf(23000));
        assertEquals(TimeOfDayPredicate.DAY, ActivityScheduler.bucketOf(24000));
    }
}