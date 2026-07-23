package me.nagasonic.alkatraz.api.progression.circle;

/**
 * The nine milestone Circles available to players.
 */
public enum CircleLevel {
    /** First Circle level (value 1). */
    I(1),
    /** Second Circle level (value 2). */
    II(2),
    /** Third Circle level (value 3). */
    III(3),
    /** Fourth Circle level (value 4). */
    IV(4),
    /** Fifth Circle level (value 5). */
    V(5),
    /** Sixth Circle level (value 6). */
    VI(6),
    /** Seventh Circle level (value 7). */
    VII(7),
    /** Eighth Circle level (value 8). */
    VIII(8),
    /** Ninth Circle level (value 9). */
    IX(9);

    private final int value;

    CircleLevel(int value) {
        this.value = value;
    }

    /**
     * Returns the integer value of this Circle level.
     *
     * @return the Circle level number (1-9)
     */
    public int getValue() {
        return value;
    }

    /**
     * Checks whether the given integer is a valid Circle level (1-9).
     *
     * @param value the value to check
     * @return {@code true} if within range
     */
    public static boolean isValid(int value) {
        return value >= I.value && value <= IX.value;
    }
}
