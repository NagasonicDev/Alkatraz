package me.nagasonic.alkatraz.progression.requirement.implementation;

import me.nagasonic.alkatraz.progression.requirement.ProgressionRequirement;
import me.nagasonic.alkatraz.progression.requirement.RequirementContext;
import me.nagasonic.alkatraz.progression.research.ResearchProgressRegistry;

import java.util.Map;

public final class ResearchRequirement implements ProgressionRequirement {

    private final String provider;
    private final String researchId;

    public ResearchRequirement(String provider, String researchId) {
        this.provider = provider;
        this.researchId = researchId;
    }

    public static ProgressionRequirement fromConfig(Map<String, Object> config) {
        String provider = String.valueOf(config.getOrDefault("provider", "alkatraz"));
        String researchId = String.valueOf(config.get("research"));
        return new ResearchRequirement(provider, researchId);
    }

    @Override
    public boolean isMet(RequirementContext context) {
        return ResearchProgressRegistry.hasCompleted(context.getPlayer(), provider, researchId);
    }

    @Override
    public String describe() {
        return "Research " + provider + ":" + researchId;
    }
}
