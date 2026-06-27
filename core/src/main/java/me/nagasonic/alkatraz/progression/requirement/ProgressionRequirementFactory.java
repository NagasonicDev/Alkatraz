package me.nagasonic.alkatraz.progression.requirement;

import java.util.Map;

@FunctionalInterface
public interface ProgressionRequirementFactory {

    ProgressionRequirement create(Map<String, Object> config);
}
