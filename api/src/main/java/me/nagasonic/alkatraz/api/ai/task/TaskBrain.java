package me.nagasonic.alkatraz.api.ai.task;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A mob's task-oriented AI: a set of {@link ActivitySpec activities}, globally
 * shared core memories, globally running core sensors, and a default activity
 * id ({@code "IDLE"} unless overridden). Declarative and immutable once built;
 * the future engine reads this to drive an entity each tick.
 */
public final class TaskBrain {

    private final List<ActivitySpec> activities;
    private final Set<MemoryKey<?>> coreMemories;
    private final List<Sensor> coreSensors;
    private final String defaultActivity;

    private TaskBrain(List<ActivitySpec> activities, Set<MemoryKey<?>> coreMemories,
                      List<Sensor> coreSensors, String defaultActivity) {
        this.activities = List.copyOf(activities);
        this.coreMemories = Set.copyOf(coreMemories);
        this.coreSensors = List.copyOf(coreSensors);
        this.defaultActivity = defaultActivity;
    }

    /** Activities in registration order. Unmodifiable. */
    public List<ActivitySpec> activities() {
        return activities;
    }

    /** Globally shared memory keys. Unmodifiable, insertion-ordered. */
    public List<MemoryKey<?>> coreMemories() {
        return List.copyOf(coreMemories);
    }

    /** Globally running sensors. Unmodifiable. */
    public List<Sensor> coreSensors() {
        return coreSensors;
    }

    /** The activity id to fall back to when no other activity can run. */
    public String defaultActivity() {
        return defaultActivity;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<ActivitySpec> activities = new ArrayList<>();
        private final LinkedHashSet<MemoryKey<?>> coreMemories = new LinkedHashSet<>();
        private final List<Sensor> coreSensors = new ArrayList<>();
        private String defaultActivity = "IDLE";
        private boolean defaultExplicit;

        private Builder() {}

        /** Adds an activity; ids must be unique across the brain. */
        public Builder addActivity(ActivitySpec activity) {
            if (activity == null) throw new NullPointerException("activity");
            for (ActivitySpec existing : activities) {
                if (existing.id().equals(activity.id())) {
                    throw new IllegalArgumentException("duplicate activity id: " + activity.id());
                }
            }
            activities.add(activity);
            return this;
        }

        /** Adds a globally shared memory key (unique per id). */
        public Builder addCoreMemory(MemoryKey<?> key) {
            if (key == null) throw new NullPointerException("key");
            if (coreMemories.contains(key)) {
                throw new IllegalArgumentException("duplicate core memory: " + key.id());
            }
            coreMemories.add(key);
            return this;
        }

        /** Adds a globally running sensor (duplicates rejected). */
        public Builder addCoreSensor(Sensor sensor) {
            if (sensor == null) throw new NullPointerException("sensor");
            if (coreSensors.contains(sensor)) {
                throw new IllegalArgumentException("duplicate core sensor");
            }
            coreSensors.add(sensor);
            return this;
        }

        /**
         * Sets the fallback activity id. Validated against the activity list at
         * {@link #build()}. Never calling this keeps {@code "IDLE"} as the default.
         */
        public Builder defaultActivity(String id) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("default activity id must be non-blank");
            this.defaultActivity = id;
            this.defaultExplicit = true;
            return this;
        }

        /** Builds the brain, validating the default activity and memory references. */
        public TaskBrain build() {
            if (defaultExplicit && activities.stream().noneMatch(a -> a.id().equals(defaultActivity))) {
                throw new IllegalArgumentException("default activity '" + defaultActivity + "' is not one of the activities");
            }
            for (ActivitySpec activity : activities) {
                validateMemoryReferences(activity);
            }
            return new TaskBrain(activities, coreMemories, coreSensors, defaultActivity);
        }

        /**
         * An activity's activation/deactivation/on-exit keys must be brain core
         * memories, or be written by the activity itself in {@code memoriesOnEnter}.
         */
        private void validateMemoryReferences(ActivitySpec activity) {
            Set<MemoryKey<?>> selfDeclared = new LinkedHashSet<>();
            for (TimedMemory timed : activity.memoriesOnEnter()) {
                selfDeclared.add(timed.key());
            }
            checkKnown(activity, activity.activationMemories(), selfDeclared);
            checkKnown(activity, activity.deactivationMemories(), selfDeclared);
            checkKnown(activity, activity.memoriesOnExit(), selfDeclared);
        }

        private void checkKnown(ActivitySpec activity, List<MemoryKey<?>> keys, Set<MemoryKey<?>> selfDeclared) {
            for (MemoryKey<?> key : keys) {
                if (coreMemories.contains(key) || selfDeclared.contains(key)) continue;
                throw new IllegalArgumentException("activity '" + activity.id() + "' references memory key '" + key.id()
                        + "' which is neither a brain core memory nor declared on the activity");
            }
        }
    }
}