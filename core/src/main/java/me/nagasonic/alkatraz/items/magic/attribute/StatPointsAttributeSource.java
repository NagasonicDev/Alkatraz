package me.nagasonic.alkatraz.items.magic.attribute;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeContribution;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeSource;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeContribution.AttributeSourceType;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Contributes invested stat points as BASE affinity/resistance values, so that
 * equipment bonuses ADD on top of points instead of replacing them.
 */
public final class StatPointsAttributeSource implements AttributeSource {

    private static final StatPointsAttributeSource INSTANCE = new StatPointsAttributeSource();

    private StatPointsAttributeSource() {}

    public static StatPointsAttributeSource getInstance() {
        return INSTANCE;
    }

    public static double affinityFromPoints(int points, double perPoint) {
        return points * perPoint;
    }

    public static double resistanceFromPoints(int points, double perPoint) {
        return points * perPoint;
    }

    @Override
    public AttributeSourceType sourceType() {
        return AttributeSourceType.BASE;
    }

    @Override
    public List<AttributeContribution> collect(LivingEntity entity, TriggerContext context) {
        if (!(entity instanceof Player player)) {
            return List.of();
        }
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        if (profile == null) {
            return List.of();
        }
        double affinityPerPoint = ((Number) Configs.AFFINITY_PER_POINT.get()).doubleValue();
        double resistancePerPoint = ((Number) Configs.RESISTANCE_PER_POINT.get()).doubleValue();

        List<AttributeContribution> contributions = new ArrayList<>();
        for (Element element : Element.values()) {
            if (element == Element.NONE) continue;
            int points = profile.getPoints(element);
            String name = element.name().toLowerCase(Locale.ROOT);
            contributions.add(new AttributeContribution(
                    MagicKeys.alkatraz(name + "_affinity"),
                    affinityFromPoints(points, affinityPerPoint),
                    AttributeContribution.AttributeOperation.ADD,
                    AttributeSourceType.BASE,
                    0));
            contributions.add(new AttributeContribution(
                    MagicKeys.alkatraz(name + "_resistance"),
                    resistanceFromPoints(points, resistancePerPoint),
                    AttributeContribution.AttributeOperation.ADD,
                    AttributeSourceType.BASE,
                    0));
        }
        return contributions;
    }
}
