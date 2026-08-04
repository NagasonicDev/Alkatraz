package me.nagasonic.alkatraz.items.magic.listener;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.playerdata.SpellHotbarManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellCastValidator;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.WandUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static me.nagasonic.alkatraz.util.Utils.notAir;

public class CastEventListener implements Listener {
    private static final Map<String, Integer> level = new HashMap<>();
    private static final Map<String, Float> exp = new HashMap<>();
    private static final Map<String, Integer> changedExp = new HashMap<>();

    private static final Set<UUID> spellDamageTargets = new HashSet<>();

    public static void markSpellDamage(LivingEntity target) {
        spellDamageTargets.add(target.getUniqueId());
    }

    @EventHandler
    private void onAttack(EntityDamageByEntityEvent e) {
        spellDamageTargets.remove(e.getEntity().getUniqueId());
    }

    // ============================
    // MANA DISPLAY & CAST CODE RESET
    // ============================

    @EventHandler
    private void onHandSwitch(PlayerItemHeldEvent e) {
        if (SpellHotbarManager.isActive(e.getPlayer())) return;
        ItemStack wand = e.getPlayer().getInventory().getItem(e.getPreviousSlot());
        if (wand != null){
            if (wand.getType() != Material.AIR && wand.getAmount() != 0) {
                if (WandUtils.isWand(wand)) {
                    NBT.modify(wand, nbt -> { nbt.setString("cast_code", ""); });
                }
            }
        }
    }

    @EventHandler
    private void onWandMana(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (SpellHotbarManager.isActive(p)) return;
        ItemStack prev = p.getInventory().getItem(e.getPreviousSlot());
        ItemStack curr = p.getInventory().getItem(e.getNewSlot());
        if (prev != null) {
            if (prev.getType() != Material.AIR && prev.getAmount() != 0){
                if (isManaDisplayItem(prev)) {
                    switchFrom(p);
                }
            }
        }
        if (curr != null){
            if (curr.getType() != Material.AIR && curr.getAmount() != 0){
                if (isManaDisplayItem(curr)) {
                    switchTo(p, curr);
                }
            }
        }
    }

    @EventHandler
    private void onInventoryClick(InventoryClickEvent e) throws InterruptedException {
        if (SpellHotbarManager.isActive((Player) e.getWhoClicked())) return;
        if (e.getClickedInventory() == e.getWhoClicked().getInventory()){
            Player p = (Player) e.getWhoClicked();
            if (e.getAction() == InventoryAction.SWAP_WITH_CURSOR){
                if (e.getSlot() == p.getInventory().getHeldItemSlot()){
                    ItemStack cursor = e.getCursor();
                    if (cursor != null){
                        if (cursor.getType() != Material.AIR && cursor.getAmount() != 0){
                            if (isManaDisplayItem(cursor)) { switchTo(p, cursor); }
                        }
                    }
                    ItemStack curr = e.getCurrentItem();
                    if (curr != null){
                        if (curr.getType() != Material.AIR && curr.getAmount() != 0){
                            if (isManaDisplayItem(curr)) { switchFrom(p); }
                        }
                    }
                }
            }else if (e.getAction() == InventoryAction.HOTBAR_SWAP){
                if (e.getHotbarButton() == p.getInventory().getHeldItemSlot()){
                    ItemStack swapped = e.getCurrentItem();
                    ItemStack swappedWith = p.getInventory().getItem(e.getHotbarButton());
                    if (notAir(swappedWith) && notAir(swapped)){
                        boolean clickedIsWand = isManaDisplayItem(swapped);
                        boolean hotbarIsWand = isManaDisplayItem(swappedWith);
                        if (clickedIsWand && !hotbarIsWand) { switchTo(p, swapped); }
                        else if (!clickedIsWand && hotbarIsWand) { switchFrom(p); }
                    } else if (notAir(swapped) && !notAir(swappedWith)) {
                        if (isManaDisplayItem(swapped)){ switchTo(p, swapped); }
                    } else if (notAir(swappedWith) && !notAir(swapped)) {
                        if (isManaDisplayItem(swappedWith)){ switchFrom(p); }
                    }
                } else if (e.getSlot() == p.getInventory().getHeldItemSlot()) {
                    ItemStack swapped = e.getCurrentItem();
                    ItemStack swappedWith = p.getInventory().getItem(e.getHotbarButton());
                    if (notAir(swappedWith) && notAir(swapped)){
                        boolean clickedIsWand = isManaDisplayItem(swapped);
                        boolean hotbarIsWand = isManaDisplayItem(swappedWith);
                        if (clickedIsWand && !hotbarIsWand) { switchFrom(p); }
                        else if (!clickedIsWand && hotbarIsWand) { switchTo(p, swapped); }
                    } else if (notAir(swapped) && !notAir(swappedWith)) {
                        if (isManaDisplayItem(swapped)){ switchFrom(p); }
                    } else if (notAir(swappedWith) && !notAir(swapped)) {
                        if (isManaDisplayItem(swappedWith)){ switchTo(p, swappedWith); }
                    }
                }
            }else if (e.getAction() == InventoryAction.PICKUP_ALL || e.getAction().equals(InventoryAction.PICKUP_HALF) || e.getAction().equals(InventoryAction.PICKUP_ONE) || e.getAction().equals(InventoryAction.PICKUP_SOME)){
                ItemStack curr = e.getCurrentItem();
                if (curr != null){
                    if (curr.getType() != Material.AIR && curr.getAmount() != 0){
                        if (isManaDisplayItem(curr)) {
                            if (e.getSlot() == p.getInventory().getHeldItemSlot()){ switchFrom(p); }
                        }
                    }
                }
            }else if (e.getAction() == InventoryAction.PLACE_ALL || e.getAction().equals(InventoryAction.PLACE_ONE) || e.getAction().equals(InventoryAction.PLACE_SOME)){
                ItemStack cursor = e.getCursor();
                if (cursor != null){
                    if (cursor.getType() != Material.AIR && cursor.getAmount() != 0){
                        if (isManaDisplayItem(cursor)) {
                            if (e.getSlot() == p.getInventory().getHeldItemSlot()){ switchTo(p, cursor); }
                        }
                    }
                }
            }else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY){
                if (e.getSlot() >= 9){
                    int mainSlot = p.getInventory().getHeldItemSlot();
                    if (p.getInventory().getItem(mainSlot) == null){
                        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                            if (mainSlot == p.getInventory().getHeldItemSlot()){
                                if (p.getInventory().getItem(mainSlot) != null){
                                    ItemStack wand = p.getInventory().getItem(mainSlot);
                                    if (wand != null){
                                        if (wand.getType() != Material.AIR && wand.getAmount() != 0){
                                            if (isManaDisplayItem(wand)) { switchTo(p, wand); }
                                        }
                                    }
                                }
                            }
                        }, 1L);
                    }
                }else if (e.getSlot() < 9) {
                    ItemStack curr = e.getCurrentItem();
                    if (curr != null){
                        if (curr.getType() != Material.AIR && curr.getAmount() != 0){
                            if (isManaDisplayItem(curr)) { switchFrom(p); }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onDrop(PlayerDropItemEvent e){
        Player p = e.getPlayer();
        if (SpellHotbarManager.isActive(p)) return;
        ItemStack dropped = e.getItemDrop().getItemStack();
        ItemStack hand = p.getItemInHand();
        if (dropped.getType() != Material.AIR && dropped.getAmount() != 0){
            if (hand.getAmount() != 0 && hand.getType() != Material.AIR) {
                if (!isManaDisplayItem(hand)){ switchFrom(p); }
            } else if (isManaDisplayItem(dropped)){ switchFrom(p); }
        }
    }

    @EventHandler
    private void onPickup(PlayerPickupItemEvent e){
        ItemStack item = e.getItem().getItemStack();
        Player p = e.getPlayer();
        if (SpellHotbarManager.isActive(p)) return;
        if (item.getType() != Material.AIR && item.getAmount() != 0){
            if (isManaDisplayItem(item)){
                for (int i = 0; i <= p.getInventory().getHeldItemSlot(); i++){
                    ItemStack s = p.getInventory().getItem(i);
                    if (s != null){
                        if (s.getAmount() == 0 || s.getType() == Material.AIR){
                            switchTo(p, item);
                            break;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onExp(PlayerExpChangeEvent e) {
        Player p = e.getPlayer();
        if (!changedExp.containsKey(p.getUniqueId().toString())){
            changedExp.put(p.getUniqueId().toString(), e.getAmount());
        }else {
            int current = changedExp.get(p.getUniqueId().toString());
            changedExp.replace(p.getUniqueId().toString(), current + e.getAmount());
        }
    }

    // ============================
    // CONNECT / DISCONNECT
    // ============================

    @EventHandler
    private void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        ItemStack wand = p.getInventory().getItem(p.getInventory().getHeldItemSlot());
        if (wand != null){
            if (wand.getType() != Material.AIR && wand.getAmount() != 0){
                if (isManaDisplayItem(wand)){ switchFrom(p); }
            }
        }
        MagicProfile data = ProfileManager.getProfile(e.getPlayer().getUniqueId(), MagicProfile.class);
        if (data.isCasting()){ data.setCasting(false); }
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        SpellHotbarManager.restoreIfNeeded(p);
        ItemStack wand = p.getInventory().getItem(p.getInventory().getHeldItemSlot());
        if (wand != null){
            if (wand.getType() != Material.AIR && wand.getAmount() != 0){
                if (isManaDisplayItem(wand)){
                    Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                        switchTo(p, wand);
                    }, 1L);
                }
            }
        }
    }

    // ============================
    // HOTBAR MODE EVENTS
    // ============================

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && SpellHotbarManager.isActive(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && SpellHotbarManager.isActive(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarDrop(PlayerDropItemEvent e) {
        if (SpellHotbarManager.isActive(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarItemHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!SpellHotbarManager.isActive(p)) return;
        if (e.getNewSlot() <= SpellHotbarManager.SPELL_SLOT_COUNT) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarSwap(PlayerSwapHandItemsEvent e) {
        if (SpellHotbarManager.isActive(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (SpellHotbarManager.isActive(p)) {
            SpellHotbarManager.exit(p);
            p.saveData();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarPluginDisable(PluginDisableEvent e) {
        if (e.getPlugin() == Alkatraz.getInstance()) {
            SpellHotbarManager.exitAll();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!SpellHotbarManager.isActive(p)) return;
        e.setCancelled(true);
        if (ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class).isCasting()) return;
        ItemStack wand = SpellHotbarManager.getWand(p);
        if (wand == null) return;
        ItemStack item = e.getItem();
        if (item == null) return;

        if (WandUtils.isWand(item)) {
            if (!SpellHotbarManager.isJustEntered(p)) {
                SpellHotbarManager.exit(p);
            }
            return;
        }

        String spellId = NBT.get(item, nbt -> (String) nbt.getString("spell_id"));
        if (spellId == null) return;
        Spell spell = SpellRegistry.getSpell(spellId);
        if (spell == null) return;
        if (SpellCastValidator.canCast(p, wand, spell)){
            spell.cast(p, wand);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (!SpellHotbarManager.isActive(p)) return;
        e.setCancelled(true);
        if (ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class).isCasting()) return;
        ItemStack wand = SpellHotbarManager.getWand(p);
        if (wand == null) return;
        ItemStack item = p.getInventory().getItem(p.getInventory().getHeldItemSlot());
        if (item == null) return;

        if (WandUtils.isWand(item)) {
            if (!SpellHotbarManager.isJustEntered(p)) {
                SpellHotbarManager.exit(p);
            }
            return;
        }

        String spellId = NBT.get(item, nbt -> (String) nbt.getString("spell_id"));
        if (spellId == null) return;
        Spell spell = SpellRegistry.getSpell(spellId);
        if (spell == null) return;
        if (SpellCastValidator.canCast(p, wand, spell)){
            spell.cast(p, wand);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if (SpellHotbarManager.isActive(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!SpellHotbarManager.isActive(p)) return;

        UUID uuid = p.getUniqueId();
        ItemStack[] savedStorage = SpellHotbarManager.peekSavedContents(uuid);
        ItemStack savedOffhand = SpellHotbarManager.peekSavedOffhand(uuid);

        SpellHotbarManager.cleanupForDeath(p);

        e.getDrops().clear();
        if (savedStorage != null) {
            for (ItemStack item : savedStorage) {
                if (item != null && item.getType() != Material.AIR) {
                    e.getDrops().add(item.clone());
                }
            }
        }
        if (savedOffhand != null && savedOffhand.getType() != Material.AIR) {
            e.getDrops().add(savedOffhand.clone());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && SpellHotbarManager.isActive(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onHotbarArmorStand(PlayerArmorStandManipulateEvent e) {
        if (SpellHotbarManager.isActive(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    // ============================
    // MANA DISPLAY HELPERS
    // ============================

    private static boolean isManaDisplayItem(ItemStack item) {
        return WandUtils.isWand(item) || WandUtils.isGrimoire(item);
    }

    public static void resetExperience(Player p){
        level.remove(p.getUniqueId().toString());
        exp.remove(p.getUniqueId().toString());
    }

    public static void switchTo(Player p, ItemStack wand) {
        MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
        Alkatraz.getNms().fakeExp(p, (float) (data.getMana() / data.getMaxMana()), (int) data.getMana(), 1);
    }

    public static void switchFrom(Player p) {
        Alkatraz.getNms().fakeExp(p, p.getExp(), p.getLevel(), p.getTotalExperience());
        SpellHotbarManager.exit(p);
    }
}
