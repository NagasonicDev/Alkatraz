package me.nagasonic.alkatraz.mobs.ai.task;

import me.nagasonic.alkatraz.api.ai.task.ActivitySpec;
import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.ai.task.TaskBrain;
import me.nagasonic.alkatraz.api.ai.task.TimeOfDayPredicate;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Chooses which activity of a {@link TaskBrain} is live right now. A pinned
 * activity always wins; otherwise the highest-priority eligible activity (ties
 * broken by registration order); the default activity is the fallback.
 */
public final class ActivityScheduler {

    private ActivityScheduler() {}

    /** Buckets a Minecraft time-of-day value (tick, modulo 24000). */
    public static TimeOfDayPredicate bucketOf(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000);
        if (t < 12000) return TimeOfDayPredicate.DAY;
        if (t < 13800) return TimeOfDayPredicate.DUSK;
        if (t < 18000) return TimeOfDayPredicate.NIGHT;
        if (t < 18500) return TimeOfDayPredicate.MIDNIGHT;
        return TimeOfDayPredicate.NIGHT;
    }

    public static String select(TaskBrain brain, @Nullable String pinnedActivity,
                                Predicate<MemoryKey<?>> present, TimeOfDayPredicate now) {
        Objects.requireNonNull(brain, "brain");
        Objects.requireNonNull(present, "present");
        Objects.requireNonNull(now, "now");
        if (pinnedActivity != null && hasActivity(brain, pinnedActivity)) {
            return pinnedActivity;
        }
        if (brain.activities().isEmpty()) {
            return brain.defaultActivity();
        }
        ActivitySpec best = null;
        for (ActivitySpec activity : brain.activities()) {
            if (!matches(activity, now, present)) continue;
            if (best == null || activity.priority() > best.priority()) best = activity;
        }
        return best != null ? best.id() : brain.defaultActivity();
    }

    private static boolean hasActivity(TaskBrain brain, String id) {
        for (ActivitySpec activity : brain.activities()) {
            if (activity.id().equals(id)) return true;
        }
        return false;
    }

    private static boolean matches(ActivitySpec activity, TimeOfDayPredicate now, Predicate<MemoryKey<?>> present) {
        if (activity.schedule() != now) return false;
        for (MemoryKey<?> key : activity.activationMemories()) {
            if (!present.test(key)) return false;
        }
        for (MemoryKey<?> key : activity.deactivationMemories()) {
            if (present.test(key)) return false;
        }
        return true;
    }
}