package me.nagasonic.alkatraz.config;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class ConfigManager {

    private static final Map<String, Config> configs = new HashMap<>();
    private static final Map<String, YamlConfiguration> defaultConfigs = new HashMap<>();

    public Map<String, Config> getConfigs() {
        return configs;
    }

    public static Map<String, YamlConfiguration> getDefaultConfigs() {
        return defaultConfigs;
    }

    private static final Collection<String> reloadedConfigs = new HashSet<>();
    public static Config getConfig(String name) {
        if (!configs.containsKey(name)){
            configs.put(name, new Config(name));
            configs.get(name).reload();
        }

        return configs.get(name);
    }

    private static final Collection<String> fetchedConfigs = new HashSet<>();
    public static YamlConfiguration getDefault(String name) {
        try {
            if (!fetchedConfigs.contains(name) && !defaultConfigs.containsKey(name)) {
                // if a config has already been fetched and didn't exist, we don't need to try again and spam console with errors
                fetchedConfigs.add(name);
                defaultConfigs.put(name, fetchDefaultConfiguration(name));
            }
        } catch (FileNotFoundException e){
            Alkatraz.logSevere("Default config " + name + " was called, but doesn't exist in plugin!");
            return null;
        }
        return defaultConfigs.get(name);
    }

    public static Config saveConfig(String name) {
        return getConfig(name).save();
    }

    public static Config reloadConfig(String name) {
        return getConfig(name).reload();
    }

    public static boolean doesPathExist(YamlConfiguration config, String root, String key){
        ConfigurationSection section = config.getConfigurationSection(root);
        return section != null && section.getKeys(false).contains(key);
    }

    public static YamlConfiguration fetchDefaultConfiguration(String name) throws FileNotFoundException {
        InputStream customClassStream = Alkatraz.getInstance().getClass().getResourceAsStream("/" + name);
        if (customClassStream == null) throw new FileNotFoundException("Config " + name + " does not exist");
        InputStreamReader strR = new InputStreamReader(customClassStream);
        return YamlConfiguration.loadConfiguration(strR);
    }
}
