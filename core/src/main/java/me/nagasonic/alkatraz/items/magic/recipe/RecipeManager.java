package me.nagasonic.alkatraz.items.magic.recipe;

import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.configuration.requirement.RequirementFactory;
import me.nagasonic.alkatraz.items.magic.MagicItemBootstrap;
import me.nagasonic.alkatraz.items.magic.recipe.adapter.CraftingEventRouter;
import me.nagasonic.alkatraz.items.magic.recipe.adapter.CustomCraftingAdapter;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Core implementation of {@link RecipeManagerAPI}.
 *
 * <p>Accessed through {@link me.nagasonic.alkatraz.Alkatraz#getRecipeManager()}.</p>
 */
public final class RecipeManager implements RecipeManagerAPI {

    @Override
    public Optional<AlkatrazRecipe> getRecipe(NamespacedKey key) {
        return Optional.ofNullable(RecipeRegistry.get(key));
    }

    @Override
    public Collection<AlkatrazRecipe> getRecipes() {
        return new ArrayList<>(RecipeRegistry.getAll());
    }

    @Override
    public boolean isUnlocked(Player player, NamespacedKey key) {
        return key != null && UnlockManager.isUnlocked(player, key.toString());
    }

    @Override
    public void unlock(Player player, NamespacedKey key) {
        if (key == null) return;
        UnlockManager.unlock(player, key.toString());
    }

    @Override
    public void lock(Player player, NamespacedKey key) {
        if (key == null) return;
        UnlockManager.lock(player, key.toString());
    }

    @Override
    public boolean canCraft(Player player, NamespacedKey key) {
        if (player == null || key == null) return false;
        List<Requirement> requirements = MagicItemRecipeManager.getRequirements(key);
        if (requirements.isEmpty()) return true;
        if (isUnlocked(player, key)) return true;
        for (Requirement requirement : requirements) {
            if (!requirement.isMet(player)) return false;
        }
        return true;
    }

    @Override
    public void registerRequirementType(String type, RequirementFactory.Builder builder) {
        RequirementFactory.register(type, builder);
    }

    @Override
    public void registerCustomAdapter(CustomCraftingAdapter adapter) {
        if (adapter == null) return;
        CraftingEventRouter.register(adapter);
    }

    @Override
    public void reload() {
        MagicItemBootstrap.reload();
    }
}
