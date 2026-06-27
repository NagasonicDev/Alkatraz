package me.nagasonic.alkatraz.progression.arcane;

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

    public static void register(ArcaneKnowledgeSource source) {
        if (source == null || source.getId() == null || source.getId().isBlank()) {
            throw new IllegalArgumentException("Arcane Knowledge source must have an id");
        }
        Map<String, ArcaneKnowledgeSource> next = new HashMap<>(sources);
        next.put(source.getId().toLowerCase(), source);
        sources = Collections.unmodifiableMap(next);
    }

    public static Optional<ArcaneKnowledgeSource> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(sources.get(id.toLowerCase()));
    }

    public static Map<String, ArcaneKnowledgeSource> all() {
        return sources;
    }

    public static void clear() {
        sources = Map.of();
    }
}
