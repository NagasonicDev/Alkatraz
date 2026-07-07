package me.nagasonic.alkatraz.config;

public enum VerbosityLevel {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    VERY_HIGH(3),
    DEBUG(4);

    private final int level;

    VerbosityLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean isAtLeast(VerbosityLevel other) {
        return this.level >= other.level;
    }

    public static VerbosityLevel fromString(String value) {
        if (value == null) return NORMAL;
        try {
            return VerbosityLevel.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
