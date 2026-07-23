package me.nagasonic.alkatraz.api.progression.requirement;

import java.util.Map;

/**
 * Functional interface for creating {@link ProgressionRequirement} instances
 * from a configuration map.
 */
@FunctionalInterface
public interface ProgressionRequirementFactory {

    /**
     * Creates a {@link ProgressionRequirement} from the given configuration map.
     *
     * @param config a map of configuration key-value pairs
     * @return the constructed requirement
     */
    ProgressionRequirement create(Map<String, Object> config);
}
