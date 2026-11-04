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
    private static Map<String, Set<Material>> typeToMaterials = Map.of();

    private ItemTypeMapper() {}

    public static void load() {
        YamlConfiguration config = ConfigManager.getConfig("magic/item_types.yml").get();
        Map<Material, Set<String>> map = new HashMap<>();
        Map<String, Set<Material>> reverse = new HashMap<>();

        for (String type : config.getConfigurationSection("types").getKeys(false)) {
            List<String> materials = config.getStringList("types." + type);
            for (String matStr : materials) {
                Material mat = MaterialCompat.resolve(matStr);
                if (mat != null) {
                    map.computeIfAbsent(mat, k -> new HashSet<>()).add(type);
                    reverse.computeIfAbsent(type, k -> new HashSet<>()).add(mat);
                } else {
                    Alkatraz.logWarning("Unknown material in item_types.yml: " + matStr);
                }
            }
        }

        // Version-gated materials that cannot be declared in the YAML statically:
        // Mace only exists on Minecraft 1.21+, so resolve it at runtime instead of
        // warning on every older server.
        Material mace = Material.matchMaterial("MACE");
        if (mace != null) {
            map.computeIfAbsent(mace, k -> new HashSet<>()).add("mace");
            reverse.computeIfAbsent("mace", k -> new HashSet<>()).add(mace);
        }

        // Freeze the maps
        Map<Material, Set<String>> frozen = new HashMap<>();
        for (var entry : map.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        Map<String, Set<Material>> frozenReverse = new HashMap<>();
        for (var entry : reverse.entrySet()) {
            frozenReverse.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        materialToTypes = Collections.unmodifiableMap(frozen);
        typeToMaterials = Collections.unmodifiableMap(frozenReverse);
        Alkatraz.logInfo("Loaded " + materialToTypes.size() + " material type mappings.");
    }

    public static Set<String> getTypes(Material material) {
        return materialToTypes.getOrDefault(material, Set.of());
    }

    public static boolean hasType(Material material, String type) {
        Set<String> types = materialToTypes.get(material);
        return types != null && types.contains(type);
    }

    public static Set<Material> getMaterials(String type) {
        return typeToMaterials.getOrDefault(type, Set.of());
    }
}
