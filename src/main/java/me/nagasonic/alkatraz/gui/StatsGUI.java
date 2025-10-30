package me.nagasonic.alkatraz.gui;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.util.ItemUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class StatsGUI implements Listener {

    public static void createGUI(Player p, OfflinePlayer target){
        PlayerData data;
        if (target.isOnline()){
            data = DataManager.getPlayerData(target);
        }else{ data = DataManager.getConfigData(target); }
        Inventory gui = Bukkit.createInventory(null, 36, target.getName() + " Stats");
        for (int i = 0; i < 9; i++){
            gui.setItem(i, Utils.getBlank());
        }
        for (int i = 27; i < 36; i++){
            gui.setItem(i, Utils.getBlank());
        }
        gui.setItem(12, getFire(data));
        gui.setItem(13, getWater(data));
        gui.setItem(14, getEarth(data));
        gui.setItem(21, getAir(data));
        gui.setItem(22, getLight(data));
        gui.setItem(23, getDark(data));
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format("&dReset Stats"));
        List<String> lore = new ArrayList<>();
        lore.add(format("&dReset Tokens: &f" + data.getStatResetTokens()));
        lore.add("");
        lore.add("&eClick to reset stats.");
        lore.add("&cTHIS IS NOT UNDOABLE");
        meta.setLore(lore);
        item.setItemMeta(meta);
        gui.setItem(31, item);
        p.openInventory(gui);
    }

    private static ItemStack getFire(PlayerData data){
        ItemStack item = new ItemStack(Material.MAGMA_CREAM);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.FIRE.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&eInvested Points: &6" + data.getFireStatPoints()));
        lore.add("");
        if (data.getFireStatPoints() > 0){
            lore.add(format("&eBonus:"));
            lore.add(format("&7 - #ff8c00+" + (10 * data.getFireStatPoints()) + " Fire Affinity"));
            lore.add(format("&7 - #ff8c00+" + (5 * data.getFireStatPoints()) + " Fire Resistance"));
            lore.add("");
        }
        lore.add(format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("stat", "fire");
        });
        int amount = data.getFireStatPoints() > 0 ? data.getFireStatPoints() : 1;
        item.setAmount(amount);
        return item;
    }

    private static ItemStack getWater(PlayerData data){
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.WATER.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&eInvested Points: &6" + data.getWaterStatPoints()));
        lore.add("");
        if (data.getWaterStatPoints() > 0){
            lore.add(format("&eBonus:"));
            lore.add(format("&7 - &9+" + (10 * data.getWaterStatPoints()) + " Water Affinity"));
            lore.add(format("&7 - &9+" + (5 * data.getWaterStatPoints()) + " Water Resistance"));
            lore.add("");
        }
        lore.add(format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("stat", "water");
        });
        int amount = data.getWaterStatPoints() > 0 ? data.getWaterStatPoints() : 1;
        item.setAmount(amount);
        return item;
    }

    private static ItemStack getEarth(PlayerData data){
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.EARTH.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&eInvested Points: &6" + data.getEarthStatPoints()));
        lore.add("");
        if (data.getEarthStatPoints() > 0){
            lore.add(format("&eBonus:"));
            lore.add(format("&7 - #A0522D+" + (10 * data.getEarthStatPoints()) + " Earth Affinity"));
            lore.add(format("&7 - #A0522D+" + (5 * data.getEarthStatPoints()) + " Earth Resistance"));
            lore.add("");
        }
        lore.add(format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("stat", "earth");
        });
        int amount = data.getEarthStatPoints() > 0 ? data.getEarthStatPoints() : 1;
        item.setAmount(amount);
        return item;
    }

    private static ItemStack getAir(PlayerData data){
        ItemStack item = ItemUtils.headFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWIyNGQ1NzhkYWYxZTg2MjRiNjJjZDY0Nzg2NDUyMmEyNmJmY2RjMDJiYWMxMTAyZjljMWQ5ZDgyZDdiMjVkMiJ9fX0");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.AIR.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&eInvested Points: &6" + data.getAirStatPoints()));
        lore.add("");
        if (data.getAirStatPoints() > 0){
            lore.add(format("&eBonus:"));
            lore.add(format("&7 - &f+" + (10 * data.getAirStatPoints()) + " Air Affinity"));
            lore.add(format("&7 - &f+" + (5 * data.getAirStatPoints()) + " Air Resistance"));
            lore.add("");
        }
        lore.add(format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("stat", "air");
        });
        int amount = data.getAirStatPoints() > 0 ? data.getAirStatPoints() : 1;
        item.setAmount(amount);
        return item;
    }

    private static ItemStack getLight(PlayerData data){
        ItemStack item = ItemUtils.headFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTEzMzIzZjIwZTY0MjFlZjFjMWRjNGU2ZjcwYTdhOGEzODRlMWZjYTUyMjA5ZDY2ZTU1YTliNjg1MmYzMmExZCJ9fX0");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.LIGHT.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&eInvested Points: &6" + data.getLightStatPoints()));
        lore.add("");
        if (data.getLightStatPoints() > 0){
            lore.add(format("&eBonus:"));
            lore.add(format("&7 - #ffff87+" + (10 * data.getLightStatPoints()) + " Light Affinity"));
            lore.add(format("&7 - #ffff87+" + (5 * data.getLightStatPoints()) + " Light Resistance"));
            lore.add("");
        }
        lore.add(format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("stat", "light");
        });
        int amount = data.getLightStatPoints() > 0 ? data.getLightStatPoints() : 1;
        item.setAmount(amount);
        return item;
    }

    private static ItemStack getDark(PlayerData data){
        ItemStack item = ItemUtils.headFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTUzNzgyNjdiNzJhMzM2MThjOGM5ZDhmZjRiZTJkNDUyYTI2NTA5YTk5NjRiMDgwYjE5ZDdjMzA4ZWM3OTYwNSJ9fX0");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.DARK.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&eInvested Points: &6" + data.getDarkStatPoints()));
        lore.add("");
        if (data.getDarkStatPoints() > 0){
            lore.add(format("&eBonus:"));
            lore.add(format("&7 - &8+" + (10 * data.getDarkStatPoints()) + " Dark Affinity"));
            lore.add(format("&7 - &8+" + (5 * data.getDarkStatPoints()) + " Dark Resistance"));
            lore.add("");
        }
        lore.add(format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("stat", "dark");
        });
        int amount = data.getDarkStatPoints() > 0 ? data.getDarkStatPoints() : 1;
        item.setAmount(amount);
        return item;
    }

    @EventHandler
    private void onClick(InventoryClickEvent e){
        if (e.getView().getTitle().contains("Stats")){
            e.setCancelled(true);
            Player target = Bukkit.getPlayer(e.getView().getTitle().split(" ")[0]);
            ItemStack item = e.getCurrentItem();
            if (item != null){
                if (!item.getType().equals(Material.AIR) && item.getAmount() > 0){
                    Player p = (Player) e.getWhoClicked();
                    PlayerData data = DataManager.getPlayerData(p);
                    if (NBT.get(item, nbt -> (String) nbt.getString("stat")) != null){
                        Element element = Element.valueOf(NBT.get(item, nbt -> (String) nbt.getString("stat")).toUpperCase());
                        if (data.getStatPoints() > 0){
                            if (element.equals(Element.FIRE)){
                                data.setStatPoints(data.getStatPoints() - 1);
                                data.setFireStatPoints(data.getFireStatPoints() + 1);
                                data.setFireAffinity(data.getFireAffinity() + 10);
                                data.setFireResistance(data.getFireResistance() + 5);
                                updateGUI(p, target);
                            } else if (element.equals(Element.WATER)) {
                                data.setStatPoints(data.getStatPoints() - 1);
                                data.setWaterStatPoints(data.getWaterStatPoints() + 1);
                                data.setWaterAffinity(data.getWaterAffinity() + 10);
                                data.setWaterResistance(data.getWaterResistance() + 5);
                                updateGUI(p, target);
                            } else if (element.equals(Element.EARTH)) {
                                data.setStatPoints(data.getStatPoints() - 1);
                                data.setEarthStatPoints(data.getEarthStatPoints() + 1);
                                data.setEarthAffinity(data.getEarthAffinity() + 10);
                                data.setEarthResistance(data.getEarthResistance() + 5);
                                updateGUI(p, target);
                            } else if (element.equals(Element.AIR)) {
                                data.setStatPoints(data.getStatPoints() - 1);
                                data.setAirStatPoints(data.getAirStatPoints() + 1);
                                data.setAirAffinity(data.getAirAffinity() + 10);
                                data.setAirResistance(data.getAirResistance() + 5);
                                updateGUI(p, target);
                            } else if (element.equals(Element.LIGHT)) {
                                data.setStatPoints(data.getStatPoints() - 1);
                                data.setLightStatPoints(data.getLightStatPoints() + 1);
                                data.setLightAffinity(data.getLightAffinity() + 10);
                                data.setLightResistance(data.getLightResistance() + 5);
                                updateGUI(p, target);
                            } else if (element.equals(Element.DARK)) {
                                data.setStatPoints(data.getStatPoints() - 1);
                                data.setDarkStatPoints(data.getDarkStatPoints() + 1);
                                data.setDarkAffinity(data.getDarkAffinity() + 10);
                                data.setDarkResistance(data.getDarkResistance() + 5);
                                updateGUI(p, target);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void updateGUI(Player p, Player target){
        PlayerData data;
        if (target.isOnline()){
            data = DataManager.getPlayerData(target);
        }else{ data = DataManager.getConfigData(target); }
        if (p.getOpenInventory().getTitle().contains("Stats")){
            Inventory gui = p.getOpenInventory().getTopInventory();
            for (int i = 0; i < 9; i++){
                gui.setItem(i, Utils.getBlank());
            }
            for (int i = 27; i < 36; i++){
                gui.setItem(i, Utils.getBlank());
            }
            gui.setItem(12, getFire(data));
            gui.setItem(13, getWater(data));
            gui.setItem(14, getEarth(data));
            gui.setItem(21, getAir(data));
            gui.setItem(22, getLight(data));
            gui.setItem(23, getDark(data));
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(format("&dReset Stats"));
            List<String> lore = new ArrayList<>();
            lore.add(format("&dReset Tokens: &f" + data.getStatResetTokens()));
            lore.add("");
            lore.add(format("&eClick to reset stats."));
            lore.add(format("&c&lTHIS IS NOT UNDOABLE"));
            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.setItem(31, item);
        }
    }
}
