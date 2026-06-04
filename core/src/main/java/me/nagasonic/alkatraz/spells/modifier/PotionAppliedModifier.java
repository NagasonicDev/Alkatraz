package me.nagasonic.alkatraz.spells.modifier;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Applies a potion effect for the duration of a pooled modifier spell (used for mobs
 * when profile stats are unavailable).
 */
public class PotionAppliedModifier extends AppliedModifier {

    private final PotionEffectType effectType;
    private final int amplifier;
    private int durationTicks;

    public PotionAppliedModifier(PotionEffectType effectType,
                                 int amplifier,
                                 String description) {
        super(description);
        this.effectType = effectType;
        this.amplifier = amplifier;
    }

    public void setDurationTicks(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    @Override
    public void apply(LivingEntity entity, Spell source) {
        if (durationTicks <= 0) return;
        entity.addPotionEffect(new PotionEffect(
                effectType, durationTicks, amplifier, false, true, true));
    }

    @Override
    public void remove(LivingEntity entity, Spell source) {
        entity.removePotionEffect(effectType);
    }
}
