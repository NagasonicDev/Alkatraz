package me.nagasonic.alkatraz.api.ai.task;

import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskBrainTest {

    private static ActivitySpec idle() {
        return new ActivitySpec(
                "IDLE", 0,
                List.of(new Sensor.HurtBy()),
                List.of(new GoalBrain.Entry(1, new NativeBehaviorSpec.SleepInBed(), false)),
                List.of(),
                List.of(MemoryKey.IS_HURT),
                List.of(new TimedMemory(MemoryKey.IS_HURT, Boolean.TRUE, 40)),
                List.of(MemoryKey.IS_HURT),
                TimeOfDayPredicate.NIGHT);
    }

    private static ActivitySpec hunt(MemoryKey<?> activation) {
        return new ActivitySpec(
                "hunt", 10,
                List.of(new Sensor.NearestLivingEntities(16)),
                List.of(new GoalBrain.Entry(1, new NativeBehaviorSpec.MoveToTargetSink(2, 1), false)),
                List.of(activation),
                List.of(),
                List.of(),
                List.of(),
                TimeOfDayPredicate.DAY);
    }

    @Test
    void emptyBrain_hasDefaultsAndUnmodifiableViews() {
        TaskBrain brain = TaskBrain.builder().build();
        assertTrue(brain.activities().isEmpty());
        assertTrue(brain.coreMemories().isEmpty());
        assertTrue(brain.coreSensors().isEmpty());
        assertEquals("IDLE", brain.defaultActivity());
        assertThrows(UnsupportedOperationException.class, () -> brain.activities().add(idle()));
        assertThrows(UnsupportedOperationException.class, () -> brain.coreMemories().add(MemoryKey.IS_HURT));
        assertThrows(UnsupportedOperationException.class, () -> brain.coreSensors().add(new Sensor.HurtBy()));
    }

    @Test
    void addActivity_preservesOrderAndRejectsDuplicates() {
        TaskBrain brain = TaskBrain.builder()
                .addCoreMemory(MemoryKey.IS_HURT)
                .addActivity(idle())
                .addActivity(hunt(MemoryKey.IS_HURT))
                .build();
        assertEquals(List.of("IDLE", "hunt"), brain.activities().stream().map(ActivitySpec::id).toList());
        assertThrows(IllegalArgumentException.class, () -> TaskBrain.builder().addActivity(idle()).addActivity(idle()));
    }

    @Test
    void defaultActivity_mustReferenceAnExistingActivity() {
        assertThrows(IllegalArgumentException.class,
                () -> TaskBrain.builder().addActivity(idle()).defaultActivity("missing").build());
        TaskBrain ok = TaskBrain.builder().addActivity(idle()).defaultActivity("IDLE").build();
        assertEquals("IDLE", ok.defaultActivity());
        assertThrows(IllegalArgumentException.class, () -> TaskBrain.builder().defaultActivity(" ").build());
        assertThrows(IllegalArgumentException.class, () -> TaskBrain.builder().defaultActivity((String) null).build());
    }

    @Test
    void coreMemories_areUnique() {
        assertThrows(IllegalArgumentException.class,
                () -> TaskBrain.builder().addCoreMemory(MemoryKey.IS_HURT).addCoreMemory(MemoryKey.IS_HURT));
        assertThrows(NullPointerException.class, () -> TaskBrain.builder().addCoreMemory(null));
    }

    @Test
    void coreSensors_areUnique() {
        assertThrows(IllegalArgumentException.class,
                () -> TaskBrain.builder().addCoreSensor(new Sensor.HurtBy()).addCoreSensor(new Sensor.HurtBy()));
        assertThrows(NullPointerException.class, () -> TaskBrain.builder().addCoreSensor(null));
    }

    @Test
    void activity_activationKey_mayBeCoreMemory() {
        TaskBrain brain = TaskBrain.builder()
                .addCoreMemory(MemoryKey.IS_HURT)
                .addActivity(hunt(MemoryKey.IS_HURT))
                .build();
        assertTrue(brain.coreMemories().contains(MemoryKey.IS_HURT));
    }

    @Test
    void activity_activationKey_mayBeSelfDeclared() {
        TaskBrain brain = TaskBrain.builder()
                .addActivity(new ActivitySpec(
                        "guarded", 1, List.of(), List.of(),
                        List.of(MemoryKey.IS_HURT), List.of(),
                        List.of(new TimedMemory(MemoryKey.IS_HURT, Boolean.TRUE, 10)),
                        List.of(), TimeOfDayPredicate.NIGHT))
                .build();
        assertEquals(1, brain.activities().size());
    }

    @Test
    void activity_unknownReferenceKey_throws() {
        MemoryKey<?> unknown = new MemoryKey<>("logistics_target");
        assertThrows(IllegalArgumentException.class,
                () -> TaskBrain.builder().addActivity(hunt(unknown)).build());
        assertThrows(IllegalArgumentException.class,
                () -> TaskBrain.builder()
                        .addActivity(new ActivitySpec(
                                "x", 1, List.of(), List.of(),
                                List.of(), List.of(), List.of(),
                                List.of(unknown), TimeOfDayPredicate.DAY))
                        .build());
    }

    @Test
    void activitySpec_validatesItself() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActivitySpec("", 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), TimeOfDayPredicate.DAY));
        assertThrows(IllegalArgumentException.class,
                () -> new ActivitySpec("x", -1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), TimeOfDayPredicate.DAY));
        assertThrows(NullPointerException.class,
                () -> new ActivitySpec("x", 1, null, List.of(), List.of(), List.of(), List.of(), List.of(), TimeOfDayPredicate.DAY));
        assertThrows(NullPointerException.class,
                () -> new ActivitySpec("x", 1, List.of((Sensor) null), List.of(), List.of(), List.of(), List.of(), List.of(), TimeOfDayPredicate.DAY));
        assertThrows(NullPointerException.class,
                () -> new ActivitySpec("x", 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null));

        ActivitySpec spec = idle();
        assertThrows(UnsupportedOperationException.class, () -> spec.sensors().add(new Sensor.HurtBy()));
        assertThrows(UnsupportedOperationException.class, () -> spec.goals().add(
                new GoalBrain.Entry(1, new NativeBehaviorSpec.SleepInBed(), false)));
        assertThrows(NullPointerException.class,
                () -> new ActivitySpec("x", 1, List.of(), List.of((GoalBrain.Entry) null),
                        List.of(), List.of(), List.of(), List.of(), TimeOfDayPredicate.DAY));
        assertThrows(UnsupportedOperationException.class, () -> spec.activationMemories().add(MemoryKey.IS_HURT));
        assertThrows(UnsupportedOperationException.class, () -> spec.memoriesOnEnter().add(new TimedMemory(MemoryKey.IS_HURT, Boolean.TRUE, 1)));
        assertThrows(UnsupportedOperationException.class, () -> spec.memoriesOnExit().add(MemoryKey.IS_HURT));
        assertThrows(UnsupportedOperationException.class, () -> spec.deactivationMemories().add(MemoryKey.IS_HURT));
    }

    @Test
    void addActivity_rejectsNull() {
        assertThrows(NullPointerException.class, () -> TaskBrain.builder().addActivity(null));
    }
}