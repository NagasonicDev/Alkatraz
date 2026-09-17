package me.nagasonic.alkatraz.api.ai.task;

/**
 * A memory write performed when an activity starts, together with the value to
 * write and its expiry in ticks. A {@code expireTicks} value of {@code -1}
 * means the entry never expires.
 */
public record TimedMemory(MemoryKey<?> key, Object value, long expireTicks) {
    public TimedMemory {
        if (key == null) throw new NullPointerException("key");
        if (value == null) throw new NullPointerException("value");
        if (expireTicks < -1) throw new IllegalArgumentException("expireTicks must be >= -1 (-1 = never expires)");
    }
}