package me.nagasonic.alkatraz.playerdata;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.implementation.MagicMissile;
import me.nagasonic.alkatraz.util.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class DataManager implements Listener {

    // TODO: REWORK STAT SYSTEM USING MAPS FOR EACH STATISTIC
    private static Map<String, PlayerData> playerData = new HashMap<>();

    public static PlayerData getPlayerData(OfflinePlayer p) {
        if (!playerData.containsKey(p.getUniqueId().toString())){
            PlayerData d = new PlayerData();
            playerData.put(p.getUniqueId().toString(), d);
            return d;
        }
        return playerData.get(p.getUniqueId().toString());
    }

    public static PlayerData getConfigData(OfflinePlayer p){
        PlayerData data = new PlayerData();
        File f = new File(getFolderPath(p) + "/general.yml");

        if (f.exists()){
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            List<String> statNames = StatManager.getStatNames();
            for (String stat : statNames){
                String type = StatManager.getType(stat);
                if (type.equals("double")){
                    data.setDouble(stat, cfg.getDouble("stats." + stat));
                }else if (type.equals("int")){
                    data.setInt(stat, cfg.getInt("stats." + stat));
                } else if (type.equals("boolean")) {
                    data.setBoolean(stat, cfg.getBoolean("stats." + stat));
                }
            }
            for (Spell spell : SpellRegistry.getAllSpells().values()){
                if (cfg.getStringList("discovered_spells").contains(spell.getType().toLowerCase())){
                    data.setDiscovered(spell, true);
                }
            }
        }else {
            List<String> statNames = StatManager.getStatNames();
            for (String stat : statNames){
                String type = StatManager.getType(stat);
                String def = StatManager.getDefault(stat);
                if (type.equals("double")){
                    data.setDouble(stat, Double.valueOf(def));
                } else if (type.equals("int")) {
                    data.setInt(stat, Integer.valueOf(def));
                } else if (type.equals("boolean")){
                    data.setBoolean(stat, Boolean.valueOf(def));
                }
            }
            data.setDiscovered(SpellRegistry.getSpell(MagicMissile.class), true);
        }
        File masteries = new File(getFolderPath(p) + "/mastery.yml");
        if (masteries.exists()) {
            FileConfiguration mcfg = YamlConfiguration.loadConfiguration(masteries);
            for (Spell spell : SpellRegistry.getAllSpells().values()) {
                if (mcfg.contains("spell_mastery." + spell.getType().toLowerCase())) {
                    data.setSpellMastery(spell, mcfg.getInt("spell_mastery." + spell.getType().toLowerCase()));
                }
            }
        }
        File stats = new File(getFolderPath(p) + "/stats.yml");
        if (stats.exists()){
            FileConfiguration scfg = YamlConfiguration.loadConfiguration(stats);
            List<String> pointNames = StatManager.getStatPoints();
            for (String name : pointNames){
                data.setInt(name, scfg.getInt(name));
            }
        }else{
            List<String> pointNames = StatManager.getStatPoints();
            for (String name : pointNames){
                data.setInt(name, 0);
            }
            // Overide Stat point and Reset Token values
            data.setInt("stat_points" , (Integer) Configs.DEFAULT_STAT_POINTS.get());
            data.setInt("reset_tokens", (Integer) Configs.DEFAULT_RESET_TOKENS.get());
        }
        return data;
    }

    public static void setPlayerData(Player p, PlayerData data) {
        playerData.put(p.getUniqueId().toString(), data);
    }


    public static String getFolderPath(OfflinePlayer p) {
        return Bukkit.getPluginsFolder().getAbsolutePath() + "/Alkatraz/playerdata/" + p.getUniqueId();
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent e) {
        PlayerData data = getConfigData(e.getPlayer());
        setPlayerData(e.getPlayer(), data);
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent e) {
        savePlayerData(e.getPlayer(), getPlayerData(e.getPlayer()));
        setPlayerData(e.getPlayer(), null);
    }

    public static void addMana(Player p, double amount) {
        PlayerData data = getPlayerData(p);
        data.setDouble("mana", data.getDouble("mana") + amount);
        if (data.getDouble("mana") > data.getDouble("max_mana")){
            data.setDouble("mana", data.getDouble("max_mana"));
        }
        ItemStack item = p.getItemInHand();
        if (item.getType() != Material.AIR && item.getAmount() != 0){
            if (Wand.isWand(item)){
                Alkatraz.getNms().fakeExp(p, (float) (data.getDouble("mana") / data.getDouble("max_mana")), data.getDouble("mana").intValue(), 1);
            }
        }
    }

    public static Long requiredExperience(int circle){
        long casts = Math.round(150.0 * Math.pow(Math.pow(800 / 150, 1.0 / (9 - 1)), circle - 1));
        long xpPerCast = Math.round(2.0 * Math.pow(1.9, circle - 1));
        long deltaXP = casts * xpPerCast;
        return deltaXP;
    }

    public static void subMana(Player p, double amount) {
        PlayerData data = getPlayerData(p);
        data.setDouble("mana", data.getDouble("mana") - amount);
        if (data.getDouble("mana") < 0) { data.setDouble("mana", (double) 0); }
        ItemStack item = p.getItemInHand();
        if (item.getType() != Material.AIR && item.getAmount() != 0){
            if (Wand.isWand(p.getItemInHand())){
                Alkatraz.getNms().fakeExp(p, (float) (data.getDouble("mana") / data.getDouble("max_mana")), data.getDouble("mana").intValue(), 1);
            }
        }
    }

    public static void addManaPerSecond(){
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()){
                PlayerData data = getPlayerData(p);
                if (data.getDouble("mana") < data.getDouble("max_mana")){
                    addMana(p, data.getDouble("mana_regeneration"));
                }
            }
        }, 0L, 20L);
    }

    public static void addSpellMastery(OfflinePlayer p, Spell spell, int mastery){
        PlayerData data = p.isOnline() ? DataManager.getPlayerData(p) : DataManager.getConfigData(p);
        if (data.getSpellMastery(spell) == -1 && mastery > 0){
            data.setSpellMastery(spell, 0);
        }
        if (data.getSpellMastery(spell) + mastery < 0){
            data.setSpellMastery(spell, 0);
        }else if (data.getSpellMastery(spell) + mastery > spell.getMaxMastery()){
            data.setSpellMastery(spell, spell.getMaxMastery());
        }else { data.setSpellMastery(spell, data.getSpellMastery(spell) + mastery); }
        if (p.isOnline()){
            Map<Spell, BossBar> masteryBars = data.getMasteryBars();
            if (masteryBars.containsKey(spell)){
                BossBar bar = masteryBars.get(spell);
                bar.removePlayer(p.getPlayer());
                bar.setTitle(format(spell.getDisplayName() + " Mastery: " + data.getSpellMastery(spell) + "/" + spell.getMaxMastery()));
                if (data.getSpellMastery(spell) / spell.getMaxMastery() > 1){
                    bar.setProgress(1);
                }else if (data.getSpellMastery(spell) / spell.getMaxMastery() < 0){
                    bar.setProgress(0);
                }else {bar.setProgress((double) data.getSpellMastery(spell) / spell.getMaxMastery()); }
                bar.addPlayer(p.getPlayer());
                masteryBars.replace(spell, bar);
                data.setMasteryBars(masteryBars);
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                    if (bar.getProgress() == data.getMasteryBars().get(spell).getProgress()){
                        bar.removePlayer(p.getPlayer());
                    }
                }, 100L);
            }else{
                BossBar bar = Bukkit.createBossBar(format(spell.getDisplayName() + ": " + data.getSpellMastery(spell) + "/" + spell.getMaxMastery()), spell.getMasteryBarColor(), BarStyle.SOLID);
                if (data.getSpellMastery(spell) / spell.getMaxMastery() > 1){
                    bar.setProgress(1);
                }else if (data.getSpellMastery(spell) / spell.getMaxMastery() < 0){
                    bar.setProgress(0);
                }else {bar.setProgress((double) data.getSpellMastery(spell) / spell.getMaxMastery()); }
                bar.addPlayer(p.getPlayer());
                masteryBars.put(spell, bar);
                data.setMasteryBars(masteryBars);
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                    if (bar.getProgress() == data.getMasteryBars().get(spell).getProgress()){
                        bar.removePlayer(p.getPlayer());
                    }
                }, 100L);
            }
        }
        if (!p.isOnline()){
            DataManager.savePlayerData(p, data);
        }
    }

    public static void addExperience(OfflinePlayer p, double exp){
        PlayerData data = p.isOnline() ? DataManager.getPlayerData(p) : DataManager.getConfigData(p);
        data.setDouble("experience", data.getDouble("experience") + exp);
        if (p.isOnline()){
            BossBar bar = data.getExpBar();
            String max = data.getInt("circle") < 9 ? String.valueOf(requiredExperience(data.getInt("circle") + 1)) : "MAX";
            if (bar == null){
                BossBar newbar = Bukkit.createBossBar(format("&bMagic Experience: " + data.getDouble("experience") + "/" + max), BarColor.WHITE, BarStyle.SOLID);
                if (data.getInt("circle") < 9){
                    if (data.getDouble("experience") / requiredExperience(data.getInt("circle") + 1) > 1){
                        newbar.setProgress(1);
                    }else if (data.getDouble("experience") / requiredExperience(data.getInt("circle") + 1) < 0){
                        newbar.setProgress(0);
                    }else {newbar.setProgress(data.getDouble("experience") / requiredExperience(data.getInt("circle") + 1)); }
                }else{
                    newbar.setProgress(1);
                }
                newbar.addPlayer(p.getPlayer());
                data.setExpBar(newbar);
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                    if (newbar.getProgress() == data.getExpBar().getProgress()){
                        newbar.removePlayer(p.getPlayer());
                    }
                }, 100L);
            }else{
                bar.removePlayer(p.getPlayer());
                bar.setTitle(format("&bMagic Experience: " + data.getDouble("experience") + "/" + max));
                if (data.getInt("circle") < 9){
                    if (data.getDouble("experience") / requiredExperience(data.getInt("circle") + 1) > 1){
                        bar.setProgress(1);
                    }else if (data.getDouble("experience") / requiredExperience(data.getInt("circle") + 1) < 0){
                        bar.setProgress(0);
                    }else {bar.setProgress(data.getDouble("experience") / requiredExperience(data.getInt("circle") + 1)); }
                }else{
                    bar.setProgress(1);
                }
                bar.addPlayer(p.getPlayer());
                data.setExpBar(bar);
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                    if (bar.getProgress() == data.getExpBar().getProgress()){
                        bar.removePlayer(p.getPlayer());
                    }
                }, 100L);
            }
        }
        if (data.getInt("circle") < 9){
            if (data.getDouble("experience") >= requiredExperience(data.getInt("circle") + 1)){
                double experience = data.getDouble("experience");
                data.setDouble("experience", (double) 0);
                addCircle(p.getPlayer(), 1);
                if (p.isOnline()){
                    p.getPlayer().sendMessage(format("&e&lCIRCLE UP!"), format("&bReached the " + StringUtils.toOrdinal(data.getInt("circle")) + " circle."), format("&bYou are now able to use spells up to the " + StringUtils.toOrdinal(data.getInt("circle")) + " rank."));
                }
                addExperience(p, (experience - requiredExperience(data.getInt("circle"))));
            }
        }
        if (!p.isOnline()){
            DataManager.savePlayerData(p, data);
        }
    }

    public static void addCircle(@NotNull Player p, int circle){
        PlayerData data = p.isOnline() ? getPlayerData(p) : getConfigData(p);
        int pcircle = data.getInt("circle");
        data.setDouble("max_mana", data.getDouble("max_mana") + (getMaxMana(circle + data.getInt("circle")) - getMaxMana(pcircle)));
        data.setDouble("mana_regeneration", data.getDouble("mana_regeneration") + (getManaRegen(circle + pcircle) - getManaRegen(pcircle)));
        data.setInt("circle", pcircle + circle);
        if (!p.isOnline()){
            savePlayerData(p, data);
        }
    }

    private static double getManaRegen(int circle){
        return switch (circle){
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 2.5;
            case 3 -> 3;
            case 4 -> 5;
            case 5 -> 6.25;
            case 6 -> 7.5;
            case 7 -> 10;
            case 8 -> 12.5;
            case 9 -> 15;
            default -> 0;
        };
    }

    private static int getMaxMana(int circle){
        return switch (circle){
            case 0 -> 100;
            case 1 -> 200;
            case 2 -> 400;
            case 3 -> 800;
            case 4 -> 1250;
            case 5 -> 2000;
            case 6 -> 2750;
            case 7 -> 4000;
            case 8 -> 7500;
            case 9 -> 10000;
            default -> 0;
        };
    }

    public static void savePlayerData(OfflinePlayer p, PlayerData data){
        File general = new File(getFolderPath(p) + "/general.yml");
        FileConfiguration gcfg = YamlConfiguration.loadConfiguration(general);
        List<String> statNames = StatManager.getStatNames();
        for (String stat : statNames){
            String type = StatManager.getType(stat);
            if (type.equals("double")){
                gcfg.set("stats." + stat, data.getDouble(stat));
            }else if (type.equals("int")){
                gcfg.set("stats." + stat, data.getInt(stat));
            } else if (type.equals("boolean")) {
                gcfg.set("stats." + stat, data.getBoolean(stat));
            } // If not, is an invalid stat, and should not be saved.
        }
        for (Spell spell : SpellRegistry.getAllSpells().values()){
            if (data.hasDiscovered(spell)){
                gcfg.getStringList("discovered_spells").add(spell.getType().toLowerCase());
            }
        }

        File masteries = new File(getFolderPath(p) + "/mastery.yml");
        FileConfiguration mcfg = YamlConfiguration.loadConfiguration(masteries);
        for (Spell spell : SpellRegistry.getAllSpells().values()){
            if (data.getSpellMastery(spell) >= 0){
                mcfg.set("spell_mastery." + spell.getType().toLowerCase(), data.getSpellMastery(spell));
            }
        }

        File stats = new File(getFolderPath(p) + "/stats.yml");
        FileConfiguration scfg = YamlConfiguration.loadConfiguration(stats);
        List<String> points = StatManager.getStatPoints();
        for (String point : points){
            scfg.set(point, data.getInt(point));
        }
        try {
            gcfg.save(general);
            mcfg.save(masteries);
            scfg.save(stats);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void saveAll(){
        for (Player p : Bukkit.getOnlinePlayers()){
            savePlayerData(p, getPlayerData(p));
        }
    }
}
