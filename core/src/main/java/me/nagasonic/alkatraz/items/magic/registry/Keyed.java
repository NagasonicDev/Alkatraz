package me.nagasonic.alkatraz.items.magic.registry;

import org.bukkit.NamespacedKey;

/**
 * Common contract for all registry-backed, namespaced Alkatraz objects.
 */
public interface Keyed {

    NamespacedKey getKey();
}
