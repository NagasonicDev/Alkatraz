package me.nagasonic.alkatraz.nms.entity;

import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MagicEntity
 *
 * Interface implemented alongside a concrete NMS mob superclass, e.g.:
 *
 *   public class MagicZombie extends Zombie implements MagicEntity { ... }
 *
 * Because Java interfaces cannot hold mutable instance state, affinities,
 * resistances and the spell roster are stored in a small inner {@link MagicData}
 * value object. Each implementing class holds exactly one MagicData field and
 * returns it via {@link #getMagicData()}, which is the only method that must
 * be implemented.
 *
 * All other methods are default and delegate through getMagicData(), so
 * subclasses get the full API for free.
 */
public interface MagicEntity {

    // -------------------------------------------------------------------------
    // Contract — the single method every implementor must provide
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link MagicData} instance owned by this entity.
     * Implementors should create this as a final field and return it here.
     *
     * <pre>
     *   private final MagicData magicData = new MagicData();
     *
     *   {@literal @}Override
     *   public MagicData getMagicData() { return magicData; }
     * </pre>
     */
    MagicData getMagicData();

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Populates affinities, resistances and the spell roster from a
     * {@link MagicProfile}, then stamps the values onto the entity's persistent
     * NBT so the rest of the damage pipeline can read them via
     * {@code MobModifier.getEntityAffinity / getEntityResistance}.
     *
     * Call this at the end of the NMS entity constructor after super() has
     * finished, passing {@code this} as the {@code self} argument.
     *
     * @param profile  data loaded from mobs/&lt;type&gt;.yml
     * @param self     the NMS Mob instance ({@code this} in the subclass)
     */
    default void initMagic(MagicProfile profile, Mob self) {
        MagicData data = getMagicData();

        // Store stats
        for (Element element : Element.values()) {
            data.affinities.put(element,  profile.getAffinity(element));
            data.resistances.put(element, profile.getResistance(element));
        }

        // Resolve spell ids against the registry
        for (String id : profile.getSpellIds()) {
            Spell spell = SpellRegistry.getSpell(id);
            if (spell != null) data.spells.add(spell);
        }

        // Stamp NBT so MobModifier's static helpers still work unchanged
        org.bukkit.entity.LivingEntity bukkit =
                (org.bukkit.entity.LivingEntity) self.getBukkitEntity();

        de.tr7zw.nbtapi.NBT.modifyPersistentData(bukkit, nbt -> {
            for (Element element : Element.values()) {
                String key = elementKey(element);
                nbt.setDouble(key + "_affinity",   data.affinities.getOrDefault(element,  0.0));
                nbt.setDouble(key + "_resistance", data.resistances.getOrDefault(element, 0.0));
            }
            nbt.setString("mob_spells", String.join(",",
                    data.spells.stream().map(Spell::getId).toList()));
        });
    }

    // -------------------------------------------------------------------------
    // Stat accessors — all default, delegate through getMagicData()
    // -------------------------------------------------------------------------

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
        if (spells.isEmpty()) return null;
        return spells.get((int) (Math.random() * spells.size()));
    }

    // -------------------------------------------------------------------------
    // Shared helper
    // -------------------------------------------------------------------------

    private static String elementKey(Element element) {
        return (element == Element.NONE) ? "magic" : element.name().toLowerCase();
    }

    // -------------------------------------------------------------------------
    // MagicData — the instance state carrier
    // -------------------------------------------------------------------------

    /**
     * Holds the mutable per-instance state that the interface itself cannot
     * store. Each implementing class declares exactly one field of this type.
     */
    class MagicData {
        final Map<Element, Double> affinities  = new HashMap<>();
        final Map<Element, Double> resistances = new HashMap<>();
        final List<Spell>          spells      = new ArrayList<>();
    }
}