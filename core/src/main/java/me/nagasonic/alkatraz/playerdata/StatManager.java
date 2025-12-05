package me.nagasonic.alkatraz.playerdata;

import me.nagasonic.alkatraz.config.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

public class StatManager {
    private static Map<String, String> allStats = Collections.unmodifiableMap(new HashMap<>());
    private static Map<String, String> defValues = Collections.unmodifiableMap(new HashMap<>());
    private static List<String> statPoints = Collections.unmodifiableList(new ArrayList<>());

    public static void load(){
        YamlConfiguration statConfig = ConfigManager.getConfig("playerdata/playerdata.yml").get();
        List<String> keys = statConfig.getStringList("stat_list");
        for (String key : keys){
            String[] split = key.split(":");
            Map<String, String> stats = new HashMap<>(allStats);
            stats.put(split[0].replaceAll(":", ""), split[1].replaceAll(":", ""));
            allStats = Collections.unmodifiableMap(stats);
            Map<String, String> defs = new HashMap<>(defValues);
            defs.put(split[0].replaceAll(":", ""), split[2].replaceAll(":", ""));
            defValues = Collections.unmodifiableMap(defs);
        }
        statPoints = statConfig.getStringList("stat_point_list");
    }

    public static List<String> getStatNames(){
        return allStats.keySet().stream().toList();
    }

    public static String getType(String key){
        return allStats.get(key);
    }

    public static String getDefault(String key){
        return defValues.get(key);
    }

    public static List<String> getStatPoints(){
        return statPoints;
    }

}
