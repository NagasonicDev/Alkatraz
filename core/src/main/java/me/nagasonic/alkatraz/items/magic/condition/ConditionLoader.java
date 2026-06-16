package me.nagasonic.alkatraz.items.magic.condition;

import me.nagasonic.alkatraz.items.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.items.magic.registry.MagicKeys;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses condition lists from YAML configuration sections.
 */
public final class ConditionLoader {

    private ConditionLoader() {}

    public static List<Condition> fromSectionList(List<?> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }
        List<Condition> conditions = new ArrayList<>();
        for (Object entry : rawList) {
            if (entry instanceof Map<?, ?> map) {
                conditions.add(fromMap(toStringMap(map)));
            }
        }
        return List.copyOf(conditions);
    }

    public static List<Condition> fromSection(ConfigurationSection section, String path) {
        return fromSectionList(section.getList(path));
    }

    public static Condition fromMap(Map<String, Object> config) {
        String type = String.valueOf(config.get("type"));
        if ("composite".equalsIgnoreCase(type)) {
            return composite(config);
        }
        return MagicItemRegistries.CONDITION_TYPES.require(type).create(config);
    }

    private static Condition composite(Map<String, Object> config) {
        Object nested = config.get("conditions");
        if (!(nested instanceof List<?> list)) {
            return AlwaysCondition.INSTANCE;
        }
        List<Condition> children = fromSectionList(list);
        return context -> ConditionEvaluator.allMatch(children, context);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringMap(Map<?, ?> map) {
        Map<String, Object> result = new HashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
