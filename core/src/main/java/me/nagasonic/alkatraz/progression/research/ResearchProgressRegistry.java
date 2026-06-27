package me.nagasonic.alkatraz.progression.research;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ResearchProgressRegistry {

    private static volatile Map<String, ResearchProgressProvider> providers = Map.of();

    private ResearchProgressRegistry() {}

    public static void register(String namespace, ResearchProgressProvider provider) {
        if (namespace == null || namespace.isBlank() || provider == null) {
            throw new IllegalArgumentException("Research provider namespace and provider are required");
        }
        Map<String, ResearchProgressProvider> next = new HashMap<>(providers);
        next.put(namespace.toLowerCase(), provider);
        providers = Collections.unmodifiableMap(next);
    }

    public static Optional<ResearchProgressProvider> get(String namespace) {
        if (namespace == null) return Optional.empty();
        return Optional.ofNullable(providers.get(namespace.toLowerCase()));
    }

    public static boolean hasCompleted(Player player, String namespace, String researchId) {
        return get(namespace).map(provider -> provider.hasCompleted(player, researchId)).orElse(false);
    }

    public static void clear() {
        providers = Map.of();
    }
}
