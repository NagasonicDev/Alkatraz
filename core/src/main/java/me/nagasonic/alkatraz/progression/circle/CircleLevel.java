package me.nagasonic.alkatraz.progression.circle;

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

    public int getValue() {
        return value;
    }

    public static boolean isValid(int value) {
        return value >= I.value && value <= IX.value;
    }
}
