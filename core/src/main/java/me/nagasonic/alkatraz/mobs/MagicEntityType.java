package me.nagasonic.alkatraz.mobs;

import java.util.Optional;

/**
 * Canonical registry of custom magic mob types. Each constant maps to a config
 * file at {@code mobs/<id>.yml} and is stamped onto spawned entities as NBT so
 * they can be identified from any module without NMS access.
 */
public enum MagicEntityType {
    ZOMBIE_MAGE("zombie_mage"),
    ZOMBIE_FIGHTER("zombie_fighter"),
    SKELETAL_MAGE("skeletal_mage");

    public static final String NBT_KEY = "magic_entity_type";

    private final String id;

    MagicEntityType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getConfigPath() {
        return "mobs/" + id + ".yml";
    }

    public static Optional<MagicEntityType> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        for (MagicEntityType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
