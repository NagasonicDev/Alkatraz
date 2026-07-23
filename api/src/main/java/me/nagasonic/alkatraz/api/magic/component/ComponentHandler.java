package me.nagasonic.alkatraz.api.magic.component;

import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles lifecycle and event callbacks for a specific {@link ComponentType} on a magic item.
 * <p>
 * Implementations define how a component reacts when its owning item is equipped, unequipped,
 * interacted with, triggered, swapped, dropped, or when the holder dies. All methods have
 * default no-op implementations so handlers only need to override the events they care about.
 */
public interface ComponentHandler {

    /**
     * Returns the {@link ComponentType} this handler is registered for.
     *
     * @return the component type this handler manages
     */
    ComponentType type();

    /**
     * Called when a player equips (wears or holds) an item that carries this component.
     *
     * @param player     the player who equipped the item
     * @param stack      the item stack being equipped
     * @param instance   the resolved magic item instance
     * @param definition the item definition for this magic item
     */
    default void onEquip(Player player, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}

    /**
     * Called when a player unequips (removes) an item that carries this component.
     *
     * @param player     the player who unequipped the item
     * @param stack      the item stack being unequipped
     * @param instance   the resolved magic item instance
     * @param definition the item definition for this magic item
     */
    default void onUnequip(Player player, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}

    /**
     * Called when a player interacts (right-click or left-click) while holding an item with this component.
     *
     * @param event      the player interact event
     * @param stack      the item stack being used
     * @param instance   the resolved magic item instance
     * @param definition the item definition for this magic item
     */
    default void onInteract(PlayerInteractEvent event, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}

    /**
     * Called when this component is triggered through the magic item's trigger system.
     *
     * @param context    the trigger context containing execution details
     * @param stack      the item stack that was triggered
     * @param instance   the resolved magic item instance
     * @param definition the item definition for this magic item
     */
    default void onTrigger(TriggerContext context, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}

    /**
     * Called when a player swaps the item with this component to their off-hand.
     *
     * @param event      the swap hand items event
     * @param stack      the item stack being swapped
     * @param instance   the resolved magic item instance
     * @param definition the item definition for this magic item
     */
    default void onSwap(PlayerSwapHandItemsEvent event, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}

    /**
     * Called when a player drops an item that carries this component.
     *
     * @param event      the player drop item event
     * @param stack      the item stack being dropped
     * @param instance   the resolved magic item instance
     * @param definition the item definition for this magic item
     */
    default void onDrop(PlayerDropItemEvent event, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}

    /**
     * Called when a player dies while carrying an item with this component.
     *
     * @param event      the player death event
     * @param player     the player who died
     * @param stack      the item stack on the player at death
     * @param instance   the resolved magic item instance
     * @param definition the item definition for this magic item
     */
    default void onDeath(PlayerDeathEvent event, Player player, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}
}
