package me.nagasonic.alkatraz.api.magic.component;

import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry that maps {@link ComponentType} keys to their {@link ComponentHandler} implementations.
 * <p>
 * Plugins register handlers via {@link #register(ComponentHandler)} during startup, and the
 * runtime looks up the appropriate handler by its {@link org.bukkit.NamespacedKey} when processing
 * magic item events.
 */
public final class ComponentHandlerRegistry {

    private static final Map<NamespacedKey, ComponentHandler> HANDLERS = new HashMap<>();

    private ComponentHandlerRegistry() {}

    /**
     * Registers a component handler. If a handler with the same key is already registered,
     * it is replaced.
     *
     * @param handler the handler to register
     */
    public static void register(ComponentHandler handler) {
        HANDLERS.put(handler.type().getKey(), handler);
    }

    /**
     * Looks up a handler by its {@link NamespacedKey}.
     *
     * @param componentType the key identifying the component type
     * @return an {@link Optional} containing the handler, or empty if none is registered
     */
    public static Optional<ComponentHandler> get(NamespacedKey componentType) {
        return Optional.ofNullable(HANDLERS.get(componentType));
    }

    /**
     * Looks up a handler by its {@link ComponentType}.
     *
     * @param componentType the component type to look up
     * @return an {@link Optional} containing the handler, or empty if none is registered
     */
    public static Optional<ComponentHandler> get(ComponentType componentType) {
        return get(componentType.getKey());
    }

    /**
     * Convenience method that returns the handler registered for the built-in wand component
     * ({@code alkatraz:wand}).
     *
     * @return an {@link Optional} containing the wand handler, or empty if not registered
     */
    public static Optional<ComponentHandler> getWandHandler() {
        return get(NamespacedKey.fromString("alkatraz:wand"));
    }

    /**
     * Checks whether a handler is registered for the given key.
     *
     * @param key the component type key to check
     * @return {@code true} if a handler is registered for the key
     */
    public static boolean hasHandler(NamespacedKey key) {
        return HANDLERS.containsKey(key);
    }
}
