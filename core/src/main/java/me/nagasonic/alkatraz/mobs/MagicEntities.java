package me.nagasonic.alkatraz.mobs;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Version-agnostic entry point for custom magic mobs.
 *
 * <p>Spawning delegates to the active {@link NmsMobFactory} registered via
 * {@link Alkatraz#setNms}. All stat and spell lookups read the persistent NBT
 * stamped onto entities at construction time, so callers never need NMS access.
 *
 * <pre>
 *   MagicEntities.spawn(MagicEntityType.ZOMBIE_MAGE, player.getLocation());
 *   MagicEntities.getType(entity).ifPresent(type -> ...);
 *   List&lt;Spell&gt; spells = MagicEntities.getSpells(entity);
 * </pre>
 */
public final class MagicEntities {

    /**
     * Resolved spell lists, keyed weakly on the entity so entries vanish with it.
     * {@code mob_spells} is written once at construction and never mutated, so the
     * resolution is stable for the entity's whole life; without this the NBT read
     * plus id split plus stream pipeline ran on every goal evaluation.
     */
    private static final Map<LivingEntity, List<Spell>> SPELL_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MagicEntities() {}

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Loads and caches all magic mob profiles from disk. Call once at startup. */
    public static void registerProfiles() {
        MagicEntityRegistry.registerAll();
        MagicBrains.registerAll();
    }

    // -------------------------------------------------------------------------
    // Spawning
    // -------------------------------------------------------------------------

    public static Optional<LivingEntity> spawn(MagicEntityType type, Location location) {
        Objects.requireNonNull(type,     "type");
        Objects.requireNonNull(location, "location");
        return Alkatraz.getNms()
                .spawnMagicEntity(type.getId(), location)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast);
    }

    public static Optional<LivingEntity> spawn(String id, Location location) {
        return MagicEntityType.fromId(id)
                .flatMap(type -> spawn(type, location));
    }

    // -------------------------------------------------------------------------
    // Identity queries
    // -------------------------------------------------------------------------

    public static boolean isMagicEntity(Entity entity) {
        return entity instanceof LivingEntity living && getType(living).isPresent();
    }

    public static Optional<MagicEntityType> getType(LivingEntity entity) {
        String id = NBT.getPersistentData(entity, nbt -> nbt.getString(MagicEntityType.NBT_KEY));
        return MagicEntityType.fromId(id);
    }

    // -------------------------------------------------------------------------
    // Spell access
    // -------------------------------------------------------------------------

    public static List<String> getSpellIds(LivingEntity entity) {
        return MobModifier.getMobSpellIds(entity);
    }

    public static List<Spell> getSpells(LivingEntity entity) {
        List<Spell> cached = SPELL_CACHE.get(entity);
        if (cached != null) return cached;
        List<Spell> resolved = resolveSpells(entity);
        SPELL_CACHE.put(entity, resolved);
        return resolved;
    }

    private static List<Spell> resolveSpells(LivingEntity entity) {
        List<String> ids = getSpellIds(entity);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Spell> spells = new ArrayList<>(ids.size());
        for (String id : ids) {
            Spell spell = SpellRegistry.getSpell(id);
            if (spell != null) spells.add(spell);
        }
        if (spells.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(spells);
    }

    // -------------------------------------------------------------------------
    // Elemental stats
    // -------------------------------------------------------------------------

    public static double getAffinity(Element element, LivingEntity entity) {
        return Utils.getEntityAffinity(element, entity);
    }

    public static double getResistance(Element element, LivingEntity entity) {
        return Utils.getEntityResistance(element, entity);
    }
}
