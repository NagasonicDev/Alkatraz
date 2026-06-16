package me.nagasonic.alkatraz.items.magic.registry;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.NamespacedKey;

import java.util.Locale;
import java.util.Optional;

/**
 * Central helpers for parsing and constructing {@link NamespacedKey} values.
 */
public final class MagicKeys {

    private MagicKeys() {}

    public static NamespacedKey alkatraz(String path) {
        return new NamespacedKey(Alkatraz.getInstance(), path);
    }

    public static Optional<NamespacedKey> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        int separator = trimmed.indexOf(':');
        if (separator >= 0) {
            String namespace = trimmed.substring(0, separator);
            String key = trimmed.substring(separator + 1);
            if (namespace.isBlank() || key.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new NamespacedKey(namespace.toLowerCase(Locale.ROOT), key.toLowerCase(Locale.ROOT)));
        }
        return Optional.of(alkatraz(trimmed.toLowerCase(Locale.ROOT)));
    }

    public static NamespacedKey require(String raw) {
        return parse(raw).orElseThrow(() ->
                new IllegalArgumentException("Invalid namespaced key: " + raw));
    }

    public static String format(NamespacedKey key) {
        return key.getNamespace() + ":" + key.getKey();
    }
}
