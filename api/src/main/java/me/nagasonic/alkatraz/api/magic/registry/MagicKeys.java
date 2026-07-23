package me.nagasonic.alkatraz.api.magic.registry;

import org.bukkit.NamespacedKey;

import java.util.Locale;
import java.util.Optional;

/**
 * Utility class for creating and parsing {@link NamespacedKey} instances
 * within the {@code alkatraz} namespace.
 */
public final class MagicKeys {

    private static final String NAMESPACE = "alkatraz";

    private MagicKeys() {}

    /**
     * Creates a new {@link NamespacedKey} in the {@code alkatraz} namespace.
     *
     * @param path the key path; must not be blank
     * @return a new {@link NamespacedKey} under the {@code alkatraz} namespace
     */
    public static NamespacedKey alkatraz(String path) {
        return new NamespacedKey(NAMESPACE, path);
    }

    /**
     * Parses a raw string into an {@link Optional} {@link NamespacedKey}.
     * If the string contains a colon separator, the part before it is used as the namespace
     * and the part after as the key. Otherwise, the key is placed in the {@code alkatraz} namespace.
     * Both namespace and key are lowercased.
     *
     * @param raw the raw string to parse, or {@code null}
     * @return an {@link Optional} containing the parsed key, or empty if the input is invalid
     */
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

    /**
     * Parses a raw string into a {@link NamespacedKey}, throwing an exception if parsing fails.
     *
     * @param raw the raw string to parse
     * @return the parsed {@link NamespacedKey}
     * @throws IllegalArgumentException if the input cannot be parsed into a valid key
     */
    public static NamespacedKey require(String raw) {
        return parse(raw).orElseThrow(() ->
                new IllegalArgumentException("Invalid namespaced key: " + raw));
    }

    /**
     * Formats a {@link NamespacedKey} back into its {@code namespace:key} string representation.
     *
     * @param key the key to format
     * @return the formatted string in {@code namespace:key} format
     */
    public static String format(NamespacedKey key) {
        return key.getNamespace() + ":" + key.getKey();
    }
}
