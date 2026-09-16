package me.nagasonic.alkatraz.api.ai.task;

/**
 * A memory write performed when an activity starts, together with its expiry
 * in ticks.
 */
public record TimedMemory(MemoryKey<?> key, long expireTicks) {
    public TimedMemory {
        if (key == null) throw new NullPointerException("key");
        if (expireTicks < 0) throw new IllegalArgumentException("expireTicks must be non-negative");
    }
}
