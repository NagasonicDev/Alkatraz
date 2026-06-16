package me.nagasonic.alkatraz.items.magic.component;

import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for optional {@link ComponentHandler} implementations.
 * Component types remain data-only; handlers provide behavior when needed.
 */
public final class ComponentHandlerRegistry {

    private static final Map<NamespacedKey, ComponentHandler> HANDLERS = new HashMap<>();

    private ComponentHandlerRegistry() {}

    public static void register(ComponentHandler handler) {
        HANDLERS.put(handler.type().getKey(), handler);
    }

    public static Optional<ComponentHandler> get(NamespacedKey componentType) {
        return Optional.ofNullable(HANDLERS.get(componentType));
    }

    public static Optional<ComponentHandler> get(ComponentType componentType) {
        return get(componentType.getKey());
    }
}
