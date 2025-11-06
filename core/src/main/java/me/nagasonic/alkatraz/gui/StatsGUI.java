package me.nagasonic.alkatraz.gui;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.config.Configs;
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
import java.util.Objects;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class StatsGUI implements Listener {
    private final static int AFFINITY_INCREASE = (Integer) Configs.AFFINITY_PER_POINT.get();
    private final static int RESISTANCE_INCREASE = (Integer) Configs.RESISTANCE_PER_POINT.get();

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
        gui.setItem(1, getLightStats(data));
        gui.setItem(2, getEarthStats(data));
        gui.setItem(3, getWaterStats(data));
        gui.setItem(4, getPlayerStats(target.getPlayer()));
        gui.setItem(5, getFireStats(data));
        gui.setItem(6, getAirStats(data));
        gui.setItem(7, getDarkStats(data));
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
        lore.add(format("&cTHIS IS NOT UNDOABLE"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setBoolean("reset_stats", true);
        });
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
            lore.add(format("&7 - #ff8c00+" + (AFFINITY_INCREASE * data.getFireStatPoints()) + " Fire Affinity"));
            lore.add(format("&7 - #ff8c00+" + (RESISTANCE_INCREASE * data.getFireStatPoints()) + " Fire Resistance"));
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
            lore.add(format("&7 - &9+" + (AFFINITY_INCREASE * data.getWaterStatPoints()) + " Water Affinity"));
            lore.add(format("&7 - &9+" + (RESISTANCE_INCREASE * data.getWaterStatPoints()) + " Water Resistance"));
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
            lore.add(format("&7 - #A0522D+" + (AFFINITY_INCREASE * data.getEarthStatPoints()) + " Earth Affinity"));
            lore.add(format("&7 - #A0522D+" + (RESISTANCE_INCREASE * data.getEarthStatPoints()) + " Earth Resistance"));
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
            lore.add(format("&7 - &f+" + (AFFINITY_INCREASE * data.getAirStatPoints()) + " Air Affinity"));
            lore.add(format("&7 - &f+" + (RESISTANCE_INCREASE * data.getAirStatPoints()) + " Air Resistance"));
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
            lore.add(format("&7 - #ffff87+" + (AFFINITY_INCREASE * data.getLightStatPoints()) + " Light Affinity"));
            lore.add(format("&7 - #ffff87+" + (RESISTANCE_INCREASE * data.getLightStatPoints()) + " Light Resistance"));
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
            lore.add(format("&7 - &8+" + (AFFINITY_INCREASE * data.getDarkStatPoints()) + " Dark Affinity"));
            lore.add(format("&7 - &8+" + (RESISTANCE_INCREASE * data.getDarkStatPoints()) + " Dark Resistance"));
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

    private static ItemStack getPlayerStats(Player player){
        ItemStack item = ItemUtils.headFromUuid(player.getUniqueId().toString());
        PlayerData data = DataManager.getPlayerData(player);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format("&f" + player.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&6Stat Points: &e" + data.getStatPoints()));
        lore.add(format("&6Reset Tokens: &e" + data.getStatResetTokens()));
        lore.add("");
        lore.add(format("&2Magic Affinity: &b" + data.getMagicAffinity()));
        lore.add(format("&2Magic Resistance: &b" + data.getMagicResistance()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getFireStats(PlayerData data){
        ItemStack item = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.FIRE.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format(Element.FIRE.getColor() + "Affinity: " + data.getFireAffinity()));
        lore.add(format(Element.FIRE.getColor() + "Resistance: " + data.getFireResistance()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getWaterStats(PlayerData data){
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.WATER.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format(Element.WATER.getColor() + "Affinity: " + data.getWaterAffinity()));
        lore.add(format(Element.WATER.getColor() + "Resistance: " + data.getWaterResistance()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getEarthStats(PlayerData data){
        ItemStack item = new ItemStack(Material.DIRT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.EARTH.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format(Element.EARTH.getColor() + "Affinity: " + data.getEarthAffinity()));
        lore.add(format(Element.EARTH.getColor() + "Resistance: " + data.getEarthResistance()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getAirStats(PlayerData data){
        ItemStack item = ItemUtils.headFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWIyNGQ1NzhkYWYxZTg2MjRiNjJjZDY0Nzg2NDUyMmEyNmJmY2RjMDJiYWMxMTAyZjljMWQ5ZDgyZDdiMjVkMiJ9fX0");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.AIR.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format(Element.AIR.getColor() + "Affinity: " + data.getAirAffinity()));
        lore.add(format(Element.AIR.getColor() + "Resistance: " + data.getAirResistance()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getLightStats(PlayerData data){
        ItemStack item = ItemUtils.headFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTEzMzIzZjIwZTY0MjFlZjFjMWRjNGU2ZjcwYTdhOGEzODRlMWZjYTUyMjA5ZDY2ZTU1YTliNjg1MmYzMmExZCJ9fX0");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.LIGHT.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format(Element.LIGHT.getColor() + "Affinity: " + data.getLightAffinity()));
        lore.add(format(Element.LIGHT.getColor() + "Resistance: " + data.getLightResistance()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getDarkStats(PlayerData data){
        ItemStack item = ItemUtils.headFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTUzNzgyNjdiNzJhMzM2MThjOGM5ZDhmZjRiZTJkNDUyYTI2NTA5YTk5NjRiMDgwYjE5ZDdjMzA4ZWM3OTYwNSJ9fX0");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.DARK.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format(Element.DARK.getColor() + "Affinity: " + data.getDarkAffinity()));
        lore.add(format(Element.DARK.getColor() + "Resistance: " + data.getDarkResistance()));
        meta.setLore(lore);
        item.setItemMeta(meta);
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
                    if (!Objects.equals(NBT.get(item, nbt -> (String) nbt.getString("stat")), "")){
                        Element element = Element.valueOf(NBT.get(item, nbt -> (String) nbt.getString("stat")).toUpperCase());
                        if (data.getStatPoints() > 0){
                            if (element.equals(Element.FIRE)){
                                data.setStatPoints(data.getStatPoints() - 1);
                                data.setFireStatPoints(data.getFireStatPoints() + 1);
                                data.setFireAffinity(data.getFireAffinity() + AFFINITY_INCREASE);
                                data.setFireResistance(data.getFireResistance() + RESISTANCE_INCREASE);
                                updateGUI(p, target);
                            } else if (element.equals(Element.WATER)) {
                                data.setStatPoints(data.getStatPoints() - 1);
                                data.setWaterStatPoints(data.getWaterStatPoints() + 1);
                                data.setWaterAffinity(data.getWaterAffinity() + AFFINITY_INCREASE);
                                data.setWaterResistance(data.getWaterResistance() + RESISTANCE_INCREASE);
                                updateGUI(p, target);
                            } else if (element.equals(Element.EARTH)) {
                                data.setStatPoints(data.getStatPoints() - 1);
                                data.setEarthStatPoints(data.getEarthStatPoints() + 1);
                                data.setEarthAffinity(data.getEarthAffinity() + AFFINITY_INCREASE);
                                data.setEarthResistance(data.getEarthResistance() + RESISTANCE_INCREASE);
                                updateGUI(p, target);
                            } else if (element.equals(Element.AIR)) {
                                data.setStatPoints(data.getStatPoints() - 1);
                                data.setAirStatPoints(data.getAirStatPoints() + 1);
                                data.setAirAffinity(data.getAirAffinity() + AFFINITY_INCREASE);
                                data.setAirResistance(data.getAirResistance() + RESISTANCE_INCREASE);
                                updateGUI(p, target);
                            } else if (element.equals(Element.LIGHT)) {
                                data.setStatPoints(data.getStatPoints() - 1);
                                data.setLightStatPoints(data.getLightStatPoints() + 1);
                                data.setLightAffinity(data.getLightAffinity() + AFFINITY_INCREASE);
                                data.setLightResistance(data.getLightResistance() + RESISTANCE_INCREASE);
                                updateGUI(p, target);
                            } else if (element.equals(Element.DARK)) {
                                data.setStatPoints(data.getStatPoints() - 1);
                                data.setDarkStatPoints(data.getDarkStatPoints() + 1);
                                data.setDarkAffinity(data.getDarkAffinity() + AFFINITY_INCREASE);
                                data.setDarkResistance(data.getDarkResistance() + RESISTANCE_INCREASE);
                                updateGUI(p, target);
                            }
                        }
                    } else if (NBT.get(item, nbt -> (Boolean) nbt.getBoolean("reset_stats"))) {
                        if (data.getStatResetTokens() > 0){
                            int totalPoints = 0;
                            totalPoints += data.getFireStatPoints() + data.getDarkStatPoints() + data.getWaterStatPoints() + data.getEarthStatPoints() + data.getAirStatPoints() + data.getAirStatPoints();
                            if (totalPoints > 0){
                                data.setStatResetTokens(data.getStatResetTokens() - 1);
                                data.setFireStatPoints(0);
                                data.setWaterStatPoints(0);
                                data.setEarthStatPoints(0);
                                data.setAirStatPoints(0);
                                data.setLightStatPoints(0);
                                data.setDarkStatPoints(0);
                                data.setStatPoints(data.getStatPoints() + totalPoints);
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
            gui.setItem(1, getLightStats(data));
            gui.setItem(2, getEarthStats(data));
            gui.setItem(3, getWaterStats(data));
            gui.setItem(4, getPlayerStats(target.getPlayer()));
            gui.setItem(5, getFireStats(data));
            gui.setItem(6, getAirStats(data));
            gui.setItem(7, getDarkStats(data));
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
            NBT.modify(item, nbt -> {
                nbt.setBoolean("reset_stats", true);
            });
            gui.setItem(31, item);
        }
    }
}
