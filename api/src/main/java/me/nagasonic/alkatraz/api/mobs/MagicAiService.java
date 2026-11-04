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

    /* --- Goal-brain profile management (MagicBrainService aliases for discoverability) --- */

    /** Assigns a brain profile by id (alias for {@link #setBrain}). */
    default boolean setProfile(LivingEntity entity, String profileId) {
        return setBrain(entity, profileId);
    }

    /** Re-applies the entity's current profile from disk (alias for {@link #reloadBrain}). */
    default boolean reloadProfile(LivingEntity entity) {
        return reloadBrain(entity);
    }

    /** Removes the entity's brain make it passive (alias for {@link #clearBrain}). */
    default boolean clearProfile(LivingEntity entity) {
        return clearBrain(entity);
    }

    /** Returns the assigned profile id (alias for {@link #getBrain}). */
    default String getProfile(LivingEntity entity) {
        return getBrain(entity);
    }

    /* --- Navigation / attributes (core-side options) --- */

    /** Swaps the entity's navigation to the given kind. Runtime supported once registered; default no-op. */
    default boolean setNavigation(LivingEntity entity, me.nagasonic.alkatraz.api.ai.navigation.NavigationKind kind) {
        return false;
    }

    /** Sets one mob attribute base value from its Bukkit name. */
    default boolean setAttribute(LivingEntity entity, String name, double value) {
        return false;
    }

    /* --- Sensors --- */

    /** Adds a sensor spec to the entity's running brain. Runtime supported once implemented; default no-op. */
    default boolean addSensor(LivingEntity entity, me.nagasonic.alkatraz.api.ai.task.Sensor sensor) {
        return false;
    }

    /** Removes a previously added sensor spec. Default no-op. */
    default boolean removeSensor(LivingEntity entity, me.nagasonic.alkatraz.api.ai.task.Sensor sensor) {
        return false;
    }

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

    /**
     * Returns the registered service instance, or {@code null} before core registers it.
     * Callers must null-check (or call {@link #setInstance(MagicAiService)} during testing).
     */
    static @Nullable MagicAiService getInstance() {
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