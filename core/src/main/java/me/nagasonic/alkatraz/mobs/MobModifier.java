package me.nagasonic.alkatraz.mobs;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.Config;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.spells.Element;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads per-mob-type elemental stats from {@code mobs/<entity_type>.yml} and
 * stamps them onto <em>vanilla</em> entities as persistent NBT the moment they
 * spawn, so every downstream system (damage calculation, spell targeting, etc.)
 * can read affinities and resistances via {@link #getEntityAffinity} and
 * {@link #getEntityResistance} without touching config again.
 *
 * <p>Magic entities ({@link MagicEntities#isMagicEntity}) are skipped here
 * because their stats are written by {@link MagicEntity#initMagic} during NMS
 * construction.
 *
 * <h3>NBT keys written</h3>
 * <pre>
 *   &lt;element&gt;_affinity    double  bonus multiplier when the mob casts spells
 *   &lt;element&gt;_resistance  double  damage reduction when the mob is hit
 *   mob_spells            String  comma-separated spell ids
 * </pre>
 *
 * <h3>Player-count scaling</h3>
 * Affinities and resistances are multiplied by the number of players within
 * 50 blocks at spawn time (minimum multiplier of 1). This makes vanilla mobs
 * progressively harder in crowded areas.
 */
public class MobModifier implements Listener {

    /** Cache parsed configs so we only hit disk once per entity type per session. */
    private final Map<EntityType, MobProfile> profileCache = new HashMap<>();

    // -------------------------------------------------------------------------
    // Spawn handler
    // -------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        // Magic entities stamp their own NBT in NmsMagicZombie / NmsMagicSkeleton.
        if (MagicEntities.isMagicEntity(entity)) {
            return;
        }

        MobProfile profile = getProfile(entity.getType());
        if (profile == null) {
            return; // no config for this mob type — leave it vanilla
        }

        applyProfile(entity, profile);
    }

    // -------------------------------------------------------------------------
    // Profile loading
    // -------------------------------------------------------------------------

    /**
     * Returns the cached {@link MobProfile} for this entity type, loading it
     * from disk on the first call. Returns {@code null} if no config file exists
     * (intentional — most mob types won't have one).
     */
    private MobProfile getProfile(EntityType type) {
        if (profileCache.containsKey(type)) {
            return profileCache.get(type);
        }

        String path = "mobs/" + type.name().toLowerCase() + ".yml";

        if (!Alkatraz.getPluginConfig().contains(path)) {
            profileCache.put(type, null);
            return null;
        }

        Config configOpt = ConfigManager.getConfig(path);
        if (configOpt == null) {
            profileCache.put(type, null);
            return null;
        }

        YamlConfiguration cfg = configOpt.get();
        MobProfile profile = new MobProfile();

        for (Element element : Element.values()) {
            String key = elementKey(element);
            profile.affinities.put(element,  cfg.getDouble(key + "_affinity",   0.0));
            profile.resistances.put(element, cfg.getDouble(key + "_resistance", 0.0));
        }
        profile.spellIds = cfg.getStringList("spells");

        profileCache.put(type, profile);
        return profile;
    }

    // -------------------------------------------------------------------------
    // NBT application
    // -------------------------------------------------------------------------

    /**
     * Writes all profile values onto the entity's persistent NBT.
     *
     * <p>The nearby-player count is computed <em>before</em> entering the NBT
     * lambda (NBT lambdas run synchronously but must not call Bukkit world
     * queries for clarity and future safety).
     */
    private void applyProfile(LivingEntity entity, MobProfile profile) {
        // Count nearby players outside the NBT lambda — cleaner and safer.
        Collection<LivingEntity> nearby = entity.getLocation().getNearbyLivingEntities(50);
        int playerCount = 0;
        for (Entity e : nearby) {
            if (e instanceof Player) {
                playerCount++;
            }
        }
        final double scaleFactor = Math.max(1, playerCount);

        NBT.modifyPersistentData(entity, nbt -> {
            for (Element element : Element.values()) {
                String key = elementKey(element);
                nbt.setDouble(key + "_affinity",
                        profile.affinities.getOrDefault(element, 0.0) * scaleFactor);
                nbt.setDouble(key + "_resistance",
                        profile.resistances.getOrDefault(element, 0.0) * scaleFactor);
            }
            nbt.setString("mob_spells", String.join(",", profile.spellIds));
        });
    }

    // -------------------------------------------------------------------------
    // Static NBT readers  (called by damage pipeline etc.)
    // -------------------------------------------------------------------------

    /**
     * Returns the list of spell ids this mob is allowed to cast.
     * Returns an empty list for unmodified mobs.
     */
    public static List<String> getMobSpellIds(LivingEntity entity) {
        String raw = NBT.get(entity, nbt -> (String) nbt.getString("mob_spells"));
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (String id : raw.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                ids.add(trimmed);
            }
        }
        return ids;
    }

    /** Reads a stored affinity value directly from NBT. */
    public static double getEntityAffinity(Element element, LivingEntity entity) {
        String key = elementKey(element) + "_affinity";
        Double val = NBT.get(entity, nbt -> (Double) nbt.getDouble(key));
        return val != null ? val : 0.0;
    }

    /** Reads a stored resistance value directly from NBT. */
    public static double getEntityResistance(Element element, LivingEntity entity) {
        String key = elementKey(element) + "_resistance";
        Double val = NBT.get(entity, nbt -> (Double) nbt.getDouble(key));
        return val != null ? val : 0.0;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
