package me.nagasonic.alkatraz.api.progression.arcane;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for Arcane Knowledge reward sources.
 */
public final class ArcaneKnowledgeRegistry {

    private static volatile Map<String, ArcaneKnowledgeSource> sources = Map.of();

    private ArcaneKnowledgeRegistry() {}

    /**
     * Registers an {@link ArcaneKnowledgeSource} in the registry.
     *
     * @param source the source to register
     * @throws IllegalArgumentException if the source or its ID is null/blank
     */
    public static void register(ArcaneKnowledgeSource source) {
        if (source == null || source.getId() == null || source.getId().isBlank()) {
            throw new IllegalArgumentException("Arcane Knowledge source must have an id");
        }
        Map<String, ArcaneKnowledgeSource> next = new HashMap<>(sources);
        next.put(source.getId().toLowerCase(), source);
        sources = Collections.unmodifiableMap(next);
    }

    /**
     * Looks up a source by its ID (case-insensitive).
     *
     * @param id the source identifier
     * @return an {@link Optional} containing the source, or empty if not found
     */
    public static Optional<ArcaneKnowledgeSource> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(sources.get(id.toLowerCase()));
    }

    /**
     * Returns an unmodifiable view of all registered sources.
     *
     * @return map of source ID to {@link ArcaneKnowledgeSource}
     */
    public static Map<String, ArcaneKnowledgeSource> all() {
        return sources;
    }

    /**
     * Clears all registered sources.
     */
    public static void clear() {
        sources = Map.of();
    }
}
