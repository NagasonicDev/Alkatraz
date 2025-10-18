package me.nagasonic.alkatraz;

import me.nagasonic.alkatraz.commands.AlkatrazCommand;
import me.nagasonic.alkatraz.commands.SpellsCommand;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.ConfigUpdater;
import me.nagasonic.alkatraz.gui.SpellsGUI;
import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.items.wands.WandListeners;
import me.nagasonic.alkatraz.items.wands.WandRegistry;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.UpdateChecker;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static me.nagasonic.alkatraz.items.wands.WandListeners.switchFrom;

public final class Alkatraz extends JavaPlugin {

    private static Alkatraz instance;
    private static YamlConfiguration pluginConfig;
    {
        instance = this;
    }

    @Override
    public void onLoad() {
        pluginConfig = saveAndUpdateConfig("config.yml");

        saveConfig("playerdata/playerdata.yml");

        saveConfig("spells/magic_missile.yml");
        saveConfig("spells/fireball.yml");
        saveConfig("spells/water_sphere.yml");
        saveConfig("wands/wooden_wand.yml");


        String lang = pluginConfig.getString("language", "en-us");
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        if (pluginConfig.getBoolean("check_updates")) UpdateChecker.checkUpdate();
        WandRegistry.registerWands();
        SpellRegistry.registerSpells();
        registerListener(new WandListeners());
        registerListener(new DataManager());
        registerListener(new SpellsGUI());
        getCommand("spells").setExecutor(new SpellsCommand());
        getCommand("alkatraz").setExecutor(new AlkatrazCommand());
        getCommand("alkatraz").setTabCompleter(new AlkatrazCommand());
        DataManager.addManaPerSecond();
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()){
            ItemStack wand = p.getInventory().getItem(p.getInventory().getHeldItemSlot());
            if (wand != null){
                if (wand.getType() != Material.AIR && wand.getAmount() != 0){
                    if (Wand.isWand(wand)){
                        switchFrom(p);
                    }
                }
            }
        }
        DataManager.saveAll();
    }

    public static Alkatraz getInstance() {
        return instance;
    }

    public static void logInfo(String message){
        instance.getServer().getLogger().info("[Alkatraz] " + message);
    }

    public static void logWarning(String warning){
        instance.getServer().getLogger().warning("[Alkatraz] " + warning);
    }
    public static void logFine(String warning){
        instance.getServer().getLogger().fine("[Alkatraz] " + warning);
        Utils.sendMessage(instance.getServer().getConsoleSender(), "&a[Alkatraz] " + warning);
    }

    public static void logSevere(String help){
        instance.getServer().getLogger().severe("[Alkatraz] " + help);
    }

    private void registerListener(Listener listener){
        getServer().getPluginManager().registerEvents(listener, this);
    }

    public YamlConfiguration saveConfig(String name){
        save(name);
        return ConfigManager.saveConfig(name).get();
    }

    public void save(String name){
        File file = new File(this.getDataFolder(), name);
        if (!file.exists()) this.saveResource(name, false);
    }

    private void updateConfig(String name){
        File configFile = new File(getDataFolder(), name);
        try {
            ConfigUpdater.update(instance, name, configFile, new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateConfig(String name, List<String> excludedSections){
        File configFile = new File(getDataFolder(), name);
        try {
            ConfigUpdater.update(instance, name, configFile, excludedSections);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private YamlConfiguration saveAndUpdateConfig(String config){
        save(config);
        updateConfig(config);
        return saveConfig(config);
    }

    private YamlConfiguration saveAndUpdateConfig(String config, List<String> excludedSections){
        save(config);
        updateConfig(config, excludedSections);
        return saveConfig(config);
    }

    public static YamlConfiguration getPluginConfig() {
        return pluginConfig;
    }
}
