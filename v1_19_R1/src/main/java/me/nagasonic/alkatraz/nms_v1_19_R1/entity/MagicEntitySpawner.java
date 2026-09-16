package me.nagasonic.alkatraz.nms_v1_19_R1.entity;

import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.api.mobs.NmsMobFactory;
import me.nagasonic.alkatraz.nms_v1_19_R1.entity.definitions.NMSMagicSkeleton;
import me.nagasonic.alkatraz.nms_v1_19_R1.entity.definitions.NMSMagicZombie;
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
            case ZOMBIE_MAGE, ZOMBIE_FIGHTER -> Optional.of(NMSMagicZombie.spawn(type, location).getBukkitEntity());
            case SKELETAL_MAGE -> Optional.of(NMSMagicSkeleton.spawn(type, location).getBukkitEntity());
        };
    }
}