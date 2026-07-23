package me.nagasonic.alkatraz.api.progression.requirement;

/**
 * Represents a single requirement that must be met for a player
 * to advance to the next Circle level.
 */
public interface ProgressionRequirement {

    /**
     * Checks whether this requirement is satisfied for the given context.
     *
     * @param context the requirement context containing the player and target Circle
     * @return {@code true} if the requirement is met
     */
    boolean isMet(RequirementContext context);

    /**
     * Returns a human-readable description of this requirement.
     * Defaults to the simple class name.
     *
     * @return the description string
     */
    default String describe() {
        return getClass().getSimpleName();
    }
}
