package me.nagasonic.alkatraz.items.wands;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.playerdata.SpellHotbarManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;

/**
 * Blocks all inventory manipulation while a player is in wand hotbar mode,
 * and handles cleanup on disconnect / server shutdown.
 *
 * <p>Register this listener alongside {@link WandListeners} in your main class.
 */
public class WandHotbarListeners implements Listener {

    // -------------------------------------------------------------------------
    // Inventory lock
    // -------------------------------------------------------------------------

    /**
     * Cancel every inventory click while in hotbar mode.
     * HIGH priority so it runs before most other plugins but can still be
     * overridden by HIGHEST-priority listeners if needed.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    private void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!SpellHotbarManager.isActive(p)) return;

        // Always cancel — no item movement is allowed in hotbar mode
        e.setCancelled(true);
    }

    /**
     * Cancel drag events (shift-click spreading, etc.) while in hotbar mode.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    private void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!SpellHotbarManager.isActive(p)) return;

        e.setCancelled(true);
    }

    /**
     * Cancel item drops while in hotbar mode.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    private void onDrop(PlayerDropItemEvent e) {
        if (!SpellHotbarManager.isActive(e.getPlayer())) return;

        e.setCancelled(true);
    }

    /**
     * Prevent the player from switching off the wand slot while in hotbar mode.
     * Slot 8 is the wand; switching away from it would break the mode.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    private void onHeldChange(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!SpellHotbarManager.isActive(p)) return;

        int newSlot = e.getNewSlot();

        // Allow switching to any spell slot (0-7) and the wand slot (8)
        // Slots 0-8 are all valid — the whole hotbar is spell items or the wand
        // Just prevent any scroll-past that might wrap around and confuse state
        // (Bukkit keeps slot within 0-8 anyway, so this is a safety guard)
        if (newSlot < 0 || newSlot > SpellHotbarManager.EXIT_SLOT) {
            e.setCancelled(true);
        }
    }

    /**
     * Block F-key (swap to off-hand) while in hotbar mode.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    private void onSwap(PlayerSwapHandItemsEvent e) {
        if (!SpellHotbarManager.isActive(e.getPlayer())) return;

        e.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Cleanup on disconnect / shutdown
    // -------------------------------------------------------------------------

    /**
     * Restore the player's inventory when they disconnect while in hotbar mode.
     * This ensures items are not lost to the saved-snapshot.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    private void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (SpellHotbarManager.isActive(p)) {
            SpellHotbarManager.exit(p);
        }
    }

    /**
     * Restore all players' inventories when the plugin is disabled
     * (covers /reload and server shutdown).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    private void onPluginDisable(PluginDisableEvent e) {
        // Only care about our own plugin disabling
        // Use the class loader as a proxy — works without a direct plugin reference
        if (!e.getPlugin().getClass().getClassLoader()
                .equals(SpellHotbarManager.class.getClassLoader())) return;

        for (Player p : e.getPlugin().getServer().getOnlinePlayers()) {
            if (SpellHotbarManager.isActive(p)) {
                SpellHotbarManager.exit(p);
            }
        }
    }

    @EventHandler
    private void onClick(PlayerInteractEvent e){
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (SpellHotbarManager.isActive(e.getPlayer())){
            int slot = e.getPlayer().getInventory().getHeldItemSlot();
            if (slot == SpellHotbarManager.EXIT_SLOT){
                SpellHotbarManager.exit(e.getPlayer());
            }else{
                String id = NBT.get(e.getPlayer().getInventory().getItemInMainHand(), nbt -> (String) nbt.getString("spell_id"));
                Spell spell = SpellRegistry.getSpell(id);
                if (spell != null) {
                    spell.cast(e.getPlayer(), SpellHotbarManager.getWand(e.getPlayer()));
                }
            }
        }
    }

    @EventHandler
    private void onEntityClick(PlayerInteractEntityEvent e) {
        if (SpellHotbarManager.isActive(e.getPlayer())){
            int slot = e.getPlayer().getInventory().getHeldItemSlot();
            if (slot == SpellHotbarManager.EXIT_SLOT){
                SpellHotbarManager.exit(e.getPlayer());
            }else{
                String id = NBT.get(e.getPlayer().getInventory().getItemInMainHand(), nbt -> (String) nbt.getString("spell_id"));
                Spell spell = SpellRegistry.getSpell(id);
                if (spell != null) {
                    spell.cast(e.getPlayer(), SpellHotbarManager.getWand(e.getPlayer()));
                }
            }
        }
    }

    @EventHandler
    private void onAtEntityClick(PlayerInteractAtEntityEvent e) {

        if (SpellHotbarManager.isActive(e.getPlayer())){
            int slot = e.getPlayer().getInventory().getHeldItemSlot();
            if (slot == SpellHotbarManager.EXIT_SLOT){
                SpellHotbarManager.exit(e.getPlayer());
            }else{
                String id = NBT.get(e.getPlayer().getInventory().getItemInMainHand(), nbt -> (String) nbt.getString("spell_id"));
                Spell spell = SpellRegistry.getSpell(id);
                if (spell != null) {
                    spell.cast(e.getPlayer(), SpellHotbarManager.getWand(e.getPlayer()));
                }
            }
        }
    }
}
