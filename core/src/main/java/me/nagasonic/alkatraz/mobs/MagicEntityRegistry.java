package me.nagasonic.alkatraz.mobs;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.Config;
import me.nagasonic.alkatraz.config.ConfigManager;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Caches {@link MobProfile} instances so each magic mob's yml is only parsed
 * once per server lifetime. NMS entity constructors retrieve profiles via
 * {@link #getProfile(MagicEntityType)}.
 */
public final class MagicEntityRegistry {

    private static final Map<String, MobProfile> profileCache = new HashMap<>();
    private static final Map<EntityType, List<SpawnReplacement>> replacementsByBaseType = new HashMap<>();

    private MagicEntityRegistry() {}

    /** Call once from plugin startup after the config system is ready. */
    public static void registerAll() {
        Alkatraz.logInfo("Registering magic mob profiles");
        profileCache.clear();
        replacementsByBaseType.clear();
        int count = 0;
        for (MagicEntityType type : MagicEntityType.values()) {
            register(type);
            count++;
        }
        Alkatraz.logInfo("Loaded " + count + " magic mob type(s) into registry");
        Alkatraz.logHigh("Total cached profiles: " + profileCache.size());
        Alkatraz.logVeryHigh("Replacement base types: " + replacementsByBaseType.size());
        for (var entry : replacementsByBaseType.entrySet()) {
            Alkatraz.logVeryHigh("  " + entry.getKey() + " -> " + entry.getValue().size() + " replacement(s)");
        }
    }

    private static void register(MagicEntityType type) {
        Config cfg = ConfigManager.getConfig(type.getConfigPath());
        if (cfg == null) {
            Alkatraz.logWarning("No config found for magic mob '" + type.getId() + "' at " + type.getConfigPath());
            return;
        }
        MobProfile profile = new MobProfile(cfg.get());
        profileCache.put(type.getId(), profile);
        Alkatraz.logHigh("Registered profile '" + type.getId() + "' (" + profile.getReplaces() + ")");
        if (profile.canReplaceNaturally()) {
            replacementsByBaseType
                    .computeIfAbsent(profile.getReplaces(), ignored -> new ArrayList<>())
                    .add(new SpawnReplacement(type, profile.getSpawnChance(), profile.getSpawnReasons()));
            Alkatraz.logVeryHigh("  Natural replacement: " + profile.getReplaces()
                    + " @ chance=" + profile.getSpawnChance()
                    + " reasons=" + profile.getSpawnReasons());
        } else {
            Alkatraz.logVeryHigh("  No natural replacement configured");
        }
    }

    /**
     * Rolls the weighted replacement pool for a vanilla spawn. Returns empty if
     * the spawn should remain vanilla.
     */
    public static Optional<MagicEntityType> rollReplacement(
            EntityType baseType,
            CreatureSpawnEvent.SpawnReason spawnReason
    ) {
        Alkatraz.logHigh("Rolling for replacement: " + baseType + " reason=" + spawnReason);
        List<SpawnReplacement> candidates = replacementsByBaseType.get(baseType);
        if (candidates == null || candidates.isEmpty()) {
            Alkatraz.logVeryHigh("No candidates for " + baseType);
            return Optional.empty();
        }

        List<SpawnReplacement> eligible = new ArrayList<>();
        double totalChance = 0.0;
        for (SpawnReplacement candidate : candidates) {
            if (!candidate.spawnReasons().contains(spawnReason)) {
                Alkatraz.logVeryHigh("  skip " + candidate.type().getId() + " (wrong reason)");
                continue;
            }
            eligible.add(candidate);
            totalChance += candidate.chance();
            Alkatraz.logVeryHigh("  eligible " + candidate.type().getId() + " chance=" + candidate.chance());
        }

        if (eligible.isEmpty() || totalChance <= 0.0) {
            Alkatraz.logHigh("No eligible replacements for " + baseType + " (totalChance=" + totalChance + ")");
            return Optional.empty();
        }

        double roll = ThreadLocalRandom.current().nextDouble();
        Alkatraz.logHigh("Roll for " + baseType + ": totalChance=" + totalChance + " roll=" + roll);
        if (roll >= totalChance) {
            Alkatraz.logHigh("  roll failed");
            return Optional.empty();
        }

        double cumulative = 0.0;
        for (SpawnReplacement candidate : eligible) {
            cumulative += candidate.chance();
            if (roll < cumulative) {
                Alkatraz.logHigh("  selected: " + candidate.type().getId());
                return Optional.of(candidate.type());
            }
        }
        return Optional.empty();
    }

    public static Optional<MobProfile> getProfile(MagicEntityType type) {
        return Optional.ofNullable(profileCache.get(type.getId()));
    }

    public static Optional<MobProfile> getProfile(String id) {
        return MagicEntityType.fromId(id).flatMap(MagicEntityRegistry::getProfile);
    }

    private record SpawnReplacement(
            MagicEntityType type,
            double chance,
            java.util.Set<CreatureSpawnEvent.SpawnReason> spawnReasons
    ) {}
}
