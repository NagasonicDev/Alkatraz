package me.nagasonic.alkatraz.mobs.ai.task;

/**
 * A stored memory value together with the absolute tick at which it expires.
 * {@code expireTick == -1} means the entry never expires.
 */
record TimedValue(Object value, long expireTick) {

    TimedValue {
        if (value == null) throw new NullPointerException("value");
    }

    boolean isExpired(long nowTick) {
        return expireTick != -1 && expireTick <= nowTick;
    }
}