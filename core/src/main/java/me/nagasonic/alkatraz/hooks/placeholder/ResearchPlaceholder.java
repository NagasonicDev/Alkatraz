package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.research.ResearchService;
import me.nagasonic.alkatraz.progression.research.ResearchState;
import me.nagasonic.alkatraz.progression.research.definition.ResearchNode;
import org.bukkit.entity.Player;

import java.util.Optional;

public class ResearchPlaceholder implements Placeholder {

    @Override
    public String name() {
        return "research";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        if (profile == null) return "";

        if (params.equals("completed_count")) {
            return String.valueOf(profile.getCompletedResearchIds().size());
        }

        if (params.startsWith("has_")) {
            String researchId = params.substring(4);
            return String.valueOf(profile.hasCompletedResearch(researchId));
        }

        if (params.startsWith("state_")) {
            String researchId = params.substring(6);
            Optional<ResearchNode> node = ResearchService.getNode(researchId);
            if (node.isEmpty()) return "";
            ResearchState state = ResearchService.getState(player, node.get());
            return state.name();
        }

        return "";
    }
}
