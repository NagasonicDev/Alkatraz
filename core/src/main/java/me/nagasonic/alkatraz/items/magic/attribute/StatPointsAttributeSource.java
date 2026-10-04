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
import org.bukkit.NamespacedKey;
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

    private static final Element[] ELEMENTS = Element.values();
    private static final NamespacedKey[] AFFINITY_KEYS = new NamespacedKey[ELEMENTS.length];
    private static final NamespacedKey[] RESISTANCE_KEYS = new NamespacedKey[ELEMENTS.length];

    static {
        for (int i = 0; i < ELEMENTS.length; i++) {
            String name = ELEMENTS[i].name().toLowerCase(Locale.ROOT);
            AFFINITY_KEYS[i] = MagicKeys.alkatraz(name + "_affinity");
            RESISTANCE_KEYS[i] = MagicKeys.alkatraz(name + "_resistance");
        }
    }

    private StatPointsAttributeSource() {}

    public static StatPointsAttributeSource getInstance() {
        return INSTANCE;
    }

    /**
     * Diminishing returns: {@code perPoint * points ^ exponent}. With the shipped
     * defaults (10, 0.65) this yields 28 affinity at 5 points and 215 at 112,
     * spanning a x1.28 to x3.15 multiplier across circles 1-9.
     *
     * <p>The {@code points <= 0} guard also keeps a negative exponent from turning
     * {@code Math.pow(0, negative)} into {@code Infinity}, so an uninvested
     * element is always exactly zero.
     */
    public static double affinityFromPoints(int points, double perPoint, double exponent) {
        return points <= 0 ? 0.0 : perPoint * Math.pow(points, exponent);
    }

    public static double resistanceFromPoints(int points, double perPoint, double exponent) {
        return points <= 0 ? 0.0 : perPoint * Math.pow(points, exponent);
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
        double affinityExponent = ((Number) Configs.AFFINITY_EXPONENT.get()).doubleValue();
        double resistanceExponent = ((Number) Configs.RESISTANCE_EXPONENT.get()).doubleValue();

        List<AttributeContribution> contributions = new ArrayList<>();
        for (int i = 0; i < ELEMENTS.length; i++) {
            Element element = ELEMENTS[i];
            if (element == Element.NONE) continue;
            int points = profile.getPoints(element);
            contributions.add(new AttributeContribution(
                    AFFINITY_KEYS[i],
                    affinityFromPoints(points, affinityPerPoint, affinityExponent),
                    AttributeContribution.AttributeOperation.ADD,
                    AttributeSourceType.BASE,
                    0));
            contributions.add(new AttributeContribution(
                    RESISTANCE_KEYS[i],
                    resistanceFromPoints(points, resistancePerPoint, resistanceExponent),
                    AttributeContribution.AttributeOperation.ADD,
                    AttributeSourceType.BASE,
                    0));
        }
        return contributions;
    }
}
