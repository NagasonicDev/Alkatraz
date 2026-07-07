package me.nagasonic.alkatraz.api.magic.component;

import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public interface ComponentHandler {

    ComponentType type();

    default void onEquip(Player player, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}

    default void onUnequip(Player player, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}

    default void onInteract(PlayerInteractEvent event, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}

    default void onTrigger(TriggerContext context, ItemStack stack, MagicItemInstance instance, ItemDefinition definition) {}
}
