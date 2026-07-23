package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Element;
import org.bukkit.entity.Player;

import java.util.Map;

public class ElementPlaceholder implements Placeholder {

    private static final Map<String, Element> ELEMENT_MAP = Map.of(
            "fire", Element.FIRE,
            "water", Element.WATER,
            "air", Element.AIR,
            "earth", Element.EARTH,
            "light", Element.LIGHT,
            "dark", Element.DARK
    );

    @Override
    public String name() {
        return "elements";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        if (profile == null) return "";

        if (params.startsWith("points_")) {
            String elementName = params.substring(7);
            Element element = ELEMENT_MAP.get(elementName);
            if (element == null) return "";
            return String.valueOf(profile.getPoints(element));
        }

        if (params.startsWith("affinity_")) {
            String elementName = params.substring(9);
            Element element = ELEMENT_MAP.get(elementName);
            if (element == null) return "";
            return String.valueOf(profile.getAffinity(element));
        }

        if (params.startsWith("resistance_")) {
            String elementName = params.substring(11);
            Element element = ELEMENT_MAP.get(elementName);
            if (element == null) return "";
            return String.valueOf(profile.getResistance(element));
        }

        return switch (params) {
            case "magic_affinity" -> String.valueOf(profile.getMagicAffinity());
            case "magic_resistance" -> String.valueOf(profile.getMagicResistance());
            default -> "";
        };
    }
}
