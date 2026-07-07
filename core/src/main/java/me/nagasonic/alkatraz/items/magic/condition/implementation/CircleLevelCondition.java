package me.nagasonic.alkatraz.items.magic.condition.implementation;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;

import java.util.Map;

public final class CircleLevelCondition implements Condition {
    private final int minCircle;

    public CircleLevelCondition(int minCircle) {
        this.minCircle = minCircle;
    }

    @Override
    public boolean test(TriggerContext context) {
        return context.playerActor().map(player -> {
            MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
            return profile.getCircleLevel() >= minCircle;
        }).orElse(false);
    }

    public static Condition fromConfig(Map<String, Object> config) {
        int minCircle = Integer.parseInt(String.valueOf(config.getOrDefault("min_circle", 1)));
        return new CircleLevelCondition(minCircle);
    }
}
