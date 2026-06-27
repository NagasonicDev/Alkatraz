package me.nagasonic.alkatraz.mobs;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Optional;

/**
 * Contract that each NMS version module must implement. The core plugin
 * discovers and stores exactly one instance (via {@code Alkatraz.getNms()})
 * and delegates all entity-spawning through it.
 *
 * <p>Implementations live in the version-specific module and are the only
 * place in the project that may import NMS or CraftBukkit internals for
 * spawning purposes.
 *
 * <p>Example registration in the version module's entry point:
 * <pre>
 *   Alkatraz.setNms(new V1_21_R7MobFactory());
 * </pre>
 */
public interface NmsMobFactory {

    /**
     * Spawns the magic mob identified by {@code id} at the given location.
     *
     * @param id       the {@link MagicEntityType#getId()} string
     * @param location the target location; the world must be loaded
     * @return the spawned entity, or empty if the id is unknown or spawning fails
     */
    Optional<Entity> spawnMagicEntity(String id, Location location);
}
