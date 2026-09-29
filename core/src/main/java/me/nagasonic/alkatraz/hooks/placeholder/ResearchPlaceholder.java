package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.api.progression.research.ResearchState;
import me.nagasonic.alkatraz.api.progression.research.definition.ResearchNode;
import me.nagasonic.alkatraz.progression.research.ResearchService;
import org.bukkit.entity.Player;

import java.util.Collection;
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
            // Equivalent to getCompletedResearchIds().size() (the union of the two
            // sets) without allocating a third collection on every resolution.
            Collection<String> completed = profile.getStringSet("researchCompleted");
            Collection<String> progressed = profile.getStringSet("researchProgress");
            Collection<String> smaller = completed.size() <= progressed.size() ? completed : progressed;
            Collection<String> larger = smaller == completed ? progressed : completed;
            int shared = 0;
            for (String id : smaller) {
                if (larger.contains(id)) shared++;
            }
            return String.valueOf(completed.size() + progressed.size() - shared);
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
