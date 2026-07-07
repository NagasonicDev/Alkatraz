package me.nagasonic.alkatraz.api.magic.component;

import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    public static Optional<ComponentHandler> getWandHandler() {
        return get(NamespacedKey.fromString("alkatraz:wand"));
    }

    public static boolean hasHandler(NamespacedKey key) {
        return HANDLERS.containsKey(key);
    }
}
