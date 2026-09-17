package me.nagasonic.alkatraz.api.ai.task;

import me.nagasonic.alkatraz.api.ai.AiSpec;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;

import java.util.List;
import java.util.Objects;

/**
 * Declarative description of one activity a TaskBrain can run. All lists are
 * defensively copied and exposed unmodifiably.
 *
 * <p>The activity's output half is a {@link GoalBrain.Entry goals} list — the
 * same open goal vocabulary used by {@code brains/<id>.yml}; when the activity
 * becomes live those goals replace whatever the mob had.
 *
 * @param id                    unique activity id (must be a non-blank string)
 * @param priority              relative priority vs other activities
 * @param sensors               sensors that run while this activity is a candidate
 * @param goals                 prioritized goals the activity runs
 * @param activationMemories    keys that must all be present for the activity to start
 * @param deactivationMemories  keys that must all be absent for the activity to stay
 * @param memoriesOnEnter       memories written (with expiry) when the activity starts
 * @param memoriesOnExit        memories erased when the activity stops
 * @param schedule              time-of-day window in which this activity is eligible
 */
public record ActivitySpec(
        String id,
        int priority,
        List<Sensor> sensors,
        List<GoalBrain.Entry> goals,
        List<MemoryKey<?>> activationMemories,
        List<MemoryKey<?>> deactivationMemories,
        List<TimedMemory> memoriesOnEnter,
        List<MemoryKey<?>> memoriesOnExit,
        TimeOfDayPredicate schedule) implements AiSpec {

    public ActivitySpec {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("activity id must be non-blank");
        if (priority < 0) throw new IllegalArgumentException("priority must be non-negative");
        sensors = List.copyOf(sensors);
        goals = List.copyOf(goals);
        activationMemories = List.copyOf(activationMemories);
        deactivationMemories = List.copyOf(deactivationMemories);
        memoriesOnEnter = List.copyOf(memoriesOnEnter);
        memoriesOnExit = List.copyOf(memoriesOnExit);
        if (schedule == null) throw new NullPointerException("schedule");
    }
}