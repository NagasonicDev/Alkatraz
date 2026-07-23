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
        Map<Integer, String> hotbar = profile.getHotbarSpellIds();

        if (params.startsWith("slot_")) {
            try {
                int slot = Integer.parseInt(params.substring(5));
                return hotbar.getOrDefault(slot - 1, "");
            } catch (NumberFormatException e) {
                return "";
            }
        }

        if (params.equals("count")) {
            return String.valueOf(hotbar.size());
        }

        return "";
    }
}
