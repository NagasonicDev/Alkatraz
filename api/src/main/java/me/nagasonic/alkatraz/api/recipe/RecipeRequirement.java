package me.nagasonic.alkatraz.api.recipe;

import org.bukkit.entity.Player;

/**
 * Third-party-facing requirement contract for the recipe system.
 *
 * <p>Implement this interface to provide custom recipe gating logic and register it through
 * {@code RecipeManagerAPI#registerRequirementType(String, RequirementFactory.Builder)}, adapting it
 * into the core {@code Requirement} with {@code RecipeRequirementAdapter}.</p>
 *
 * <p>This interface lives in the {@code api} module so it can be depended on without the core
 * plugin; it intentionally mirrors the core requirement contract.</p>
 */
public interface RecipeRequirement {

    /**
     * Checks whether the given player meets this requirement.
     *
     * @param player the player to check
     * @return {@code true} if the player meets this requirement
     */
    boolean isMet(Player player);

    /**
     * Returns a human-readable description of this requirement.
     *
     * @return the description
     */
    String getDescription();

    /**
     * Returns the progress toward meeting this requirement, clamped to {@code 0-100}.
     *
     * <p>Used for progress bars in the recipe book. Defaults to {@code 100} when met and
     * {@code 0} otherwise.</p>
     *
     * @param player the player to check
     * @return progress from {@code 0} to {@code 100}
     */
    default int getProgress(Player player) {
        return isMet(player) ? 100 : 0;
    }
}
