package me.nagasonic.alkatraz.progression.requirement;

/**
 * A single independently evaluated progression requirement.
 */
public interface ProgressionRequirement {

    boolean isMet(RequirementContext context);

    default String describe() {
        return getClass().getSimpleName();
    }
}
