package me.nagasonic.alkatraz.api.mobs;

import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * TaskBrain runtime surface: blackboard access and activity pinning for any
 * living mob running a task brain, layered on {@link MagicBrainService}.
 *
 * <p>Boolean-returning methods follow {@link MagicBrainService} threading
 * semantics: main-thread calls are synchronous; off-thread calls are scheduled
 * onto the main thread and return {@code true} once the request is accepted.
 */
public interface MagicAiService extends MagicBrainService {

    /** Stores a value on the entity's blackboard. False if no engine is attached. */
    <T> boolean setMemory(LivingEntity entity, MemoryKey<T> key, T value);

    /** Stores a value expiring after {@code expireTicks} ticks. */
    <T> boolean setMemoryWithExpiry(LivingEntity entity, MemoryKey<T> key, T value, long expireTicks);

    /** The value stored under {@code key}, if the entity has an engine and the key. */
    <T> Optional<T> getMemory(LivingEntity entity, MemoryKey<T> key);

    /** Removes the value under {@code key}. */
    boolean eraseMemory(LivingEntity entity, MemoryKey<?> key);

    /** Pins ({@code activityId}) or unpins ({@code null}) the active activity. */
    boolean overrideActivity(LivingEntity entity, @Nullable String activityId);

    /** The currently running activity id, if the entity has an attached engine. */
    Optional<String> currentActivity(LivingEntity entity);

    /** Returns the registered service instance. */
    static MagicAiService getInstance() {
        return Holder.INSTANCE;
    }

    /** Registers the service instance (called by core at startup). */
    static void setInstance(MagicAiService service) {
        Holder.INSTANCE = service;
    }

    /** Holds the singleton; mirrors {@code MagicBrainService} holder semantics. */
    final class Holder {
        private static MagicAiService INSTANCE;
        private Holder() {}
    }
}