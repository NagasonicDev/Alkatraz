package me.nagasonic.alkatraz.api.ai.task;

import org.bukkit.entity.LivingEntity;

import java.util.Optional;

/**
 * Version-agnostic, per-entity view over the TaskBrain blackboard and the mob it
 * drives. Implemented by the future core engine; behaviors and custom sensors
 * depend only on this interface, never on NMS.
 */
public interface TaskBrainContext {

    /** The Bukkit mob this context wraps. */
    LivingEntity getEntity();

    /** The value stored under {@code key}, if present. */
    <T> Optional<T> getMemory(MemoryKey<T> key);

    /** Stores {@code value} under {@code key} without an expiry. */
    <T> void setMemory(MemoryKey<T> key, T value);

    /** Stores {@code value} under {@code key}, expiring after {@code ticks} ticks. */
    <T> void setMemoryWithExpiry(MemoryKey<T> key, T value, long ticks);

    /** Removes the value (and registration) under {@code key}. */
    void eraseMemory(MemoryKey<?> key);

    /** Whether a value is currently present under {@code key}. */
    boolean hasMemory(MemoryKey<?> key);
}
