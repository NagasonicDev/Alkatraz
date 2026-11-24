package me.nagasonic.alkatraz.spells.util;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The one list of "bad" potion effects the light heal spells cleanse. Kept here
 * rather than copied per spell so a newly added effect only has to be listed once.
 */
public final class HarmfulEffects {

    private static final Set<String> HARMFUL = Set.of(
            "poison", "wither", "weakness", "slowness", "mining_fatigue", "blindness",
            "nausea", "hunger", "levitation", "unluck", "darkness", "bad_omen", "trial_omen");

    private HarmfulEffects() {
    }

    public static boolean isHarmful(PotionEffect effect) {
        return effect != null && HARMFUL.contains(effect.getType().getName().toLowerCase(Locale.ROOT));
    }

    /** Removes up to {@code max} harmful effects, strongest-free order. Returns how many went. */
    public static int clear(LivingEntity target, int max) {
        if (target == null || max <= 0) return 0;
        List<PotionEffect> active = new ArrayList<>(target.getActivePotionEffects());
        int removed = 0;
        for (PotionEffect effect : active) {
            if (removed >= max) break;
            if (!isHarmful(effect)) continue;
            target.removePotionEffect(effect.getType());
            removed++;
        }
        return removed;
    }
}
