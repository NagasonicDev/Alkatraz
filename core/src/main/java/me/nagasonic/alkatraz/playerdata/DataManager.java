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
import java.util.Map;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class DataManager implements Listener {
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
            data.setMaxMana(cfg.getDouble("stats.max_mana"));
            data.setMana(cfg.getDouble("stats.mana"));
            data.setCircle(cfg.getInt("stats.circle"));
            data.setManaRegeneration(cfg.getDouble("stats.mana_regeneration"));
            data.setExperience(cfg.getDouble("stats.experience"));
            data.setMagicAffinity(cfg.getDouble("stats.magic_affinity"));
            data.setMagicResistance(cfg.getDouble("stats.magic_resistance"));
            data.setFireAffinity(cfg.getDouble("stats.fire_affinity"));
            data.setFireResistance(cfg.getDouble("stats.fire_resistance"));
            data.setAirAffinity(cfg.getDouble("stats.air_affinity"));
            data.setAirResistance(cfg.getDouble("stats.air_resistance"));
            data.setEarthAffinity(cfg.getDouble("stats.earth_affinity"));
            data.setEarthResistance(cfg.getDouble("stats.earth_resistance"));
            data.setWaterAffinity(cfg.getDouble("stats.water_affinity"));
            data.setWaterResistance(cfg.getDouble("stats.water_resistance"));
            data.setLightAffinity(cfg.getDouble("stats.light_affinity"));
            data.setLightResistance(cfg.getDouble("stats.light_resistance"));
            data.setDarkAffinity(cfg.getDouble("stats.dark_affinity"));
            data.setDarkResistance(cfg.getDouble("stats.dark_resistance"));
            for (Spell spell : SpellRegistry.getAllSpells().values()){
                if (cfg.getStringList("discovered_spells").contains(spell.getType().toLowerCase())){
                    data.setDiscovered(spell, true);
                }
            }
        }else {
            data.setMaxMana(100);
            data.setMana(100);
            data.setCircle(0);
            data.setManaRegeneration(1);
            data.setExperience(0);
            data.setMagicAffinity(1);
            data.setMagicResistance(0);
            data.setFireAffinity(0);
            data.setFireResistance(0);
            data.setAirAffinity(0);
            data.setAirResistance(0);
            data.setEarthAffinity(0);
            data.setEarthResistance(0);
            data.setWaterAffinity(0);
            data.setWaterResistance(0);
            data.setLightAffinity(0);
            data.setLightResistance(0);
            data.setDarkAffinity(0);
            data.setDarkResistance(0);
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
            data.setStatPoints(scfg.getInt("stat_points"));
            data.setStatResetTokens(scfg.getInt("reset_tokens"));
            data.setFireStatPoints(scfg.getInt("fire_points"));
            data.setWaterStatPoints(scfg.getInt("water_points"));
            data.setAirStatPoints(scfg.getInt("air_points"));
            data.setEarthStatPoints(scfg.getInt("earth_points"));
            data.setLightStatPoints(scfg.getInt("light_points"));
            data.setDarkStatPoints(scfg.getInt("dark_points"));
        }else{
            data.setStatPoints((Integer) Configs.DEFAULT_STAT_POINTS.get());
            data.setStatResetTokens((Integer) Configs.DEFAULT_RESET_TOKENS.get());
            data.setFireStatPoints(0);
            data.setWaterStatPoints(0);
            data.setAirStatPoints(0);
            data.setEarthStatPoints(0);
            data.setLightStatPoints(0);
            data.setDarkStatPoints(0);
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
        data.setMana(data.getMana() + amount);
        if (data.getMana() > data.getMaxMana()){
            data.setMana(data.getMaxMana());
        }
        ItemStack item = p.getItemInHand();
        if (item.getType() != Material.AIR && item.getAmount() != 0){
            if (Wand.isWand(item)){
                Alkatraz.getNms().fakeExp(p, (float) (data.getMana() / data.getMaxMana()), (int) data.getMana(), 1);
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
        data.setMana(data.getMana() - amount);
        if (data.getMana() < 0) { data.setMana(0); }
        ItemStack item = p.getItemInHand();
        if (item.getType() != Material.AIR && item.getAmount() != 0){
            if (Wand.isWand(p.getItemInHand())){
                Alkatraz.getNms().fakeExp(p, (float) (data.getMana() / data.getMaxMana()), (int) data.getMana(), 1);
            }
        }
    }

    public static void addManaPerSecond(){
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()){
                PlayerData data = getPlayerData(p);
                if (data.getMana() < data.getMaxMana()){
                    addMana(p, data.getManaRegeneration());
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
        data.setExperience(data.getExperience() + exp);
        if (p.isOnline()){
            BossBar bar = data.getExpBar();
            String max = data.getCircle() < 9 ? String.valueOf(requiredExperience(data.getCircle() + 1)) : "MAX";
            if (bar == null){
                BossBar newbar = Bukkit.createBossBar(format("&bMagic Experience: " + data.getExperience() + "/" + max), BarColor.WHITE, BarStyle.SOLID);
                if (data.getCircle() < 9){
                    if (data.getExperience() / requiredExperience(data.getCircle() + 1) > 1){
                        newbar.setProgress(1);
                    }else if (data.getExperience() / requiredExperience(data.getCircle() + 1) < 0){
                        newbar.setProgress(0);
                    }else {newbar.setProgress(data.getExperience() / requiredExperience(data.getCircle() + 1)); }
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
                bar.setTitle(format("&bMagic Experience: " + data.getExperience() + "/" + max));
                if (data.getCircle() < 9){
                    if (data.getExperience() / requiredExperience(data.getCircle() + 1) > 1){
                        bar.setProgress(1);
                    }else if (data.getExperience() / requiredExperience(data.getCircle() + 1) < 0){
                        bar.setProgress(0);
                    }else {bar.setProgress(data.getExperience() / requiredExperience(data.getCircle() + 1)); }
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
        if (data.getCircle() < 9){
            if (data.getExperience() >= requiredExperience(data.getCircle() + 1)){
                double experience = data.getExperience();
                data.setExperience(0);
                addCircle(p.getPlayer(), 1);
                if (p.isOnline()){
                    p.getPlayer().sendMessage(format("&e&lCIRCLE UP!"), format("&bReached the " + StringUtils.toOrdinal(data.getCircle()) + " circle."), format("&bYou are now able to use spells up to the " + StringUtils.toOrdinal(data.getCircle()) + " rank."));
                }
                addExperience(p, (experience - requiredExperience(data.getCircle())));
            }
        }
        if (!p.isOnline()){
            DataManager.savePlayerData(p, data);
        }
    }

    public static void addCircle(@NotNull Player p, int circle){
        PlayerData data = p.isOnline() ? getPlayerData(p) : getConfigData(p);
        int pcircle = data.getCircle();
        data.setMaxMana(data.getMaxMana() + (getMaxMana(circle + data.getCircle()) - getMaxMana(pcircle)));
        data.setManaRegeneration(data.getManaRegeneration() + (getManaRegen(circle) - getManaRegen(data.getCircle())));
        data.setCircle(pcircle + circle);
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
        gcfg.set("stats.max_mana", data.getMaxMana());
        gcfg.set("stats.mana", data.getMana());
        gcfg.set("stats.circle", data.getCircle());
        gcfg.set("stats.mana_regeneration", data.getManaRegeneration());
        gcfg.set("stats.experience", data.getExperience());
        gcfg.set("stats.magic_affinity", data.getMagicAffinity());
        gcfg.set("stats.magic_resistance", data.getMagicResistance());
        gcfg.set("stats.fire_affinity", data.getFireAffinity());
        gcfg.set("stats.fire_resistance", data.getFireResistance());
        gcfg.set("stats.air_affinity", data.getAirAffinity());
        gcfg.set("stats.air_resistance", data.getAirResistance());
        gcfg.set("stats.earth_affinity", data.getEarthAffinity());
        gcfg.set("stats.earth_resistance", data.getEarthResistance());
        gcfg.set("stats.water_affinity", data.getWaterAffinity());
        gcfg.set("stats.water_resistance", data.getWaterResistance());
        gcfg.set("stats.light_affinity", data.getLightAffinity());
        gcfg.set("stats.light_resistance", data.getLightResistance());
        gcfg.set("stats.dark_affinity", data.getDarkAffinity());
        gcfg.set("stats.dark_resistance", data.getDarkResistance());
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
        scfg.set("stat_points", data.getStatPoints());
        scfg.set("reset_tokens", data.getStatResetTokens());
        scfg.set("fire_points", data.getFireStatPoints());
        scfg.set("air_points", data.getAirStatPoints());
        scfg.set("water_points", data.getWaterStatPoints());
        scfg.set("earth_points", data.getEarthStatPoints());
        scfg.set("light_points", data.getLightStatPoints());
        scfg.set("dark_points", data.getDarkStatPoints());

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
