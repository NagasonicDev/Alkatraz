package me.nagasonic.alkatraz.nms.entity;

import me.nagasonic.alkatraz.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.NmsMobFactory;
import me.nagasonic.alkatraz.nms.entity.implementation.SkeletalMage;
import me.nagasonic.alkatraz.nms.entity.implementation.ZombieFighter;
import me.nagasonic.alkatraz.nms.entity.implementation.ZombieMage;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Optional;

public class MagicEntitySpawner implements NmsMobFactory {

    public static final MagicEntitySpawner INSTANCE = new MagicEntitySpawner();

    private MagicEntitySpawner() {}

    @Override
    public Optional<Entity> spawnMagicEntity(String id, Location location) {
        return MagicEntityType.fromId(id)
                .flatMap(type -> spawnByType(type, location));
    }

    private Optional<Entity> spawnByType(MagicEntityType type, Location location) {
        return switch (type) {
            case ZOMBIE_MAGE    -> Optional.of(ZombieMage.spawn(location).getBukkitEntity());
            case ZOMBIE_FIGHTER -> Optional.of(ZombieFighter.spawn(location).getBukkitEntity());
            case SKELETAL_MAGE  -> Optional.of(SkeletalMage.spawn(location).getBukkitEntity());
        };
    }
}
