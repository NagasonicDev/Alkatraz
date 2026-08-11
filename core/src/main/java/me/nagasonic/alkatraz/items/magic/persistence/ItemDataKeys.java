package me.nagasonic.alkatraz.items.magic.persistence;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.NamespacedKey;

/**
 * Top-level PersistentDataContainer keys for magic items.
 * Only two keys are ever written to item meta.
 */
public final class ItemDataKeys {

    private static NamespacedKey itemDefinition;
    private static NamespacedKey itemInstance;
    private static NamespacedKey engraving;
    private static NamespacedKey projectileWeaponInstance;
    private static NamespacedKey itemUuid;

    private ItemDataKeys() {}

    public static void initialize() {
        itemDefinition = new NamespacedKey(Alkatraz.getInstance(), "item_definition");
        itemInstance = new NamespacedKey(Alkatraz.getInstance(), "item_instance");
        engraving = new NamespacedKey(Alkatraz.getInstance(), "engraving");
        projectileWeaponInstance = new NamespacedKey(Alkatraz.getInstance(), "projectile_weapon_instance");
        itemUuid = new NamespacedKey(Alkatraz.getInstance(), "item_uuid");
    }

    public static NamespacedKey itemDefinition() {
        return itemDefinition;
    }

    public static NamespacedKey itemInstance() {
        return itemInstance;
    }

    public static NamespacedKey engraving() {
        return engraving;
    }

    public static NamespacedKey projectileWeaponInstance() {
        return projectileWeaponInstance;
    }

    public static NamespacedKey itemUuid() {
        return itemUuid;
    }
}
