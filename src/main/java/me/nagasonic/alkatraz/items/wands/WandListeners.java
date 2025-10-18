package me.nagasonic.alkatraz.items.wands;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class WandListeners implements Listener {
    private static final Map<String, Integer> level = new HashMap<>();
    private static final Map<String, Float> exp = new HashMap<>();
    private static final Map<String, Integer> changedExp = new HashMap<>();

    @EventHandler
    private void onClick(PlayerInteractEvent e){
        if (e.getItem() != null) {
            if (e.getItem().getType() != Material.AIR && e.getItem().getAmount() != 0) {
                if (Wand.isWand(e.getItem())) {
                    if (!DataManager.getPlayerData(e.getPlayer()).isCasting()){
                        String code = NBT.get(e.getItem(), nbt -> (String) nbt.getString("cast_code"));
                        if (e.getAction().isRightClick()) {
                            NBT.modify(e.getItem(), nbt -> {
                                nbt.setString("cast_code", code + "R");
                            });
                        } else if (e.getAction().isLeftClick()) {
                            NBT.modify(e.getItem(), nbt -> {
                                nbt.setString("cast_code", code + "L");
                            });
                        }
                        String code2 = NBT.get(e.getItem(), nbt -> (String) nbt.getString("cast_code"));
                        String message = code2.replace("R", "◆");
                        message = message.replace("L", "◈");
                        message = message.replace("S", "❖");
                        Utils.sendActionBar(e.getPlayer(), message);
                        if (code2.length() >= 5){
                            Spell spell = SpellRegistry.getSpell(code2);
                            if (spell != null) {
                                if (DataManager.getPlayerData(e.getPlayer()).hasDiscovered(spell) || e.getPlayer().hasPermission("alkatraz.allspells")){
                                    spell.cast(e.getPlayer(), e.getItem());
                                }
                            }
                            NBT.modify(e.getItem(), nbt -> {
                                nbt.setString("cast_code", "");
                            });
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            ItemStack wand = p.getItemInHand();
            if (wand.getType() != Material.AIR && wand.getAmount() != 0) {
                if (Wand.isWand(wand)) {
                    if (!DataManager.getPlayerData(p).isCasting()){
                        String code = NBT.get(wand, nbt -> (String) nbt.getString("cast_code"));
                        NBT.modify(wand, nbt -> {
                            nbt.setString("cast_code", code + "L");
                        });
                        String code2 = NBT.get(wand, nbt -> (String) nbt.getString("cast_code"));
                        String message = code2.replace("R", "◆");
                        message = message.replace("L", "◈");
                        message = message.replace("S", "❖");
                        Utils.sendActionBar(p, message);
                        if (code2.length() >= 5){
                            Alkatraz.logFine(code2);
                            Spell spell = SpellRegistry.getSpell(code2);
                            if (spell != null) {
                                if (DataManager.getPlayerData(p).hasDiscovered(spell) || p.hasPermission("alkatraz.allspells")){
                                    spell.cast(p, wand);
                                }
                            }
                            NBT.modify(wand, nbt -> {
                                nbt.setString("cast_code", "");
                            });
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onClickEntity(PlayerInteractEntityEvent e) {
        ItemStack wand = e.getPlayer().getInventory().getItem(e.getHand());
        Player p = e.getPlayer();
        if (wand.getType() != Material.AIR && wand.getAmount() != 0){
            if (Wand.isWand(wand)) {
                e.setCancelled(true);
                if (!DataManager.getPlayerData(p).isCasting()){
                    String code = NBT.get(wand, nbt -> (String) nbt.getString("cast_code"));
                    NBT.modify(wand, nbt -> {
                        nbt.setString("cast_code", code + "R");
                    });
                    String code2 = NBT.get(wand, nbt -> (String) nbt.getString("cast_code"));
                    String message = code2.replace("R", "◆");
                    message = message.replace("L", "◈");
                    message = message.replace("S", "❖");
                    Utils.sendActionBar(p, message);
                    if (code2.length() >= 5){
                        Spell spell = SpellRegistry.getSpell(code2);
                        if (spell != null) {
                            if (DataManager.getPlayerData(p).hasDiscovered(spell) || p.hasPermission("alkatraz.allspells")){
                                spell.cast(p, wand);
                            }
                        }
                        NBT.modify(wand, nbt -> {
                            nbt.setString("cast_code", "");
                        });
                    }
                }
            }
        }
    }

    @EventHandler
    private void onSwap(PlayerSwapHandItemsEvent e){
        ItemStack wand = e.getOffHandItem();
        Player p = e.getPlayer();
        if (wand != null){
            if (wand.getType() != Material.AIR && wand.getAmount() != 0){
                if (Wand.isWand(wand)) {
                    e.setCancelled(true);
                    if (!DataManager.getPlayerData(p).isCasting()){
                        String code = NBT.get(wand, nbt -> (String) nbt.getString("cast_code"));
                        NBT.modify(wand, nbt -> {
                            nbt.setString("cast_code", code + "S");
                        });
                        String code2 = NBT.get(wand, nbt -> (String) nbt.getString("cast_code"));
                        String message = code2.replace("R", "◆");
                        message = message.replace("L", "◈");
                        message = message.replace("S", "❖");
                        Utils.sendActionBar(p, message);
                        if (code2.length() >= 5){
                            Spell spell = SpellRegistry.getSpell(code2);
                            if (spell != null) {
                                if (DataManager.getPlayerData(p).hasDiscovered(spell) || p.hasPermission("alkatraz.allspells")){
                                    spell.cast(p, wand);

                                }
                            }
                            NBT.modify(wand, nbt -> {
                                nbt.setString("cast_code", "");
                            });
                        }
                        p.setItemInHand(wand);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onClickAtEntity(PlayerInteractAtEntityEvent e) {
        ItemStack wand = e.getPlayer().getInventory().getItem(e.getHand());
        if (wand.getType() != Material.AIR && wand.getAmount() != 0){
            if (Wand.isWand(wand)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    private void onHandSwitch(PlayerItemHeldEvent e) {
        ItemStack wand = e.getPlayer().getInventory().getItem(e.getPreviousSlot());
        if (wand != null){
            if (wand.getType() != Material.AIR && wand.getAmount() != 0) {
                if (Wand.isWand(wand)) {
                    NBT.modify(wand, nbt -> {
                        nbt.setString("cast_code", "");
                    });
                }
            }
        }
    }

    //Wand Mana Display Events
    @EventHandler
    private void onWand(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        ItemStack prev = p.getInventory().getItem(e.getPreviousSlot());
        ItemStack curr = p.getInventory().getItem(e.getNewSlot());
        if (prev != null) {
            if (prev.getType() != Material.AIR && prev.getAmount() != 0){
                if (Wand.isWand(prev)) {
                    switchFrom(p);
                }
            }
        }
        if (curr != null){
            if (curr.getType() != Material.AIR && curr.getAmount() != 0){
                if (Wand.isWand(curr)) {
                    switchTo(p);
                }
            }
        }
    }

    @EventHandler
    private void onInventoryClick(InventoryClickEvent e) throws InterruptedException {
        if (e.getClickedInventory() == e.getWhoClicked().getInventory()){
            Player p = (Player) e.getWhoClicked();
            Alkatraz.logFine(e.getAction().toString());
            if (e.getAction() == InventoryAction.SWAP_WITH_CURSOR){
                if (e.getSlot() == p.getInventory().getHeldItemSlot()){
                    ItemStack cursor = e.getCursor();
                    if (cursor != null){
                        if (cursor.getType() != Material.AIR && cursor.getAmount() != 0){
                            if (Wand.isWand(cursor)) {
                                switchTo(p);
                            }
                        }
                    }

                    ItemStack curr = e.getCurrentItem();
                    if (curr != null){
                        if (curr.getType() != Material.AIR && curr.getAmount() != 0){
                            if (Wand.isWand(curr)) {
                                switchFrom(p);
                            }
                        }
                    }
                }
            }else if (e.getAction() == InventoryAction.HOTBAR_SWAP){
                if (e.getSlot() == p.getInventory().getHeldItemSlot()){
                    ItemStack swapped = e.getCurrentItem();
                    ItemStack swappedWith = p.getInventory().getItem(e.getHotbarButton());
                    if (swapped != null){
                        if (swapped.getType() != Material.AIR && swapped.getAmount() != 0){
                            if (Wand.isWand(swapped)){
                                if (swappedWith == null){
                                    switchFrom(p);
                                }else{
                                    if (!Wand.isWand(swappedWith)){
                                        switchFrom(p);
                                    }
                                }
                            }else{
                                if (swappedWith != null){
                                    if (Wand.isWand(swappedWith)){
                                        switchTo(p);
                                    }
                                }
                            }
                        }else{
                            if (swappedWith != null){
                                if (Wand.isWand(swappedWith)){
                                    switchTo(p);
                                }
                            }
                        }
                    }else{
                        if (swappedWith != null){
                            if (Wand.isWand(swappedWith)){
                                switchTo(p);
                            }
                        }
                    }
                }
            }else if (e.getAction() == InventoryAction.PICKUP_ALL || e.getAction().equals(InventoryAction.PICKUP_HALF) || e.getAction().equals(InventoryAction.PICKUP_ONE) || e.getAction().equals(InventoryAction.PICKUP_SOME)){
                ItemStack curr = e.getCurrentItem();
                if (curr != null){
                    if (curr.getType() != Material.AIR && curr.getAmount() != 0){
                        if (Wand.isWand(curr)) {
                            if (e.getSlot() == p.getInventory().getHeldItemSlot()){
                                switchFrom(p);
                            }
                        }
                    }
                }
            }else if (e.getAction() == InventoryAction.PLACE_ALL || e.getAction().equals(InventoryAction.PLACE_ONE) || e.getAction().equals(InventoryAction.PLACE_SOME)){
                ItemStack cursor = e.getCursor();
                if (cursor != null){
                    if (cursor.getType() != Material.AIR && cursor.getAmount() != 0){
                        if (Wand.isWand(cursor)) {
                            if (e.getSlot() == p.getInventory().getHeldItemSlot()){
                                switchTo(p);
                            }
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
                                            if (Wand.isWand(wand)) {
                                                switchTo(p);
                                            }
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
                            if (Wand.isWand(curr)) {
                                switchFrom(p);
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onDrop(PlayerDropItemEvent e){
        Player p = e.getPlayer();
        ItemStack dropped = e.getItemDrop().getItemStack();
        ItemStack hand = p.getItemInHand();
        if (dropped.getType() != Material.AIR && dropped.getAmount() != 0){
            if (hand.getAmount() != 0 && hand.getType() != Material.AIR) {
                if (!Wand.isWand(hand)){
                    switchFrom(p);
                }
            } else if (Wand.isWand(dropped)){
                switchFrom(p);
            }
        }
    }

    @EventHandler
    private void onPickup(PlayerPickupItemEvent e){
        ItemStack item = e.getItem().getItemStack();
        Player p = e.getPlayer();
        if (item.getType() != Material.AIR && item.getAmount() != 0){
            if (Wand.isWand(item)){
                for (int i = 0; i <= p.getInventory().getHeldItemSlot(); i++){
                    Alkatraz.logFine("" + i);
                    ItemStack s = p.getInventory().getItem(i);
                    if (s != null){
                        if (s.getAmount() == 0 || s.getType() == Material.AIR){
                            switchTo(p);
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
        Alkatraz.logFine(String.valueOf(changedExp.get(p.getUniqueId().toString())));
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        ItemStack wand = p.getInventory().getItem(p.getInventory().getHeldItemSlot());
        if (wand != null){
            if (wand.getType() != Material.AIR && wand.getAmount() != 0){
                if (Wand.isWand(wand)){
                    switchFrom(p);
                }
            }
        }
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        ItemStack wand = p.getInventory().getItem(p.getInventory().getHeldItemSlot());
        if (wand != null){
            if (wand.getType() != Material.AIR && wand.getAmount() != 0){
                if (Wand.isWand(wand)){
                    Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                        switchTo(p);
                    }, 1L);
                }
            }
        }
    }

    public static void resetExperience(Player p){
        level.remove(p.getUniqueId().toString());
        exp.remove(p.getUniqueId().toString());
    }

    public static void switchTo(Player p) {
        PlayerData data = DataManager.getPlayerData(p);
        if (!level.containsKey(p.getUniqueId().toString())){
            level.put(p.getUniqueId().toString(), p.getLevel());
        }else level.replace(p.getUniqueId().toString(), p.getLevel());
        if (!exp.containsKey(p.getUniqueId().toString())){
            exp.put(p.getUniqueId().toString(), p.getExp());
        }else exp.replace(p.getUniqueId().toString(), p.getExp());
        p.setLevel((int) data.getMana());
        p.setExp(Float.parseFloat(String.valueOf((data.getMana() / data.getMaxMana())-0.01)));
    }

    public static void switchFrom(Player p){
        if (level.containsKey(p.getUniqueId().toString())){
            p.setLevel(level.get(p.getUniqueId().toString()));
        }
        if (exp.containsKey(p.getUniqueId().toString())){
            p.setExp(exp.get(p.getUniqueId().toString()));
        }
        if (changedExp.containsKey(p.getUniqueId().toString())){
            p.giveExp(changedExp.get(p.getUniqueId().toString()));
            changedExp.remove(p.getUniqueId().toString());
        }
    }
}
