package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.entity.Player;

import java.util.Map;

public class HotbarPlaceholder implements Placeholder {

    @Override
    public String name() {
        return "hotbar";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        if (profile == null) return "";

        if (params.startsWith("slot_")) {
            try {
                int slot = Integer.parseInt(params.substring(5));
                Map<Integer, String> hotbar = profile.getHotbarSpellIds();
                return hotbar.getOrDefault(slot - 1, "");
            } catch (NumberFormatException e) {
                return "";
            }
        }

        if (params.equals("count")) {
            // getHotbarSpellIds() collapses malformed entries onto the same slot, so
            // the raw set size is not equivalent; the map still has to be built.
            return String.valueOf(profile.getHotbarSpellIds().size());
        }

        return "";
    }
}
