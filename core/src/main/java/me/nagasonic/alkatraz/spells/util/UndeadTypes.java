package me.nagasonic.alkatraz.spells.util;

import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The undead roster shared by {@code radiance} and light's anti-dark
 * amplification. Promoted verbatim from the private set in Radiance so light
 * spells can reuse it rather than duplicating the list. Note the roster
 * deliberately contains no ENDERMAN, SILVERFISH or GHAST, and does contain
 * ZOGLIN, SKELETON_HORSE and ZOMBIE_HORSE - that is what the shipped source says.
 */
public final class UndeadTypes {

    private static final Set<EntityType> UNDEAD = Collections.unmodifiableSet(EnumSet.of(
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.SKELETON,
            EntityType.WITHER_SKELETON,
            EntityType.STRAY,
            EntityType.WITHER,
            EntityType.PHANTOM,
            EntityType.ZOGLIN,
            EntityType.ZOMBIFIED_PIGLIN,
            EntityType.SKELETON_HORSE,
            EntityType.ZOMBIE_HORSE));

    private UndeadTypes() {
    }

    /**
     * Reports whether an entity type is on the undead roster.
     *
     * @param type the entity type to test, may be null
     * @return true when the type is undead; false for null and everything else
     */
    public static boolean contains(EntityType type) {
        return type != null && UNDEAD.contains(type);
    }

    /**
     * The full undead roster, in the order it was declared in Radiance.
     *
     * @return an unmodifiable set of the 13 undead entity types
     */
    public static Set<EntityType> all() {
        return UNDEAD;
    }
}