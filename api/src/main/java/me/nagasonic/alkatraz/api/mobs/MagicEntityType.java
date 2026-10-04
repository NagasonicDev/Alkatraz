package me.nagasonic.alkatraz.api.mobs;

import java.util.Optional;

/**
 * Canonical registry of custom magic mob types. Each constant maps to a config
 * file at {@code mobs/<id>.yml} and is stamped onto spawned entities as NBT so
 * they can be identified from any module without NMS access.
 */
public enum MagicEntityType {
    ZOMBIE_MAGE("zombie_mage"),
    ZOMBIE_FIGHTER("zombie_fighter"),
    SKELETAL_MAGE("skeletal_mage"),
    CINDERBOUND_HEXER("cinderbound_hexer"),
    TIDEGLASS_ACOLYTE("tideglass_acolyte"),
    RUNEBOUND_WARDEN("runebound_warden"),
    GALEHOOK_MARAUDER("galehook_marauder"),
    LANTERNLESS_PENITENT("lanternless_penitent");

    public static final String NBT_KEY = "magic_entity_type";

    private static final MagicEntityType[] VALUES = values();

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
        for (MagicEntityType type : VALUES) {
            if (type.id.equals(id)) {
                return Optional.of(type);
            }
        }
        for (MagicEntityType type : VALUES) {
            if (type.id.equalsIgnoreCase(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
