package me.nagasonic.alkatraz.items.magic.effect;

import me.nagasonic.alkatraz.items.magic.registry.MagicItemRegistries;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses effect lists from YAML configuration sections.
 */
public final class EffectLoader {

    private EffectLoader() {}

    public static List<Effect> fromSectionList(List<?> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }
        List<Effect> effects = new ArrayList<>();
        for (Object entry : rawList) {
            if (entry instanceof Map<?, ?> map) {
                effects.add(fromMap(toStringMap(map)));
            }
        }
        return List.copyOf(effects);
    }

    public static List<Effect> fromSection(ConfigurationSection section, String path) {
        return fromSectionList(section.getList(path));
    }

    public static Effect fromMap(Map<String, Object> config) {
        String type = String.valueOf(config.get("type"));
        return MagicItemRegistries.EFFECT_TYPES.require(type).create(config);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringMap(Map<?, ?> map) {
        Map<String, Object> result = new HashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
