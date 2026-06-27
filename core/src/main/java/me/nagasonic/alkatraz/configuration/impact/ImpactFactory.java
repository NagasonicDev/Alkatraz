package me.nagasonic.alkatraz.configuration.impact;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public final class ImpactFactory {

    @FunctionalInterface
    public interface Builder {
        Impact build(Spell spell, ConfigurationSection section);
    }

    private static final Map<String, Builder> REGISTRY = new HashMap<>();

    public static void register(String type, Builder builder) {
        if (type == null || type.isBlank() || builder == null) {
            throw new IllegalArgumentException("Impact type and builder are required");
        }
        REGISTRY.put(type.toLowerCase(), builder);
    }

    public static Impact create(String type, Spell spell, ConfigurationSection section) {
        Builder builder = REGISTRY.get(type.toLowerCase());
        if (builder == null) {
            throw new IllegalArgumentException("Unknown impact type '" + type + "'. Registered types: " + REGISTRY.keySet());
        }
        return builder.build(spell, section);
    }

    public static boolean isRegistered(String type) {
        return type != null && REGISTRY.containsKey(type.toLowerCase());
    }

    private ImpactFactory() {}
}
