package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI handler exposing the player's focus resource:
 * {@code %alkatraz_focus_value%}, {@code %alkatraz_focus_max%},
 * {@code %alkatraz_focus_percent%}.
 */
public class FocusPlaceholder implements Placeholder {

    @Override
    public String name() {
        return "focus";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        if (profile == null) return "";
        return switch (params) {
            case "value" -> String.valueOf(Math.round(profile.getFocus()));
            case "max" -> String.valueOf(Math.round(profile.getMaxFocus()));
            case "percent" -> {
                double max = profile.getMaxFocus();
                yield max > 0 ? String.valueOf(Math.round(profile.getFocus() / max * 100)) : "0";
            }
            default -> "";
        };
    }
}
