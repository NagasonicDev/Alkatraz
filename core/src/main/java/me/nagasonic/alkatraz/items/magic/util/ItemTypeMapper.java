package me.nagasonic.alkatraz.items.magic.util;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.util.MaterialCompat;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ItemTypeMapper {

    private static Map<Material, Set<String>> materialToTypes = Map.of();

    private ItemTypeMapper() {}

    public static void load() {
        YamlConfiguration config = ConfigManager.getConfig("magic/item_types.yml").get();
        Map<Material, Set<String>> map = new HashMap<>();

        for (String type : config.getConfigurationSection("types").getKeys(false)) {
            List<String> materials = config.getStringList("types." + type);
            for (String matStr : materials) {
                try {
                    Material mat = Material.valueOf(matStr.toUpperCase());
                    map.computeIfAbsent(mat, k -> new HashSet<>()).add(type);
                } catch (IllegalArgumentException e) {
                    Alkatraz.logWarning("Unknown material in item_types.yml: " + matStr);
                }
            }
        }

        // Freeze the map
        Map<Material, Set<String>> frozen = new HashMap<>();
        for (var entry : map.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        materialToTypes = Collections.unmodifiableMap(frozen);
        Alkatraz.logInfo("Loaded " + materialToTypes.size() + " material type mappings.");
    }

    public static Set<String> getTypes(Material material) {
        return materialToTypes.getOrDefault(material, Set.of());
    }

    public static boolean hasType(Material material, String type) {
        Set<String> types = materialToTypes.get(material);
        return types != null && types.contains(type);
    }
}
