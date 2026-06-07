package me.nagasonic.alkatraz.nms.entity;

import me.nagasonic.alkatraz.nms.entity.implementation.ZombieMage;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Optional;

/**
 * MagicEntitySpawner
 *
 * Convenience utility for spawning magic mobs by string key. Dispatches
 * directly to each mob class's own static {@code spawn(Location)} method,
 * so there is no factory abstraction to maintain — adding a new mob means
 * adding one case here and implementing {@code spawn()} on the class itself.
 *
 * Usage
 * ─────
 *   MagicEntitySpawner.spawn("magic_zombie", player.getLocation());
 */
public final class MagicEntitySpawner {

    private MagicEntitySpawner() {}

    /**
     * Spawns a magic mob by registry key at the given location.
     *
     * @param key      registry key, e.g. {@code "magic_zombie"}
     * @param location target location (world must be loaded)
     * @return the spawned Bukkit entity, or empty if the key is unknown
     */
    public static Optional<Entity> spawn(String key, Location location) {
        return switch (key) {
            case "magic_zombie" -> Optional.of((Entity) ZombieMage.spawn(location));
            // case "magic_skeleton" -> Optional.of(MagicSkeleton.spawn(location));
            // case "magic_witch"    -> Optional.of(MagicWitch.spawn(location));
            default -> Optional.empty();
        };
    }
}