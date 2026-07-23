package me.nagasonic.alkatraz.items.magic.config;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.config.ConfigManager;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

public class SetBonusConfig {

    public static class BonusEntry {
        public final NamespacedKey attribute;
        public final double value;

        public BonusEntry(NamespacedKey attribute, double value) {
            this.attribute = attribute;
            this.value = value;
        }
    }

    public static class SetBonusData {
        public final String setName;
        public final Map<Integer, List<BonusEntry>> bonuses;

        public SetBonusData(String setName, Map<Integer, List<BonusEntry>> bonuses) {
            this.setName = setName;
            this.bonuses = bonuses;
        }
    }

    public static Map<String, SetBonusData> loadAll() {
        Map<String, SetBonusData> result = new HashMap<>();
        Alkatraz plugin = Alkatraz.getInstance();

        plugin.save("magic/set_bonuses.yml");
        YamlConfiguration config = ConfigManager.getConfig("magic/set_bonuses.yml").get();

        if (config.isConfigurationSection("set_bonuses")) {
            ConfigurationSection setsSection = config.getConfigurationSection("set_bonuses");
            if (setsSection != null) {
                for (String setName : setsSection.getKeys(false)) {
                    ConfigurationSection setSection = setsSection.getConfigurationSection(setName);
                    if (setSection != null) {
                        Map<Integer, List<BonusEntry>> bonuses = new HashMap<>();
                        for (String key : setSection.getKeys(false)) {
                            try {
                                int pieces = Integer.parseInt(key);
                                ConfigurationSection piecesSection = setSection.getConfigurationSection(key);
                                if (piecesSection != null) {
                                    List<BonusEntry> entries = new ArrayList<>();
                                    for (String attrKey : piecesSection.getKeys(false)) {
                                        double value = piecesSection.getDouble(attrKey);
                                        NamespacedKey namespacedKey = MagicKeys.alkatraz(attrKey);
                                        entries.add(new BonusEntry(namespacedKey, value));
                                    }
                                    bonuses.put(pieces, entries);
                                }
                            } catch (NumberFormatException e) {
                                Alkatraz.logWarning("Invalid bonus key in set_bonuses.yml: " + key);
                            }
                        }
                        result.put(setName, new SetBonusData(setName, bonuses));
                    }
                }
            }
        }
        return result;
    }
}
