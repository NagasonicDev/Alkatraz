package me.nagasonic.alkatraz.nms.entity;

import me.nagasonic.alkatraz.spells.Element;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MagicProfile
 *
 * Plain value object that carries the elemental stats and spell ids parsed from
 * a mob's yml config file. Passed into {@link MagicEntity#initMagic} during
 * construction so the entity doesn't need to touch the config system itself.
 *
 * ── yml keys expected ────────────────────────────────────────────────────────
 *
 *   fire_affinity:    1.5
 *   fire_resistance: -0.5
 *   # ... one pair per element, plus magic_affinity / magic_resistance
 *
 *   spells:
 *     - 'fireball'
 *     - 'geyser'
 */
public class MagicProfile {

    private final Map<Element, Double> affinities  = new HashMap<>();
    private final Map<Element, Double> resistances = new HashMap<>();
    private final List<String>         spellIds;

    // -------------------------------------------------------------------------
    // Construction from YamlConfiguration
    // -------------------------------------------------------------------------

    public MagicProfile(YamlConfiguration cfg) {
        for (Element element : Element.values()) {
            String key = elementKey(element);
            affinities.put(element,  cfg.getDouble(key + "_affinity",   0.0));
            resistances.put(element, cfg.getDouble(key + "_resistance", 0.0));
        }
        spellIds = cfg.getStringList("spells");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public double getAffinity(Element element) {
        return affinities.getOrDefault(element, 0.0);
    }

    public double getResistance(Element element) {
        return resistances.getOrDefault(element, 0.0);
    }

    public List<String> getSpellIds() {
        return spellIds;
    }

    // -------------------------------------------------------------------------

    private static String elementKey(Element element) {
        return (element == Element.NONE) ? "magic" : element.name().toLowerCase();
    }
}
