package me.nagasonic.alkatraz.mob;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.Config;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MobModifier
 *
 * Loads per-mob-type elemental stats from mobs/<entity_type>.yml and stamps
 * them onto entities as persistent NBT the moment they spawn, so every other
 * system (damage calculation, spell targeting, etc.) can read them via
 * {@link #getEntityAffinity} and {@link #getEntityResistance} without touching
 * the config again.
 *
 * ── YAML layout (e.g. mobs/blaze.yml) ───────────────────────────────────────
 *
 *   fire_affinity:    1.5      # takes/deals 50 % more fire damage
 *   fire_resistance:  0.8      # receives 20 % less fire damage
 *   water_affinity:   0.0
 *   water_resistance: -0.5     # negative = weakness; takes 50 % extra water dmg
 *   magic_affinity:   0.2
 *   magic_resistance: 0.0
 *   # ... one key per element + "magic" for NONE-element spells
 *
 *   spells:
 *     - 'fireball'
 *     - 'flame_burst'
 *
 * ── NBT keys written to each entity ─────────────────────────────────────────
 *
 *   <element>_affinity   (double)   — bonus multiplier when the mob uses spells
 *   <element>_resistance (double)   — damage reduction multiplier when hit
 *   mob_spells           (string)   — comma-separated spell ids available to mob
 *
 * ────────────────────────────────────────────────────────────────────────────
 */
public class MobModifier implements Listener {

    // Cache parsed configs so we only hit disk once per entity type
    private final Map<EntityType, MobProfile> profileCache = new HashMap<>();

    // -------------------------------------------------------------------------
    // Spawn Handler
    // -------------------------------------------------------------------------

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        LivingEntity entity = e.getEntity();
        MobProfile profile = getProfile(entity.getType());
        if (profile == null) return; // no config for this mob — leave it vanilla

        applyProfile(entity, profile);
    }

    // -------------------------------------------------------------------------
    // Profile loading
    // -------------------------------------------------------------------------

    /**
     * Returns the cached {@link MobProfile} for this entity type, loading and
     * caching it from disk on the first call. Returns null if no config file
     * exists for this mob type (intentional — most mobs won't have one).
     */
    private MobProfile getProfile(EntityType type) {
        if (profileCache.containsKey(type)) {
            return profileCache.get(type); // may be null if we already confirmed no config
        }

        String path = "mobs/" + type.name().toLowerCase() + ".yml";

        // Save the default file if it ships inside the jar; skip silently if not
        Alkatraz.getInstance().saveResource(path, false);

        Config configOpt = ConfigManager.getConfig(path);
        if (configOpt == null) {
            profileCache.put(type, null);
            return null;
        }

        YamlConfiguration cfg = configOpt.get();
        MobProfile profile = new MobProfile();

        // Read affinity + resistance for every element plus the generic NONE/magic key
        for (Element element : Element.values()) {
            String key = elementKey(element);
            profile.affinities.put(element,   cfg.getDouble(key + "_affinity",   0.0));
            profile.resistances.put(element,  cfg.getDouble(key + "_resistance", 0.0));
        }

        // Spell list — ids that match registered spells; validated at cast time
        profile.spellIds = cfg.getStringList("spells");

        profileCache.put(type, profile);
        return profile;
    }

    // -------------------------------------------------------------------------
    // NBT application
    // -------------------------------------------------------------------------

    /**
     * Writes all profile values onto the entity's persistent NBT so every
     * downstream system can read them without touching the config.
     */
    private void applyProfile(LivingEntity entity, MobProfile profile) {
        NBT.modifyPersistentData(entity, nbt -> {
            for (Element element : Element.values()) {
                String key = elementKey(element);
                int player_count = 0;
                for (Entity e : entity.getLocation().getNearbyLivingEntities(50)) {
                    if (e instanceof Player p) {
                        player_count += 1;
                    }
                }
                nbt.setDouble(key + "_affinity",   profile.affinities.getOrDefault(element,  0.0) * (player_count != 0 ? player_count : 1));
                nbt.setDouble(key + "_resistance", profile.resistances.getOrDefault(element, 0.0) * (player_count != 0 ? player_count : 1));
            }

            // Spell ids stored as a comma-separated string — simple and NBT-friendly
            nbt.setString("mob_spells", String.join(",", profile.spellIds));
        });
    }

    // -------------------------------------------------------------------------
    // Static NBT Readers  (called by damage pipeline etc.)
    // -------------------------------------------------------------------------

    /**
     * Returns the list of spell ids that this mob is allowed to cast.
     * Returns an empty list for unmodified mobs.
     */
    public static List<String> getMobSpellIds(LivingEntity entity) {
        String raw = NBT.get(entity, nbt -> (String) nbt.getString("mob_spells"));
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (String id : raw.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) ids.add(trimmed);
        }
        return ids;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Converts an Element to its config/NBT key prefix. */
    private static String elementKey(Element element) {
        return (element == Element.NONE) ? "magic" : element.name().toLowerCase();
    }

    // -------------------------------------------------------------------------
    // Profile cache entry
    // -------------------------------------------------------------------------

    private static class MobProfile {
        final Map<Element, Double> affinities  = new HashMap<>();
        final Map<Element, Double> resistances = new HashMap<>();
        List<String> spellIds = new ArrayList<>();
    }
}
