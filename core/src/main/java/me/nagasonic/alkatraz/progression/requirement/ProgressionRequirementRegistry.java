package me.nagasonic.alkatraz.progression.requirement;

import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.configuration.requirement.RequirementFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry for Circle requirement types.
 */
public final class ProgressionRequirementRegistry {

    private static volatile Map<String, ProgressionRequirementFactory> factories = Map.of();

    private ProgressionRequirementRegistry() {}

    public static void register(String type, ProgressionRequirementFactory factory) {
        if (type == null || type.isBlank() || factory == null) {
            throw new IllegalArgumentException("Requirement type and factory are required");
        }
        Map<String, ProgressionRequirementFactory> next = new HashMap<>(factories);
        next.put(type.toLowerCase(), factory);
        factories = Collections.unmodifiableMap(next);
    }

    public static ProgressionRequirement create(Map<String, Object> config) {
        Object rawType = config.get("type");
        if (rawType == null) {
            throw new IllegalArgumentException("Circle requirement is missing type");
        }
        String type = String.valueOf(rawType).toLowerCase();
        ProgressionRequirementFactory factory = factories.get(type);
        if (factory == null) {
            if (RequirementFactory.isRegistered(type)) {
                Requirement requirement = RequirementFactory.create(null, RequirementFactory.toSection(config));
                return new CentralRequirementAdapter(requirement);
            }
            throw new IllegalArgumentException("Unknown Circle requirement type: " + type);
        }
        return factory.create(config);
    }

    public static void clear() {
        factories = Map.of();
    }

    private record CentralRequirementAdapter(Requirement requirement) implements ProgressionRequirement {
        @Override
        public boolean isMet(RequirementContext context) {
            return requirement.isMet(context.getPlayer());
        }

        @Override
        public String describe() {
            return requirement.getDescription();
        }
    }
}
