package me.nagasonic.alkatraz.api.magic.registry;

import org.bukkit.NamespacedKey;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A generic, thread-safe registry that stores {@link Keyed} entries indexed by {@link NamespacedKey}.
 * Uses volatile immutable map snapshots for safe concurrent reads without locking.
 *
 * @param <T> the type of entry stored in this registry; must implement {@link Keyed}
 */
public final class Registry<T extends Keyed> {

    private volatile Map<NamespacedKey, T> entries = Map.of();

    /**
     * Registers a single entry. The entry and its key must not be null.
     *
     * @param entry the entry to register
     * @throws IllegalArgumentException if the entry or its key is null
     * @throws IllegalStateException if an entry with the same key is already registered
     */
    public void register(T entry) {
        if (entry == null || entry.getKey() == null) {
            throw new IllegalArgumentException("Registry entry and its key must not be null");
        }
        Map<NamespacedKey, T> next = new HashMap<>(entries);
        if (next.putIfAbsent(entry.getKey(), entry) != null) {
            throw new IllegalStateException("Duplicate registry key: " + entry.getKey());
        }
        entries = Collections.unmodifiableMap(next);
    }

    /**
     * Registers all entries in the given collection.
     *
     * @param values the collection of entries to register
     */
    public void registerAll(Collection<T> values) {
        for (T value : values) {
            register(value);
        }
    }

    /**
     * Looks up an entry by its {@link NamespacedKey}.
     *
     * @param key the key to look up, or {@code null}
     * @return an {@link Optional} containing the entry, or empty if not found or key is null
     */
    public Optional<T> get(NamespacedKey key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(key));
    }

    /**
     * Looks up an entry by parsing a raw key string.
     *
     * @param keyString the raw key string to parse and look up
     * @return an {@link Optional} containing the entry, or empty if parsing or lookup fails
     */
    public Optional<T> get(String keyString) {
        return MagicKeys.parse(keyString).flatMap(this::get);
    }

    /**
     * Looks up an entry by its {@link NamespacedKey}, throwing if not found.
     *
     * @param key the key to look up
     * @return the entry
     * @throws IllegalArgumentException if no entry is registered for the given key
     */
    public T require(NamespacedKey key) {
        return get(key).orElseThrow(() ->
                new IllegalArgumentException("No registry entry for key: " + key));
    }

    /**
     * Looks up an entry by parsing a raw key string, throwing if not found.
     *
     * @param keyString the raw key string to parse and look up
     * @return the entry
     * @throws IllegalArgumentException if parsing or lookup fails
     */
    public T require(String keyString) {
        return get(keyString).orElseThrow(() ->
                new IllegalArgumentException("No registry entry for key: " + keyString));
    }

    /**
     * Checks whether an entry is registered under the given key.
     *
     * @param key the key to check
     * @return {@code true} if an entry is registered for the key
     */
    public boolean isRegistered(NamespacedKey key) {
        return key != null && entries.containsKey(key);
    }

    /**
     * Returns all registered entries as a collection.
     *
     * @return an unmodifiable collection of all registered entries
     */
    public Collection<T> values() {
        return entries.values();
    }

    /**
     * Returns the underlying key-to-entry map as an unmodifiable snapshot.
     *
     * @return an unmodifiable map of all registered entries
     */
    public Map<NamespacedKey, T> asMap() {
        return entries;
    }

    /**
     * Returns all registered keys.
     *
     * @return an unmodifiable set of all registered {@link NamespacedKey} values
     */
    public Set<NamespacedKey> keySet() {
        return entries.keySet();
    }

    /**
     * Removes all entries from this registry.
     */
    public void clear() {
        entries = Map.of();
    }
}
