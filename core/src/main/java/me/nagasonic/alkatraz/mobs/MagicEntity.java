package me.nagasonic.alkatraz.mobs;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interface implemented by NMS custom mob classes, e.g.:
 *
 *   public class ZombieMage extends Zombie implements MagicEntity { ... }
 *
 * Because Java interfaces cannot hold mutable instance state, affinities,
 * resistances and the spell roster are stored in a {@link MagicData} value
 * object owned by each implementor.
 */
public interface MagicEntity {

    MagicData getMagicData();

    /**
     * Populates affinities, resistances and the spell roster from a
     * {@link MobProfile}, then stamps the values onto the entity's persistent
     * NBT so the rest of the plugin can read them without NMS access.
     *
     * Call this at the end of the NMS entity constructor after {@code super()}
     * has finished.
     */
    default void initMagic(MobProfile profile, MagicEntityType type, LivingEntity self) {
        MagicData data = getMagicData();

        for (Element element : Element.values()) {
            data.affinities.put(element,  profile.getAffinity(element));
            data.resistances.put(element, profile.getResistance(element));
        }

        for (String id : profile.getSpellIds()) {
            Spell spell = SpellRegistry.getSpell(id);
            if (spell != null) {
                data.spells.add(spell);
            }
        }

        NBT.modifyPersistentData(self, nbt -> {
            for (Element element : Element.values()) {
                String key = elementKey(element);
                nbt.setDouble(key + "_affinity",   data.affinities.getOrDefault(element,  0.0));
                nbt.setDouble(key + "_resistance", data.resistances.getOrDefault(element, 0.0));
            }
            nbt.setString("mob_spells", String.join(",",
                    data.spells.stream().map(Spell::getId).toList()));
            nbt.setString(MagicEntityType.NBT_KEY, type.getId());
        });
    }

    default double getAffinity(Element element) {
        return getMagicData().affinities.getOrDefault(element, 0.0);
    }

    default double getResistance(Element element) {
        return getMagicData().resistances.getOrDefault(element, 0.0);
    }

    default List<Spell> getSpells() {
        return Collections.unmodifiableList(getMagicData().spells);
    }

    default Spell pickRandomSpell() {
        List<Spell> spells = getMagicData().spells;
        if (spells.isEmpty()) {
            return null;
        }
        return spells.get((int) (Math.random() * spells.size()));
    }

    private static String elementKey(Element element) {
        return (element == Element.NONE) ? "magic" : element.name().toLowerCase();
    }

    /**
     * Holds the mutable per-instance state that the interface itself cannot store.
     */
    class MagicData {
        final Map<Element, Double> affinities  = new HashMap<>();
        final Map<Element, Double> resistances = new HashMap<>();
        final List<Spell>          spells      = new ArrayList<>();
    }
}
