package me.nagasonic.alkatraz.api.ai.task;

/**
 * The state a {@link Behavior} requires for a memory key before it may run.
 * {@code REGISTERED} mirrors vanilla's notion of a key that must be registered
 * even while its value is absent.
 */
public enum MemoryStatus {
    /** The value must be present. */
    PRESENT,
    /** The value must be absent. */
    ABSENT,
    /** The key must be registered, whether or not a value is present. */
    REGISTERED
}
