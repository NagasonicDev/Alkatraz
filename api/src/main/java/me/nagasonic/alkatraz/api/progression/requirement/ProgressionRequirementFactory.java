package me.nagasonic.alkatraz.api.progression.requirement;

import java.util.Map;

@FunctionalInterface
public interface ProgressionRequirementFactory {

    ProgressionRequirement create(Map<String, Object> config);
}
