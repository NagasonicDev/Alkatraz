package me.nagasonic.alkatraz.playerdata;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.implementation.MagicMissile;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
            data.setMagicLevel(cfg.getDouble("stats.magic_level"));
            data.setMagicDamage(cfg.getDouble("stats.magic_damage"));
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
            data.setDiscovered(SpellRegistry.getSpell(MagicMissile.class), true);
        }else {
            data.setMaxMana(100);
            data.setMana(100);
            data.setCircle(0);
            data.setMagicLevel(1);
            data.setMagicDamage(1);
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
        savePlayerData(e.getPlayer());
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
                p.setLevel((int) data.getMana());
                p.setExp(Float.parseFloat(String.valueOf((data.getMana() / data.getMaxMana())-0.01)));
            }
        }
    }

    public static void subMana(Player p, double amount) {
        PlayerData data = getPlayerData(p);
        data.setMana(data.getMana() - amount);
        if (data.getMana() < 0) { data.setMana(0); }
        ItemStack item = p.getItemInHand();
        if (item.getType() != Material.AIR && item.getAmount() != 0){
            if (Wand.isWand(p.getItemInHand())){
                p.setLevel((int) data.getMana());
                p.setExp(Float.parseFloat(String.valueOf((data.getMana() / data.getMaxMana())-0.01)));
            }
        }
    }

    public static void addManaPerSecond(){
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()){
                PlayerData data = getPlayerData(p);
                if (data.getMana() < data.getMaxMana()){
                    addMana(p, 1);
                }
            }
        }, 0L, 20L);
    }

    public static void addSpellMastery(Player p, Spell spell, int mastery){
        PlayerData data = getPlayerData(p);
        if (data.getSpellMastery(spell) == -1 && mastery > 0){
            data.setSpellMastery(spell, 0);
        }
        data.setSpellMastery(spell, data.getSpellMastery(spell) + mastery);
        Map<Spell, BossBar> masteryBars = data.getMasteryBars();
        if (masteryBars.containsKey(spell)){
            BossBar bar = masteryBars.get(spell);
            bar.removePlayer(p);
            bar.setTitle(ColorFormat.format(spell.getDisplayName() + ": " + data.getSpellMastery(spell) + "/" + spell.getMaxMastery()));
            bar.setProgress((double) data.getSpellMastery(spell) / spell.getMaxMastery());
            bar.addPlayer(p);
            masteryBars.replace(spell, bar);
            data.setMasteryBars(masteryBars);
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                if (bar.getProgress() == data.getMasteryBars().get(spell).getProgress()){
                    bar.removePlayer(p);
                }
            }, 100L);
        }else{
            BossBar bar = Bukkit.createBossBar(ColorFormat.format(spell.getDisplayName() + ": " + data.getSpellMastery(spell) + "/" + spell.getMaxMastery()), spell.getMasteryBarColor(), BarStyle.SOLID);
            bar.setProgress((double) data.getSpellMastery(spell) / spell.getMaxMastery());
            bar.addPlayer(p);
            masteryBars.put(spell, bar);
            data.setMasteryBars(masteryBars);
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                if (bar.getProgress() == data.getMasteryBars().get(spell).getProgress()){
                    bar.removePlayer(p);
                }
            }, 100L);
        }
    }

    public static void savePlayerData(Player p){
        PlayerData data = getPlayerData(p);
        File general = new File(getFolderPath(p) + "/general.yml");
        FileConfiguration gcfg = YamlConfiguration.loadConfiguration(general);
        gcfg.set("stats.max_mana", data.getMaxMana());
        gcfg.set("stats.mana", data.getMana());
        gcfg.set("stats.circle", data.getCircle());
        gcfg.set("stats.magic_level", data.getMagicLevel());
        gcfg.set("stats.magic_damage", data.getMagicDamage());
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

        try {
            gcfg.save(general);
            mcfg.save(masteries);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void saveAll(){
        for (Player p : Bukkit.getOnlinePlayers()){
            savePlayerData(p);
        }
    }
}
