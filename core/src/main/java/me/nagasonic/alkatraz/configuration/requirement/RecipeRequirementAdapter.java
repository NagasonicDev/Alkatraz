package me.nagasonic.alkatraz.configuration.requirement;

import me.nagasonic.alkatraz.api.recipe.RecipeRequirement;
import org.bukkit.entity.Player;

/**
 * Adapts a third-party {@link RecipeRequirement} into the core {@link Requirement} contract.
 *
 * <p>Use inside a {@link RequirementFactory.Builder} when registering a custom requirement type,
 * for example:</p>
 *
 * <pre>{@code
 * Alkatraz.getRecipeManager().registerRequirementType("my_type",
 *         (spell, section) -> new RecipeRequirementAdapter(new MyRecipeRequirement(section)));
 * }</pre>
 */
public class RecipeRequirementAdapter implements Requirement {

    private final RecipeRequirement delegate;

    public RecipeRequirementAdapter(RecipeRequirement delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isMet(Player player) {
        return delegate.isMet(player);
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public int getProgress(Player player) {
        return delegate.getProgress(player);
    }
}
