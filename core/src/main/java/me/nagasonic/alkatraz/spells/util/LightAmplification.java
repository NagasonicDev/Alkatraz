package me.nagasonic.alkatraz.spells.util;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Light is the counter-element: every LIGHT spell's damage is multiplied by the
 * stronger of two terms. Undead take a flat x2.0; a target invested in darkness
 * scales up to x2.5 on the gap between its DARK and LIGHT affinity. The two
 * terms take the MAX, never the product, so an undead that is also dark-affinity
 * caps at x2.5 rather than reaching x5.0.
 * <p>
 * Not mirrored in reverse - DARK takes no bonus against LIGHT affinity.
 */
public final class LightAmplification {

    /** Flat multiplier against anything on the undead roster. */
    public static final double UNDEAD_MULTIPLIER = 2.0;

    /** Affinity gap at which the dark-invested term reaches its cap. */
    public static final double GAP_CAP = 150.0;

    private LightAmplification() {
    }

    /**
     * Computes the light damage multiplier for a single damage instance.
     * <p>
     * The two terms are mutually exclusive by construction: anything on the
     * undead roster returns {@link #UNDEAD_MULTIPLIER} immediately, everything
     * else is scored purely on its DARK/LIGHT affinity gap. A target can therefore
     * never exceed x2.5.
     *
     * @param caster the casting entity; reserved for future caster-side terms and
     *               currently unused, so an undead caster is not amplified against itself
     * @param target the entity being damaged; null yields a neutral x1.0
     * @return the multiplier to apply to a LIGHT spell's damage, at least 1.0
     */
    public static double forTarget(LivingEntity caster, LivingEntity target) {
        if (target == null) return 1.0;
        if (UndeadTypes.contains(target.getType())) return UNDEAD_MULTIPLIER;
        double dark = affinityOf(target, Element.DARK);
        double light = affinityOf(target, Element.LIGHT);
        if (dark <= light) return 1.0;
        return 1.0 + Math.min(dark - light, GAP_CAP) / 100.0;
    }

    private static double affinityOf(LivingEntity entity, Element element) {
        if (entity instanceof Player player) {
            MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
            return profile == null ? 0.0 : profile.getAffinity(element);
        }
        return Utils.getEntityAffinity(element, entity);
    }
}