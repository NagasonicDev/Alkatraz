package me.nagasonic.alkatraz.api.magic.registry;

import org.bukkit.NamespacedKey;

/**
 * A registry entry that can be identified and looked up by its {@link NamespacedKey}.
 */
public interface Keyed {

    /**
     * Returns the unique {@link NamespacedKey} that identifies this entry within a {@link Registry}.
     *
     * @return the namespaced key of this entry
     */
    NamespacedKey getKey();
}
