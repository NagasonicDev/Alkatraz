package me.nagasonic.alkatraz.items.magic.persistence;

import me.nagasonic.alkatraz.api.magic.instance.Engraving;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned YAML serialization for {@link MagicItemInstance} payloads stored in PDC.
 */
public final class ItemInstanceSerializer {

    private static final String ROOT = "instance";

    private ItemInstanceSerializer() {}

    public static String serialize(MagicItemInstance instance) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(ROOT + ".data-version", ItemDataVersion.CURRENT);
        yaml.set(ROOT + ".definition-key", MagicKeys.format(instance.definitionKey()));
        yaml.set(ROOT + ".modifiers", formatKeys(instance.modifiers()));
        yaml.set(ROOT + ".engravings", serializeEngravings(instance.engravings()));
        yaml.set(ROOT + ".progression", instance.progression());
        yaml.set(ROOT + ".custom-data", instance.customData());
        return yaml.saveToString();
    }

    public static MagicItemInstance deserialize(String raw) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse magic item instance payload", ex);
        }

        int version = yaml.getInt(ROOT + ".data-version", 0);
        if (version <= 0) {
            throw new IllegalStateException("Missing or invalid magic item data-version");
        }

        MagicItemInstance migrated = parseVersion(yaml, version);
        return migrateForward(migrated, version);
    }

    private static MagicItemInstance parseVersion(YamlConfiguration yaml, int version) {
        if (version != ItemDataVersion.CURRENT) {
            // Future migrations deserialize older versions here.
        }

        String rawInstanceId = yaml.getString(ROOT + ".instance-id");
        UUID instanceId = rawInstanceId != null ? UUID.fromString(rawInstanceId) : null;
        NamespacedKey definitionKey = MagicKeys.require(yaml.getString(ROOT + ".definition-key"));
        List<NamespacedKey> modifiers = parseKeys(yaml.getStringList(ROOT + ".modifiers"));
        List<Engraving> engravings = parseEngravings(yaml.getMapList(ROOT + ".engravings"));
        Map<String, Object> progression = sectionToMap(yaml.getConfigurationSection(ROOT + ".progression"));
        Map<String, Object> customData = sectionToMap(yaml.getConfigurationSection(ROOT + ".custom-data"));

        return new MagicItemInstance(instanceId, definitionKey, modifiers, engravings, progression, customData);
    }

    private static MagicItemInstance migrateForward(MagicItemInstance instance, int fromVersion) {
        if (fromVersion == ItemDataVersion.CURRENT) {
            return instance;
        }
        // Placeholder for chained migrations as the schema evolves.
        return instance;
    }

    private static List<Map<String, String>> serializeEngravings(List<Engraving> engravings) {
        List<Map<String, String>> list = new ArrayList<>();
        for (Engraving eng : engravings) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("engraving_key", MagicKeys.format(eng.engravingKey()));
            entry.put("trigger_key", MagicKeys.format(eng.triggerKey()));
            list.add(entry);
        }
        return list;
    }

    private static List<Engraving> parseEngravings(List<Map<?, ?>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Engraving> engravings = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            Object engravingKeyObj = entry.get("engraving_key");
            Object triggerKeyObj = entry.get("trigger_key");
            if (engravingKeyObj == null || triggerKeyObj == null) continue;
            MagicKeys.parse(String.valueOf(engravingKeyObj)).ifPresent(ek ->
                    MagicKeys.parse(String.valueOf(triggerKeyObj)).ifPresent(tk ->
                            engravings.add(new Engraving(ek, tk))));
        }
        return engravings;
    }

    private static List<String> formatKeys(List<NamespacedKey> keys) {
        List<String> formatted = new ArrayList<>();
        for (NamespacedKey key : keys) {
            formatted.add(MagicKeys.format(key));
        }
        return formatted;
    }

    private static List<NamespacedKey> parseKeys(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<NamespacedKey> keys = new ArrayList<>();
        for (String entry : raw) {
            MagicKeys.parse(entry).ifPresent(keys::add);
        }
        return List.copyOf(keys);
    }

    private static Map<String, Object> sectionToMap(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            map.put(key, section.get(key));
        }
        return map;
    }
}
