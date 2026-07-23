package me.nagasonic.alkatraz.items.magic.adapter;

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
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Dispatches Bukkit events (interact, equip, unequip) to registered
 * {@link ComponentHandler} instances based on the components defined
 * in the held item's {@link ItemDefinition}.
 */
public final class MagicItemComponentListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack stack = event.getItem();
        if (stack == null || stack.getType().isAir()) {
            return;
        }

        // Only process new-style magic items with PDC data
        Optional<MagicItemInstance> optInstance = MagicItemStack.readInstance(stack);
        if (optInstance.isEmpty()) {
            return;
        }

        optInstance.ifPresent(instance -> {
            ItemDefinition definition = MagicItemRegistries.ITEM_DEFINITIONS
                    .get(instance.definitionKey()).orElse(null);
            if (definition == null) {
                return;
            }
            
            for (NamespacedKey componentKey : definition.components()) {
                ComponentHandlerRegistry.get(componentKey).ifPresent(handler -> {
                    handler.onInteract(event, stack, instance, definition);
                });
            }
        });
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        
        ItemStack stack = player.getItemInHand();
        if (stack == null || stack.getType().isAir()) {
            return;
        }

        // Only process new-style magic items with PDC data
        Optional<MagicItemInstance> optInstance = MagicItemStack.readInstance(stack);
        if (optInstance.isEmpty()) {
            return;
        }

        optInstance.ifPresent(instance -> {
            ItemDefinition definition = MagicItemRegistries.ITEM_DEFINITIONS
                    .get(instance.definitionKey()).orElse(null);
            if (definition == null) {
                return;
            }
            
            for (NamespacedKey componentKey : definition.components()) {
                ComponentHandlerRegistry.get(componentKey).ifPresent(handler -> {
                    if (handler instanceof me.nagasonic.alkatraz.items.magic.component.handler.wand.WandComponentHandler) {
                        // Convert attack event to interact event for wand handler
                        PlayerInteractEvent interactEvent = new PlayerInteractEvent(
                            player, 
                            Action.LEFT_CLICK_AIR, 
                            player.getItemInHand(), 
                            null, 
                            null
                        );
                        handler.onInteract(interactEvent, stack, instance, definition);
                    }
                });
            }
        });
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack stack = event.getPlayer().getItemInHand();
        if (stack == null || stack.getType().isAir()) {
            return;
        }

        // Only process new-style magic items with PDC data
        Optional<MagicItemInstance> optInstance = MagicItemStack.readInstance(stack);
        if (optInstance.isEmpty()) {
            return;
        }

        optInstance.ifPresent(instance -> {
            ItemDefinition definition = MagicItemRegistries.ITEM_DEFINITIONS
                    .get(instance.definitionKey()).orElse(null);
            if (definition == null) {
                return;
            }
            
            // Convert entity interact to regular interact event
            PlayerInteractEvent interactEvent = new PlayerInteractEvent(
                event.getPlayer(),
                Action.RIGHT_CLICK_AIR,
                stack,
                null,
                null
            );
            
            for (NamespacedKey componentKey : definition.components()) {
                ComponentHandlerRegistry.get(componentKey).ifPresent(handler -> {
                    handler.onInteract(interactEvent, stack, instance, definition);
                });
            }
        });
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        ItemStack stack = null;
        if (mainHand != null && !mainHand.getType().isAir()) {
            stack = mainHand;
        } else if (offHand != null && !offHand.getType().isAir()) {
            stack = offHand;
        }

        if (stack == null) {
            return;
        }

        Optional<MagicItemInstance> optInstance = MagicItemStack.readInstance(stack);
        if (optInstance.isEmpty()) {
            return;
        }

        MagicItemInstance instance = optInstance.get();
        ItemDefinition definition = MagicItemRegistries.ITEM_DEFINITIONS
                .get(instance.definitionKey()).orElse(null);
        if (definition == null) {
            return;
        }

        boolean handled = false;
        for (NamespacedKey componentKey : definition.components()) {
            ComponentHandler handler = ComponentHandlerRegistry.get(componentKey).orElse(null);
            if (handler != null) {
                handler.onSwap(event, stack, instance, definition);
                handled = true;
            }
        }

        if (handled) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (stack == null || stack.getType().isAir()) {
            return;
        }

        Optional<MagicItemInstance> optInstance = MagicItemStack.readInstance(stack);
        if (optInstance.isEmpty()) {
            return;
        }

        MagicItemInstance instance = optInstance.get();
        ItemDefinition definition = MagicItemRegistries.ITEM_DEFINITIONS
                .get(instance.definitionKey()).orElse(null);
        if (definition == null) {
            return;
        }

        boolean handled = false;
        for (NamespacedKey componentKey : definition.components()) {
            ComponentHandler handler = ComponentHandlerRegistry.get(componentKey).orElse(null);
            if (handler != null) {
                handler.onDrop(event, stack, instance, definition);
                handled = true;
            }
        }

        if (handled) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        for (ItemStack stack : event.getDrops()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            Optional<MagicItemInstance> optInstance = MagicItemStack.readInstance(stack);
            if (optInstance.isEmpty()) {
                continue;
            }

            MagicItemInstance instance = optInstance.get();
            ItemDefinition definition = MagicItemRegistries.ITEM_DEFINITIONS
                    .get(instance.definitionKey()).orElse(null);
            if (definition == null) {
                continue;
            }

            for (NamespacedKey componentKey : definition.components()) {
                ComponentHandlerRegistry.get(componentKey).ifPresent(handler -> {
                    handler.onDeath(event, player, stack, instance, definition);
                });
            }
        }
    }
}
