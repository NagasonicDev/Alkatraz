package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.component.ComponentHandler;
import me.nagasonic.alkatraz.api.magic.component.ComponentHandlerRegistry;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Dispatches Bukkit events (interact, equip, unequip) to registered
 * {@link ComponentHandler} instances based on the components defined
 * in the held item's {@link ItemDefinition}.
 */
public final class MagicItemComponentListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack stack = event.getItem();
        if (stack == null || stack.getType().isAir()) {
            Alkatraz.logInfo("[Debug] CLICK: null or air");
            return;
        }

        Alkatraz.logInfo("[Debug] CLICK: material=" + stack.getType() + " hasMeta=" + stack.hasItemMeta());

        // Only process new-style magic items with PDC data
        var optInstance = MagicItemStack.readInstance(stack);
        if (optInstance.isEmpty()) {
            Alkatraz.logInfo("[Debug] readInstance returned empty");
            // Check if definition key is readable at all
            var defKey = MagicItemStack.readDefinitionKey(stack);
            Alkatraz.logInfo("[Debug] readDefinitionKey: " + defKey.map(k -> k.toString()).orElse("empty"));
            return;
        }

        optInstance.ifPresent(instance -> {
            Alkatraz.logInfo("[Debug] instance OK, defKey=" + instance.definitionKey());

            ItemDefinition definition = MagicItemRegistries.ITEM_DEFINITIONS
                    .get(instance.definitionKey()).orElse(null);
            if (definition == null) {
                Alkatraz.logInfo("[Debug] definition is null for key=" + instance.definitionKey());
                return;
            }

            Alkatraz.logInfo("[Debug] definition found, components=" + definition.components());

            for (NamespacedKey componentKey : definition.components()) {
                Alkatraz.logInfo("[Debug] looking up handler for component=" + componentKey);
                ComponentHandlerRegistry.get(componentKey).ifPresentOrElse(handler -> {
                    Alkatraz.logInfo("[Debug] handler found, calling onInteract");
                    handler.onInteract(event, stack, instance, definition);
                }, () -> {
                    Alkatraz.logInfo("[Debug] NO HANDLER for component=" + componentKey);
                });
            }
        });
    }
}
