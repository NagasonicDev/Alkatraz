package me.nagasonic.alkatraz.api.progression.requirement;

public interface ProgressionRequirement {

    boolean isMet(RequirementContext context);

    default String describe() {
        return getClass().getSimpleName();
    }
}
