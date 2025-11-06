package me.nagasonic.alkatraz.gui;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.implementation.MagicMissile;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class SpellsGUI implements Listener {
    private static Map<Integer, List<Spell>> guiPages = new HashMap<>();

    public static void openGUI(Player player, OfflinePlayer target){
        Collection<Spell> spells = SpellRegistry.getAllSpells().values();
        int i = 1;
        int p = 1;
        int pageNumbers = (int) Math.ceil((double) spells.size() / 36) + 2;
        List<Spell> pageSpells = new ArrayList<>();
        for (Spell spell : spells){
            if (i < 36){
                pageSpells.add(spell);
                i++;
            }else{
                guiPages.put(p, pageSpells);
                i = 1;
                p++;
                pageSpells.clear();
            }
        }
        guiPages.put(p, pageSpells);
        createGui(1, player, target, pageNumbers);
    }

    private static void createGui(int page, Player p, OfflinePlayer target, int totalPages){
        PlayerData data;
        if (target.isOnline()){
            data = DataManager.getPlayerData(target);
        }else{ data = DataManager.getConfigData(target); }
        Inventory gui = Bukkit.createInventory(null, 54, "Spells");
        for (int i = 0; i < 9; i++){
            gui.setItem(i, Utils.getBlank());
        }
        for (int i = 45; i <54; i++){
            gui.setItem(i, Utils.getBlank());
        }
        if (page < totalPages){
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.setDisplayName(format("&fNext Page"));
            List<String> lore = new ArrayList<>();
            lore.add(format("&ePage " + (page + 1)));
            meta.setLore(lore);
            next.setItemMeta(meta);
            NBT.modify(next, nbt -> {
                nbt.setInteger("page", page + 1);
                nbt.setString("player", p.getUniqueId().toString());
                nbt.setString("target", target.getUniqueId().toString());
                nbt.setInteger("total_pages", totalPages);
            });
            gui.setItem(53, next);
        }
        if (page > 1){
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.setDisplayName(format("&fPrevious Page"));
            List<String> lore = new ArrayList<>();
            lore.add(format("&ePage " + (page - 1)));
            meta.setLore(lore);
            prev.setItemMeta(meta);
            NBT.modify(prev, nbt -> {
                nbt.setInteger("page", page - 1);
                nbt.setString("player", p.getUniqueId().toString());
                nbt.setString("target", target.getUniqueId().toString());
                nbt.setInteger("total_pages", totalPages);
            });
            gui.setItem(45, prev);
        }
        List<Spell> spells = guiPages.get(page);
        int s = 0;
        for (int i = 9; i < 45; i++){
            if (s < spells.size()){
                Spell spell = spells.get(s);
                ItemStack item;
                if (data.hasDiscovered(spell) || target.getPlayer().hasPermission("alkatraz.allspells")){
                    item = new ItemStack(spell.getGuiItem());
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName(format(spell.getDisplayName()));
                    List<String> lore = new ArrayList<>();
                    for (String st : spell.getDescription()){
                        lore.add(format(st));
                    }
                    lore.add("");
                    lore.add(format("&bCode: " + spell.getCode()));
                    lore.add(format("&bMana Cost: " + spell.getCost()));
                    lore.add(format("&bCast Time: " + spell.getCastTime() + "s"));
                    lore.add(format("&bElement: " + spell.getElement().getName()));
                    lore.add(format("&bMastery: " + data.getSpellMastery(spell) + "/" + spell.getMaxMastery()));
                    lore.add("");
                    lore.add(format("&eCircle: " + spell.getLevel()));
                    meta.setLore(lore);
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    item.setItemMeta(meta);
                }else{
                    item = new ItemStack(Material.GRAY_DYE);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName(format("&8???"));
                    List<String> lore = new ArrayList<>();
                    lore.add(format("&7&oCircle: " + spell.getLevel()));
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                gui.setItem(i, item);
                s++;
            }
        }
        p.openInventory(gui);
    }

    @EventHandler
    private void onInventoryClick(InventoryClickEvent e){
        if (e.getView().getTitle().equals("Spells")){
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item != null) {
                if (item.getType() != Material.AIR && item.getAmount() > 0){
                    ItemMeta meta = item.getItemMeta();
                    if (meta.getDisplayName().equals(format("&fNext Page"))){
                        int newPage = NBT.get(item, nbt -> (Integer) nbt.getInteger("page"));
                        Player p = Bukkit.getPlayer(UUID.fromString(NBT.get(item, nbt -> (String) nbt.getString("player"))));
                        OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(NBT.get(item, nbt -> (String) nbt.getString("target"))));
                        int totalPages = NBT.get(item, nbt -> (Integer) nbt.getInteger("total_pages"));
                        createGui(newPage, p, target, totalPages);
                    }else if (meta.getDisplayName().equals(format("&fPrevious Page"))){
                        int newPage = NBT.get(item, nbt -> (Integer) nbt.getInteger("page"));
                        Player p = Bukkit.getPlayer(UUID.fromString(NBT.get(item, nbt -> (String) nbt.getString("player"))));
                        OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(NBT.get(item, nbt -> (String) nbt.getString("target"))));
                        int totalPages = NBT.get(item, nbt -> (Integer) nbt.getInteger("total_pages"));
                        createGui(newPage, p, target, totalPages);
                    }
                }
            }
        }
    }
}