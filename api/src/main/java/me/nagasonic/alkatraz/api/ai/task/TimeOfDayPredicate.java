package me.nagasonic.alkatraz.api.ai.task;

/**
 * Time-of-day window in which an {@link ActivitySpec} is eligible to run.
 */
public enum TimeOfDayPredicate {
    DAY,
    NIGHT,
    DAWN,
    DUSK,
    MIDNIGHT
}
