package me.nagasonic.alkatraz.items.magic.persistence;

import me.nagasonic.alkatraz.items.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.registry.MagicKeys;
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
        yaml.set(ROOT + ".instance-id", instance.instanceId().toString());
        yaml.set(ROOT + ".definition-key", MagicKeys.format(instance.definitionKey()));
        yaml.set(ROOT + ".modifiers", formatKeys(instance.modifiers()));
        yaml.set(ROOT + ".sockets", formatKeys(instance.sockets()));
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

        UUID instanceId = UUID.fromString(yaml.getString(ROOT + ".instance-id"));
        NamespacedKey definitionKey = MagicKeys.require(yaml.getString(ROOT + ".definition-key"));
        List<NamespacedKey> modifiers = parseKeys(yaml.getStringList(ROOT + ".modifiers"));
        List<NamespacedKey> sockets = parseKeys(yaml.getStringList(ROOT + ".sockets"));
        Map<String, Object> progression = sectionToMap(yaml.getConfigurationSection(ROOT + ".progression"));
        Map<String, Object> customData = sectionToMap(yaml.getConfigurationSection(ROOT + ".custom-data"));

        return new MagicItemInstance(instanceId, definitionKey, modifiers, sockets, progression, customData);
    }

    private static MagicItemInstance migrateForward(MagicItemInstance instance, int fromVersion) {
        if (fromVersion == ItemDataVersion.CURRENT) {
            return instance;
        }
        // Placeholder for chained migrations as the schema evolves.
        return instance;
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
