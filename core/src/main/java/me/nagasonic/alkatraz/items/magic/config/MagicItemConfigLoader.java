package me.nagasonic.alkatraz.items.magic.config;

import me.nagasonic.alkatraz.items.magic.condition.Condition;
import me.nagasonic.alkatraz.items.magic.condition.ConditionLoader;
import me.nagasonic.alkatraz.items.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.items.magic.definition.ItemVisual;
import me.nagasonic.alkatraz.items.magic.effect.Effect;
import me.nagasonic.alkatraz.items.magic.effect.EffectLoader;
import me.nagasonic.alkatraz.items.magic.modifier.ModifierDefinition;
import me.nagasonic.alkatraz.items.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.items.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerBinding;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads immutable item and modifier definitions from YAML.
 */
public final class MagicItemConfigLoader {

    private MagicItemConfigLoader() {}

    public static ItemDefinition loadItemDefinition(YamlConfiguration config) {
        NamespacedKey key = MagicKeys.require(config.getString("key"));
        Material material = Utils.materialFromString(config.getString("material", "STONE")).getType();
        String displayName = config.getString("display_name", key.getKey());
        List<String> lore = config.getStringList("lore");
        int customModelData = config.getInt("custom_model_data", 0);
        boolean unbreakable = config.getBoolean("unbreakable", false);
        boolean hideAttributes = config.getBoolean("hide_attributes", true);

        ItemVisual visual = new ItemVisual(material, displayName, lore, customModelData, unbreakable, hideAttributes);
        List<NamespacedKey> components = parseKeyList(config.getStringList("components"));
        Map<NamespacedKey, Double> attributes = parseAttributeSection(config.getConfigurationSection("attributes"));
        List<TriggerBinding> triggers = parseTriggerBindings(config.getMapList("triggers"));
        Map<String, Object> staticConfig = sectionToMap(config);

        return new ItemDefinition(key, visual, components, attributes, triggers, staticConfig);
    }

    public static ModifierDefinition loadModifierDefinition(YamlConfiguration config) {
        NamespacedKey key = MagicKeys.require(config.getString("key"));
        Map<NamespacedKey, Double> attributes = parseAttributeSection(config.getConfigurationSection("attributes"));
        List<TriggerBinding> triggers = parseTriggerBindings(config.getMapList("triggers"));
        Map<String, Object> staticConfig = sectionToMap(config);
        return new ModifierDefinition(key, attributes, triggers, staticConfig);
    }

    public static List<TriggerBinding> parseTriggerBindings(List<Map<?, ?>> rawBindings) {
        if (rawBindings == null || rawBindings.isEmpty()) {
            return List.of();
        }
        List<TriggerBinding> bindings = new ArrayList<>();
        for (Map<?, ?> raw : rawBindings) {
            Map<String, Object> map = toStringMap(raw);
            NamespacedKey triggerType = MagicKeys.require(String.valueOf(map.get("trigger")));
            List<Condition> conditions = ConditionLoader.fromSectionList(castList(map.get("conditions")));
            List<Effect> effects = EffectLoader.fromSectionList(castList(map.get("effects")));
            int priority = Integer.parseInt(String.valueOf(map.getOrDefault("priority", 0)));
            bindings.add(new TriggerBinding(triggerType, conditions, effects, priority));
        }
        return List.copyOf(bindings);
    }

    private static Map<NamespacedKey, Double> parseAttributeSection(ConfigurationSection section) {
        Map<NamespacedKey, Double> attributes = new LinkedHashMap<>();
        if (section == null) {
            return attributes;
        }
        for (String rawKey : section.getKeys(false)) {
            MagicKeys.parse(rawKey).ifPresent(key ->
                    attributes.put(key, section.getDouble(rawKey)));
        }
        return attributes;
    }

    private static List<NamespacedKey> parseKeyList(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<NamespacedKey> keys = new ArrayList<>();
        for (String entry : raw) {
            MagicKeys.parse(entry).ifPresent(keys::add);
        }
        return List.copyOf(keys);
    }

    private static Map<String, Object> sectionToMap(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (section == null) {
            return map;
        }
        for (String key : section.getKeys(false)) {
            map.put(key, section.get(key));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<?> castList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }
}
