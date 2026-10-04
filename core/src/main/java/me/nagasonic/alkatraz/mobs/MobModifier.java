package me.nagasonic.alkatraz.mobs;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.Config;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.api.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
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

    /** Half-extent of the spawn box used for the nearby-player count. */
    private static final double SPAWN_SCAN_RADIUS = 50;

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
        final double scaleFactor = Math.max(1, countNearbyPlayers(entity));

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

    /**
     * Counts players inside the spawn box. This reproduces the old
     * {@code Utils.getNearbyLivingEntities(location, 50)} filter exactly — same
     * world, and the same axis-aligned 100x100x100 box (a box, not a sphere,
     * which is what {@code CraftWorld.getNearbyEntities(Location, x, y, z)} runs)
     * — but walks the online player list instead of every chunk section in the
     * box and materialising every entity inside it.
     *
     * <p>{@code Entity.getX()} only exists from the 1.20 API onwards, so one
     * {@code Location} per candidate is the floor on this 1.19 baseline.
     */
    private static int countNearbyPlayers(LivingEntity entity) {
        World world = entity.getWorld();
        if (world == null) return 0;
        Location origin = entity.getLocation();
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(world)) continue;
            Location at = player.getLocation();
            if (Math.abs(at.getX() - origin.getX()) > SPAWN_SCAN_RADIUS) continue;
            if (Math.abs(at.getY() - origin.getY()) > SPAWN_SCAN_RADIUS) continue;
            if (Math.abs(at.getZ() - origin.getZ()) > SPAWN_SCAN_RADIUS) continue;
            count++;
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Static NBT readers  (called by damage pipeline etc.)
    // -------------------------------------------------------------------------

    /**
     * Returns the list of spell ids this mob is allowed to cast.
     * Returns an empty list for unmodified mobs.
     */
    public static List<String> getMobSpellIds(LivingEntity entity) {
        String raw = NBT.getPersistentData(entity, nbt -> nbt.getString("mob_spells"));
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
        Double val = NBT.getPersistentData(entity, nbt -> nbt.getDouble(key));
        return val != null ? val : 0.0;
    }

    /** Reads a stored resistance value directly from NBT. */
    public static double getEntityResistance(Element element, LivingEntity entity) {
        String key = elementKey(element) + "_resistance";
        Double val = NBT.getPersistentData(entity, nbt -> nbt.getDouble(key));
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
