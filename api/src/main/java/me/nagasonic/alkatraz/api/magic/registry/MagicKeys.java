package me.nagasonic.alkatraz.api.magic.registry;

import org.bukkit.NamespacedKey;

import java.util.Locale;
import java.util.Optional;

public final class MagicKeys {

    private static final String NAMESPACE = "alkatraz";

    private MagicKeys() {}

    public static NamespacedKey alkatraz(String path) {
        return new NamespacedKey(NAMESPACE, path);
    }

    public static Optional<NamespacedKey> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        int separator = trimmed.indexOf(':');
        if (separator >= 0) {
            String ns = trimmed.substring(0, separator);
            String key = trimmed.substring(separator + 1);
            if (ns.isBlank() || key.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new NamespacedKey(ns.toLowerCase(Locale.ROOT), key.toLowerCase(Locale.ROOT)));
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
