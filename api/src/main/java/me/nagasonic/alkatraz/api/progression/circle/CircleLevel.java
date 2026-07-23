package me.nagasonic.alkatraz.api.progression.circle;

/**
 * The nine milestone Circles available to players.
 */
public enum CircleLevel {
    I(1),
    II(2),
    III(3),
    IV(4),
    V(5),
    VI(6),
    VII(7),
    VIII(8),
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
