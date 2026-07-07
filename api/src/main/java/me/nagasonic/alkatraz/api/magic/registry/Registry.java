package me.nagasonic.alkatraz.api.magic.registry;

import org.bukkit.NamespacedKey;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Registry<T extends Keyed> {

    private volatile Map<NamespacedKey, T> entries = Map.of();

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

    public void registerAll(Collection<T> values) {
        for (T value : values) {
            register(value);
        }
    }

    public Optional<T> get(NamespacedKey key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(key));
    }

    public Optional<T> get(String keyString) {
        return MagicKeys.parse(keyString).flatMap(this::get);
    }

    public T require(NamespacedKey key) {
        return get(key).orElseThrow(() ->
                new IllegalArgumentException("No registry entry for key: " + key));
    }

    public T require(String keyString) {
        return get(keyString).orElseThrow(() ->
                new IllegalArgumentException("No registry entry for key: " + keyString));
    }

    public boolean isRegistered(NamespacedKey key) {
        return key != null && entries.containsKey(key);
    }

    public Collection<T> values() {
        return entries.values();
    }

    public Map<NamespacedKey, T> asMap() {
        return entries;
    }

    public Set<NamespacedKey> keySet() {
        return entries.keySet();
    }

    public void clear() {
        entries = Map.of();
    }
}
