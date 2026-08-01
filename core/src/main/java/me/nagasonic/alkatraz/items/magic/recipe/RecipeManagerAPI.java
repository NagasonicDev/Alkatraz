package me.nagasonic.alkatraz.items.magic.recipe;

import me.nagasonic.alkatraz.configuration.requirement.RequirementFactory;
import me.nagasonic.alkatraz.items.magic.recipe.adapter.CustomCraftingAdapter;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

/**
 * Public API for querying and mutating the Alkatraz recipe system.
 *
 * <p>Obtain the instance via {@link me.nagasonic.alkatraz.Alkatraz#getRecipeManager()}. This
 * interface is implemented by the core plugin; third-party plugins depending on the
 * {@code alkatraz-core} artifact may call any of these methods at runtime.</p>
 */
public interface RecipeManagerAPI {

    /**
     * Returns the recipe registered under the given key, if any.
     *
     * @param key the recipe key
     * @return an {@link Optional} containing the recipe, or an empty {@link Optional} if no recipe
     *         is registered under that key
     */
    Optional<AlkatrazRecipe> getRecipe(NamespacedKey key);

    /**
     * Returns all currently registered recipes.
     *
     * @return an immutable view of the registered recipes; never {@code null}
     */
    Collection<AlkatrazRecipe> getRecipes();

    /**
     * Checks whether the given player has unlocked the recipe.
     *
     * <p>Recipes without requirements are always unlocked. Returns {@code false} for unknown keys
     * or a {@code null} key.</p>
     *
     * @param player the player to check
     * @param key    the recipe key
     * @return {@code true} if the player has the recipe unlocked
     */
    boolean isUnlocked(Player player, NamespacedKey key);

    /**
     * Unlocks the recipe for the given player.
     *
     * <p>Idempotent: unlocking an already-unlocked recipe is a no-op. Fires
     * {@link me.nagasonic.alkatraz.events.RecipeUnlockedEvent} and sends the configured unlock
     * notifications.</p>
     *
     * @param player the player to unlock the recipe for
     * @param key    the recipe key
     */
    void unlock(Player player, NamespacedKey key);

    /**
     * Locks the recipe for the given player, undoing a previous unlock.
     *
     * <p>Idempotent. Does not fire an event or notification.</p>
     *
     * @param player the player to lock the recipe for
     * @param key    the recipe key
     */
    void lock(Player player, NamespacedKey key);

    /**
     * Checks whether the given player may craft the recipe right now.
     *
     * <p>Returns {@code false} for unknown keys. Recipes without requirements are always craftable.
     * Otherwise the player must either have unlocked the recipe or currently satisfy every
     * requirement.</p>
     *
     * @param player the player to check
     * @param key    the recipe key
     * @return {@code true} if the player may craft the recipe
     */
    boolean canCraft(Player player, NamespacedKey key);

    /**
     * Registers a custom requirement type used by recipe YAML files.
     *
     * <p>The builder receives the {@link me.nagasonic.alkatraz.spells.Spell} being loaded and the
     * requirement's {@code ConfigurationSection}; see
     * {@link me.nagasonic.alkatraz.configuration.requirement.RequirementFactory}.</p>
     *
     * @param type    the requirement type id (case-insensitive)
     * @param builder the builder that constructs the requirement
     * @throws IllegalArgumentException if {@code type} or {@code builder} is {@code null} or blank
     */
    void registerRequirementType(String type, RequirementFactory.Builder builder);

    /**
     * Registers a third-party custom crafting adapter.
     *
     * <p>See {@link CustomCraftingAdapter} and the
     * <i>Custom Crafting Adapter</i> guide for the extension contract.</p>
     *
     * @param adapter the adapter to register
     */
    void registerCustomAdapter(CustomCraftingAdapter adapter);

    /**
     * Reloads all recipe definitions from disk.
     *
     * <p>Unregisters every recipe, re-parses the {@code magic/recipes} YAML files and re-registers
     * native Bukkit recipes. Safe to call at runtime.</p>
     */
    void reload();
}
