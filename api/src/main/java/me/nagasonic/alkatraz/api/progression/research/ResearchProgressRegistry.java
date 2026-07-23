package me.nagasonic.alkatraz.api.progression.research;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe registry that maps namespace strings to {@link ResearchProgressProvider} instances,
 * allowing multiple plugins to contribute research progress data.
 */
public final class ResearchProgressRegistry {

    private static volatile Map<String, ResearchProgressProvider> providers = Map.of();

    private ResearchProgressRegistry() {}

    /**
     * Registers a {@link ResearchProgressProvider} under the given namespace.
     *
     * @param namespace the unique namespace for this provider
     * @param provider the progress provider
     * @throws IllegalArgumentException if the namespace or provider is null/blank
     */
    public static void register(String namespace, ResearchProgressProvider provider) {
        if (namespace == null || namespace.isBlank() || provider == null) {
            throw new IllegalArgumentException("Research provider namespace and provider are required");
        }
        Map<String, ResearchProgressProvider> next = new HashMap<>(providers);
        next.put(namespace.toLowerCase(), provider);
        providers = Collections.unmodifiableMap(next);
    }

    /**
     * Looks up a provider by its namespace (case-insensitive).
     *
     * @param namespace the provider namespace
     * @return an {@link Optional} containing the provider, or empty if not found
     */
    public static Optional<ResearchProgressProvider> get(String namespace) {
        if (namespace == null) return Optional.empty();
        return Optional.ofNullable(providers.get(namespace.toLowerCase()));
    }

    /**
     * Convenience method that checks whether a player has completed a specific
     * research in the given namespace.
     *
     * @param player the player to check
     * @param namespace the provider namespace
     * @param researchId the research identifier
     * @return {@code true} if completed, or {@code false} if no provider is registered
     */
    public static boolean hasCompleted(Player player, String namespace, String researchId) {
        return get(namespace).map(provider -> provider.hasCompleted(player, researchId)).orElse(false);
    }

    /**
     * Clears all registered providers.
     */
    public static void clear() {
        providers = Map.of();
    }
}
