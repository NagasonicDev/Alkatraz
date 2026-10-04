package me.nagasonic.alkatraz.spells.types;

import java.util.Locale;

/**
 * What a spell is judged on. Roles describe what a spell DOES; the element
 * archetypes in spec section 5.1 describe what an ELEMENT is for, and constrain
 * which roles an element may use at all. See {@code SpellBudgets.isAllowed}.
 */
public enum SpellRole {

    /** Judged on health damage per cast. */
    BURST,

    /** Judged on total damage-over-time per cast. Damages a target at most once per 20 ticks. */
    SUSTAIN,

    /** Judged on blocks destroyed per cast. */
    TERRAIN,

    /** Judged on blocked block-seconds. Incidental damage capped at the same-circle BURST budget. */
    DENIAL,

    /** Judged on nothing. No budget. */
    UTILITY;

    /**
     * Parses the {@code role:} key from a spell yml. Returns {@code null} for an
     * absent or unrecognised value so the caller can log and substitute a default
     * rather than silently coercing a typo to a valid role.
     */
    public static SpellRole parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (SpellRole role : values()) {
            if (role.name().equals(raw.trim().toUpperCase(Locale.ROOT))) return role;
        }
        return null;
    }
}
