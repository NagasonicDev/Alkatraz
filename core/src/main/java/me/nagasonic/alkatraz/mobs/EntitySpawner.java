package me.nagasonic.alkatraz.mobs;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Optional;

/**
 * NMS-facing spawn contract. Each version module provides an implementation that
 * maps {@link MagicEntityType} ids to concrete entity classes.
 */
public interface EntitySpawner {

    Optional<Entity> spawn(String key, Location location);

    default Optional<Entity> spawn(MagicEntityType type, Location location) {
        return spawn(type.getId(), location);
    }
}
