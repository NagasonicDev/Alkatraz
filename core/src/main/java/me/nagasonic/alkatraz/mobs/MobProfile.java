package me.nagasonic.alkatraz.mobs;

import me.nagasonic.alkatraz.spells.Element;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Value object carrying elemental stats and spell ids parsed from a magic mob's
 * yml config. Passed into {@link MagicEntity#initMagic} during NMS construction
 * so entity classes do not touch the config system directly.
 *
 * <p>Natural spawn replacement is configured with:
 * <ul>
 *   <li>{@code replaces} — vanilla {@link EntityType} this magic mob can replace</li>
 *   <li>{@code spawn_chance} — probability (0.05 or 5 for 5%) when that type spawns</li>
 *   <li>{@code spawn_reasons} — optional spawn reasons; defaults to {@code NATURAL}</li>
 * </ul>
 */
public class MobProfile {

    private final Map<Element, Double> affinities  = new HashMap<>();
    private final Map<Element, Double> resistances = new HashMap<>();
    private final List<String>         spellIds;
    private final EntityType           replaces;
    private final double               spawnChance;
    private final Set<CreatureSpawnEvent.SpawnReason> spawnReasons;

    public MobProfile(YamlConfiguration cfg) {
        for (Element element : Element.values()) {
            String key = elementKey(element);
            affinities.put(element,  cfg.getDouble(key + "_affinity",   0.0));
            resistances.put(element, cfg.getDouble(key + "_resistance", 0.0));
        }
        spellIds = cfg.getStringList("spells");
        replaces = parseReplaces(cfg.getString("replaces"));
        spawnChance = parseSpawnChance(cfg.getDouble("spawn_chance", 0.0));
        spawnReasons = parseSpawnReasons(cfg.getStringList("spawn_reasons"));
    }

    public double getAffinity(Element element) {
        return affinities.getOrDefault(element, 0.0);
    }

    public double getResistance(Element element) {
        return resistances.getOrDefault(element, 0.0);
    }

    public List<String> getSpellIds() {
        return spellIds;
    }

    public EntityType getReplaces() {
        return replaces;
    }

    public double getSpawnChance() {
        return spawnChance;
    }

    public Set<CreatureSpawnEvent.SpawnReason> getSpawnReasons() {
        return spawnReasons;
    }

    public boolean canReplaceNaturally() {
        return replaces != null && spawnChance > 0.0;
    }

    private static EntityType parseReplaces(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Accepts 0–1 fractions or 1–100 percentages. */
    private static double parseSpawnChance(double raw) {
        if (raw <= 0.0) {
            return 0.0;
        }
        if (raw > 1.0) {
            return Math.min(raw / 100.0, 1.0);
        }
        return raw;
    }

    private static Set<CreatureSpawnEvent.SpawnReason> parseSpawnReasons(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return EnumSet.of(CreatureSpawnEvent.SpawnReason.NATURAL);
        }
        Set<CreatureSpawnEvent.SpawnReason> reasons = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            try {
                reasons.add(CreatureSpawnEvent.SpawnReason.valueOf(entry.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // skip invalid entries
            }
        }
        return reasons.isEmpty()
                ? EnumSet.of(CreatureSpawnEvent.SpawnReason.NATURAL)
                : Collections.unmodifiableSet(reasons);
    }

    private static String elementKey(Element element) {
        return (element == Element.NONE) ? "magic" : element.name().toLowerCase();
    }
}
