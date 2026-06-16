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

    private ItemDataKeys() {}

    public static void initialize() {
        itemDefinition = new NamespacedKey(Alkatraz.getInstance(), "item_definition");
        itemInstance = new NamespacedKey(Alkatraz.getInstance(), "item_instance");
    }

    public static NamespacedKey itemDefinition() {
        return itemDefinition;
    }

    public static NamespacedKey itemInstance() {
        return itemInstance;
    }
}
