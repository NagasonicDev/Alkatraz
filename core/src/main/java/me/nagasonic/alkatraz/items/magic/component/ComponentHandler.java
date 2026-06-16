package me.nagasonic.alkatraz.items.magic.component;

import me.nagasonic.alkatraz.items.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.items.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Optional behavior hook for a {@link ComponentType}. Handlers are registered in a
 * separate map so component metadata stays data-only.
 */
public interface ComponentHandler {

    ComponentType type();

    default void onEquip(Player player, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}

    default void onUnequip(Player player, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}

    default void onInteract(PlayerInteractEvent event, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}

    default void onTrigger(TriggerContext context, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}
}
