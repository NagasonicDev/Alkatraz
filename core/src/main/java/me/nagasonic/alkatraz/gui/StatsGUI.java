package me.nagasonic.alkatraz.gui;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.util.ItemUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.data.type.Fire;
import org.bukkit.block.data.type.Light;
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

    @SuppressWarnings("deprecation")
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
        lore.add(format("&dReset Tokens: &f" + data.getInt("reset_tokens")));
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
        lore.add(format("&eInvested Points: &6" + data.getPoints(Element.FIRE)));
        lore.add("");
        if (data.getPoints(Element.FIRE) > 0){
            lore.add(format("&eBonus:"));
            lore.add(format("&7 - #ff8c00+" + (AFFINITY_INCREASE * data.getPoints(Element.FIRE)) + " Fire Affinity"));
            lore.add(format("&7 - #ff8c00+" + (RESISTANCE_INCREASE * data.getPoints(Element.FIRE)) + " Fire Resistance"));
            lore.add("");
        }
        lore.add(format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("stat", "fire");
        });
        int amount = data.getPoints(Element.FIRE) > 0 ? data.getPoints(Element.FIRE) : 1;
        item.setAmount(amount);
        return item;
    }

    private static ItemStack getWater(PlayerData data){
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.WATER.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&eInvested Points: &6" + data.getPoints(Element.WATER)));
        lore.add("");
        if (data.getPoints(Element.WATER) > 0){
            lore.add(format("&eBonus:"));
            lore.add(format("&7 - &9+" + (AFFINITY_INCREASE * data.getPoints(Element.WATER)) + " Water Affinity"));
            lore.add(format("&7 - &9+" + (RESISTANCE_INCREASE * data.getPoints(Element.WATER)) + " Water Resistance"));
            lore.add("");
        }
        lore.add(format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("stat", "water");
        });
        int amount = data.getPoints(Element.WATER) > 0 ? data.getPoints(Element.WATER) : 1;
        item.setAmount(amount);
        return item;
    }

    private static ItemStack getEarth(PlayerData data){
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.EARTH.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&eInvested Points: &6" + data.getPoints(Element.EARTH)));
        lore.add("");
        if (data.getPoints(Element.EARTH) > 0){
            lore.add(format("&eBonus:"));
            lore.add(format("&7 - #A0522D+" + (AFFINITY_INCREASE * data.getPoints(Element.EARTH)) + " Earth Affinity"));
            lore.add(format("&7 - #A0522D+" + (RESISTANCE_INCREASE * data.getPoints(Element.EARTH)) + " Earth Resistance"));
            lore.add("");
        }
        lore.add(format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("stat", "earth");
        });
        int amount = data.getPoints(Element.EARTH) > 0 ? data.getPoints(Element.EARTH) : 1;
        item.setAmount(amount);
        return item;
    }

    private static ItemStack getAir(PlayerData data){
        ItemStack item = ItemUtils.headFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWIyNGQ1NzhkYWYxZTg2MjRiNjJjZDY0Nzg2NDUyMmEyNmJmY2RjMDJiYWMxMTAyZjljMWQ5ZDgyZDdiMjVkMiJ9fX0");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.AIR.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&eInvested Points: &6" + data.getPoints(Element.AIR)));
        lore.add("");
        if (data.getPoints(Element.AIR) > 0){
            lore.add(format("&eBonus:"));
            lore.add(format("&7 - &f+" + (AFFINITY_INCREASE * data.getPoints(Element.AIR)) + " Air Affinity"));
            lore.add(format("&7 - &f+" + (RESISTANCE_INCREASE * data.getPoints(Element.AIR)) + " Air Resistance"));
            lore.add("");
        }
        lore.add(format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("stat", "air");
        });
        int amount = data.getPoints(Element.AIR) > 0 ? data.getPoints(Element.AIR) : 1;
        item.setAmount(amount);
        return item;
    }

    private static ItemStack getLight(PlayerData data){
        ItemStack item = ItemUtils.headFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTEzMzIzZjIwZTY0MjFlZjFjMWRjNGU2ZjcwYTdhOGEzODRlMWZjYTUyMjA5ZDY2ZTU1YTliNjg1MmYzMmExZCJ9fX0");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.LIGHT.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&eInvested Points: &6" + data.getPoints(Element.LIGHT)));
        lore.add("");
        if (data.getPoints(Element.LIGHT) > 0){
            lore.add(format("&eBonus:"));
            lore.add(format("&7 - #ffff87+" + (AFFINITY_INCREASE * data.getPoints(Element.LIGHT)) + " Light Affinity"));
            lore.add(format("&7 - #ffff87+" + (RESISTANCE_INCREASE * data.getPoints(Element.LIGHT)) + " Light Resistance"));
            lore.add("");
        }
        lore.add(format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("stat", "light");
        });
        int amount = data.getPoints(Element.LIGHT) > 0 ? data.getPoints(Element.LIGHT) : 1;
        item.setAmount(amount);
        return item;
    }

    private static ItemStack getDark(PlayerData data){
        ItemStack item = ItemUtils.headFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTUzNzgyNjdiNzJhMzM2MThjOGM5ZDhmZjRiZTJkNDUyYTI2NTA5YTk5NjRiMDgwYjE5ZDdjMzA4ZWM3OTYwNSJ9fX0");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.DARK.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&eInvested Points: &6" + data.getPoints(Element.DARK)));
        lore.add("");
        if (data.getPoints(Element.DARK) > 0){
            lore.add(format("&eBonus:"));
            lore.add(format("&7 - &8+" + (AFFINITY_INCREASE * data.getPoints(Element.DARK)) + " Dark Affinity"));
            lore.add(format("&7 - &8+" + (RESISTANCE_INCREASE * data.getPoints(Element.DARK)) + " Dark Resistance"));
            lore.add("");
        }
        lore.add(format("&eClick to invest &61 &epoint."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("stat", "dark");
        });
        int amount = data.getPoints(Element.DARK) > 0 ? data.getPoints(Element.DARK) : 1;
        item.setAmount(amount);
        return item;
    }

    private static ItemStack getPlayerStats(Player player){
        ItemStack item = ItemUtils.headFromUuid(player.getUniqueId().toString());
        PlayerData data = DataManager.getPlayerData(player);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format("&f" + player.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format("&6Stat Points: &e" + data.getInt("stat_points")));
        lore.add(format("&6Reset Tokens: &e" + data.getInt("reset_tokens")));
        lore.add("");
        lore.add(format("&2Magic Affinity: &b" + data.getAffinity(Element.NONE)));
        lore.add(format("&2Magic Resistance: &b" + data.getResistance(Element.NONE)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getFireStats(PlayerData data){
        ItemStack item = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.FIRE.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format(Element.FIRE.getColor() + "Affinity: " + data.getAffinity(Element.FIRE)));
        lore.add(format(Element.FIRE.getColor() + "Resistance: " + data.getResistance(Element.FIRE)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getWaterStats(PlayerData data){
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.WATER.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format(Element.WATER.getColor() + "Affinity: " + data.getAffinity(Element.WATER)));
        lore.add(format(Element.WATER.getColor() + "Resistance: " + data.getResistance(Element.WATER)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getEarthStats(PlayerData data){
        ItemStack item = new ItemStack(Material.DIRT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.EARTH.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format(Element.EARTH.getColor() + "Affinity: " + data.getAffinity(Element.EARTH)));
        lore.add(format(Element.EARTH.getColor() + "Resistance: " + data.getResistance(Element.EARTH)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getAirStats(PlayerData data){
        ItemStack item = ItemUtils.headFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWIyNGQ1NzhkYWYxZTg2MjRiNjJjZDY0Nzg2NDUyMmEyNmJmY2RjMDJiYWMxMTAyZjljMWQ5ZDgyZDdiMjVkMiJ9fX0");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.AIR.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format(Element.AIR.getColor() + "Affinity: " + data.getAffinity(Element.AIR)));
        lore.add(format(Element.AIR.getColor() + "Resistance: " + data.getResistance(Element.AIR)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getLightStats(PlayerData data){
        ItemStack item = ItemUtils.headFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTEzMzIzZjIwZTY0MjFlZjFjMWRjNGU2ZjcwYTdhOGEzODRlMWZjYTUyMjA5ZDY2ZTU1YTliNjg1MmYzMmExZCJ9fX0");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.LIGHT.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format(Element.LIGHT.getColor() + "Affinity: " + data.getAffinity(Element.LIGHT)));
        lore.add(format(Element.LIGHT.getColor() + "Resistance: " + data.getResistance(Element.LIGHT)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getDarkStats(PlayerData data){
        ItemStack item = ItemUtils.headFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTUzNzgyNjdiNzJhMzM2MThjOGM5ZDhmZjRiZTJkNDUyYTI2NTA5YTk5NjRiMDgwYjE5ZDdjMzA4ZWM3OTYwNSJ9fX0");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(format(Element.DARK.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(format(Element.DARK.getColor() + "Affinity: " + data.getAffinity(Element.DARK)));
        lore.add(format(Element.DARK.getColor() + "Resistance: " + data.getResistance(Element.DARK)));
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
                        if (data.getInt("stat_points") > 0){
                            data.setInt("stat_points", data.getInt("stat_points") - 1);
                            data.setInt(element.getName().toLowerCase() + "_points", data.getPoints(element) + 1);
                            data.setDouble(element.getName().toLowerCase() + "_affinity", data.getAffinity(element) + AFFINITY_INCREASE);
                            data.setDouble(element.getName().toLowerCase() + "_resistance", data.getResistance(element) + RESISTANCE_INCREASE);
                            updateGUI(p, target);
                        }
                    } else if (NBT.get(item, nbt -> (Boolean) nbt.getBoolean("reset_stats"))) {
                        if (data.getInt("reset_tokens") > 0){
                            int totalPoints = 0;
                            for (Element t : Element.values()){
                                if (!t.equals(Element.NONE)){
                                    totalPoints += data.getPoints(t);
                                }
                            }
                            if (totalPoints > 0){
                                data.setInt("reset_tokens", data.getInt("reset_tokens") - 1);
                                data.setInt("fire_points", 0);
                                data.setInt("water_points", 0);
                                data.setInt("earth_points", 0);
                                data.setInt("air_points", 0);
                                data.setInt("light_points", 0);
                                data.setInt("dark_points", 0);
                                data.setInt("stat_points", data.getInt("stat_points") + totalPoints);
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
            lore.add(format("&dReset Tokens: &f" + data.getInt("reset_tokens")));
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
