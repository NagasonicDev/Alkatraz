package me.nagasonic.alkatraz.api.magic.registry;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MagicKeysTest {

    @Test
    void bareKeyIsPrefixedWithAlkatrazNamespace() {
        NamespacedKey key = MagicKeys.alkatraz("fire_affinity");
        assertEquals("alkatraz", key.getNamespace());
        assertEquals("fire_affinity", key.getKey());
    }

    @Test
    void alreadyNamespacedKeyIsNotDoublePrefixed() {
        NamespacedKey key = MagicKeys.alkatraz("alkatraz:fire_affinity");
        assertEquals("alkatraz", key.getNamespace());
        assertEquals("fire_affinity", key.getKey());
    }

    @Test
    void foreignNamespacedKeyIsPreserved() {
        NamespacedKey key = MagicKeys.alkatraz("minecraft:stone");
        assertEquals("minecraft", key.getNamespace());
        assertEquals("stone", key.getKey());
    }

    @Test
    void blankKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MagicKeys.alkatraz("  "));
    }
}
