package me.nagasonic.alkatraz;
import fr.skytasul.glowingentities.GlowingEntities;
import me.nagasonic.alkatraz.commands.AlkatrazCommand;
import me.nagasonic.alkatraz.commands.SpellsCommand;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.ConfigUpdater;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.dom.MinecraftVersion;
import me.nagasonic.alkatraz.gui.SpellsGUI;
import me.nagasonic.alkatraz.gui.StatsGUI;
import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.items.wands.WandListeners;
import me.nagasonic.alkatraz.items.wands.WandRegistry;
import me.nagasonic.alkatraz.nms.NMS;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.StatManager;
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

    private static GlowingEntities glowingEntities;
    private static Alkatraz instance;
    private static YamlConfiguration pluginConfig;
    private static NMS nms = null;
    private static boolean enabled = true;

    {
        instance = this;
    }

    @Override
    public void onLoad() {
        pluginConfig = saveAndUpdateConfig("config.yml");
        saveConfig("playerdata/playerdata.yml");

        saveSpellConfigs();
        saveConfig("wands/wooden_wand.yml");
        if (!setupNMS()){
            enabled = false;
            return;
        }

        String lang = pluginConfig.getString("language", "en-us");
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        if ((boolean) Configs.CHECK_UPDATES.get()) UpdateChecker.checkUpdate();
        if (!enabled){
            logSevere("This version of Minecraft is not compatible with Alkatraz.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        } else nms.onEnable();
        Metrics metrics = new Metrics(this, 27657);
        glowingEntities = new GlowingEntities(instance);
        WandRegistry.registerWands();
        SpellRegistry.registerSpells();
        registerListener(new WandListeners());
        registerListener(new DataManager());
        registerListener(new SpellsGUI());
        registerListener(new StatsGUI());
        logInfo("NMS version " + nms.getClass().getSimpleName() + " registered!");
        getCommand("spells").setExecutor(new SpellsCommand());
        getCommand("alkatraz").setExecutor(new AlkatrazCommand());
        getCommand("alkatraz").setTabCompleter(new AlkatrazCommand());
        StatManager.load();
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

    private boolean setupNMS() {
        try {
            String nmsVersion = MinecraftVersion.getServerVersion().getNmsVersion();
            if (nmsVersion == null) return false;
            Class<?> clazz = Class.forName("me.nagasonic.alkatraz.nms.NMS_" + nmsVersion);

            if (NMS.class.isAssignableFrom(clazz)) {
                nms = (NMS) clazz.getDeclaredConstructor().newInstance();
            }

            return nms != null;
        } catch (Exception | Error e) {
            e.printStackTrace();
            return false;
        }
    }

    public static NMS getNms() {
        return nms;
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

    private void saveSpellConfigs(){
        saveConfig("spells/magic_missile.yml");
        saveConfig("spells/fireball.yml");
        saveConfig("spells/water_sphere.yml");
        saveConfig("spells/air_burst.yml");
        saveConfig("spells/earth_throw.yml");
        saveConfig("spells/lesser_heal.yml");
        saveConfig("spells/fire_blast.yml");
        saveConfig("spells/detect.yml");
        saveConfig("spells/stealth.yml");
        saveConfig("spells/disguise.yml");
        saveConfig("spells/swift.yml");
        saveConfig("spells/fire_wall.yml");
    }

    public static GlowingEntities getGlowingEntities() {
        return glowingEntities;
    }
}
