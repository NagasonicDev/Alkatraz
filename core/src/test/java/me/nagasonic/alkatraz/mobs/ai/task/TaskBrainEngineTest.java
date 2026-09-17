package me.nagasonic.alkatraz.mobs.ai.task;

import de.tr7zw.changeme.nbtapi.NBTCompound;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.ai.task.ActivitySpec;
import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.ai.task.Sensor;
import me.nagasonic.alkatraz.api.ai.task.TaskBrain;
import me.nagasonic.alkatraz.api.ai.task.TimedMemory;
import me.nagasonic.alkatraz.api.ai.task.TimeOfDayPredicate;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskBrainEngineTest {

    private Mob mob;
    private World world;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        when(world.getTime()).thenReturn(6000L);
        mob = mock(Mob.class);
        when(mob.getWorld()).thenReturn(world);
        when(mob.isValid()).thenReturn(true);
    }

    private static TaskBrain brain() {
        return TaskBrain.builder()
                .addCoreMemory(MemoryKey.IS_HURT)
                .addCoreMemory(MemoryKey.WALK_TARGET)
                .addActivity(new ActivitySpec(
                        "IDLE", 1, List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), TimeOfDayPredicate.DAY))
                .addActivity(new ActivitySpec(
                        "FIGHT", 10, List.of(new Sensor.HurtBy()), List.of(),
                        List.of(MemoryKey.IS_HURT), List.of(),
                        List.of(new TimedMemory(MemoryKey.WALK_TARGET, 42L, 500)),
                        List.of(), TimeOfDayPredicate.DAY))
                .build();
    }

    @Test
    void startsOnDefaultActivityAndSwitchesOnActivationMemory() {
        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            ignored.when(Alkatraz::getNms).thenReturn(null);
            MobMemoryStore store = MobMemoryStore.empty();
            TaskBrainEngine engine = TaskBrainEngine.create(mob, brain(), Map.of(), store, null, 0);

            assertEquals("IDLE", engine.currentActivity());

            engine.setMemory(MemoryKey.IS_HURT, true);
            assertEquals("FIGHT", engine.currentActivity());
            assertTrue(engine.hasMemory(MemoryKey.IS_HURT));
            assertTrue(engine.getMemory(MemoryKey.IS_HURT).isPresent());
            // leaving FIGHT requires IS_HURT to be absent -> erasing it returns to IDLE
            engine.eraseMemory(MemoryKey.IS_HURT);
            assertEquals("IDLE", engine.currentActivity());
        }
    }

    @Test
    void memoriesOnEnterAreWrittenOnSwitch() {
        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            ignored.when(Alkatraz::getNms).thenReturn(null);
            TaskBrainEngine engine = TaskBrainEngine.create(mob, brain(), Map.of(), MobMemoryStore.empty(), null, 0);
            engine.setMemory(MemoryKey.IS_HURT, true);
            assertEquals(42L, engine.getMemory(MemoryKey.WALK_TARGET).orElseThrow());
        }
    }

    @Test
    void pinnedActivityBypassesGatesAndUnknownPinFallsBack() {
        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            ignored.when(Alkatraz::getNms).thenReturn(null);
            TaskBrainEngine engine = TaskBrainEngine.create(mob, brain(), Map.of(), MobMemoryStore.empty(), "FIGHT", 0);
            assertEquals("FIGHT", engine.currentActivity());

            engine.setPinnedActivity(null);
            assertEquals("IDLE", engine.currentActivity());

            engine.setPinnedActivity("FIGHT");
            assertEquals("FIGHT", engine.currentActivity());
            engine.setPinnedActivity("NOPE");
            assertEquals("IDLE", engine.currentActivity());
            assertEquals("NOPE", engine.pinnedActivity());
        }
    }

    @Test
    void switchingAppliesActivityGoalsThroughAiApplier() {
        GoalBrain fightGoals = GoalBrain.builder().build();
        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            // leave getNms() unstubbed -> null -> applyGoalBrain is a no-op
            TaskBrainEngine engine = TaskBrainEngine.create(mob, brain(), Map.of("FIGHT", fightGoals, "IDLE", GoalBrain.builder().build()), MobMemoryStore.empty(), null, 0);
            engine.setMemory(MemoryKey.IS_HURT, true);
            assertEquals("FIGHT", engine.currentActivity());
        }
    }

    @Test
    void tickAdvancesClockAndRunsSensors() {
        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            ignored.when(Alkatraz::getNms).thenReturn(null);
            TaskBrainEngine engine = TaskBrainEngine.create(mob, brain(), Map.of(), MobMemoryStore.empty(), null, 0);
            assertDoesNotThrow(() -> engine.tick(20));
            assertDoesNotThrow(() -> engine.tick(40));
        }
    }

    @Test
    void writeMemoryToPersistsUnexpiredEntries() {
        NBTCompound container = mock(NBTCompound.class, RETURNS_DEEP_STUBS);
        MobMemoryStore store = MobMemoryStore.empty();
        store.set(new MemoryKey<String>("keep"), "v");
        store.set(new MemoryKey<String>("stale"), "s", 0);
        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            ignored.when(Alkatraz::getNms).thenReturn(null);
            TaskBrainEngine engine = TaskBrainEngine.create(mob, brain(), Map.of(), store, null, 0);
            engine.writeMemoryTo(container);
        }
        verify(container).getOrCreateCompound("keep");
        verify(container, never()).getOrCreateCompound("stale");
    }
}