package me.nagasonic.alkatraz.spells.effect;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.modifier.AppliedModifier;
import me.nagasonic.alkatraz.spells.modifier.AttributeAppliedModifier;
import me.nagasonic.alkatraz.spells.modifier.PotionAppliedModifier;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;

/**
 * The only sanctioned way to apply a timed effect, because it is the only place
 * the effect gets stamped with the circle of the caster who applied it.
 *
 * <p><b>This class never changes what an effect does.</b> It calls exactly the
 * same vanilla or modifier call the call site would have made, then records the
 * fact. A spell applying a raw potion outside this class is not broken — it is
 * simply invisible to the dispel, which is a correctness gap rather than a
 * crash.
 *
 * <p>Two things are worth stating because they look like bugs otherwise:
 *
 * <ul>
 *   <li><b>The circle comes from the caster, and mobs stamp 0.</b> There is no
 *       profile to read for a mob, and inventing a circle for one would make
 *       every mob-applied buff trivially strippable. 0 is the honest answer: an
 *       effect with no circle contests at band 1.</li>
 *   <li><b>Concealment has no caster parameter,</b> because both concealment
 *       spells are self-applied — caster and target are the same player — so
 *       the target's own circle is the caster's circle.</li>
 * </ul>
 */
public final class EffectApplicator {

    private EffectApplicator() {
    }

    /**
     * The handle for a Stealth illusion. Pass it straight to
     * {@link #applyConcealment(Player, Spell, Object)}.
     *
     * @return the package-private Stealth marker
     */
    public static Object stealthHandle() {
        return EffectConcealment.STEALTH;
    }

    /**
     * The handle for a Disguise illusion. Pass it straight to
     * {@link #applyConcealment(Player, Spell, Object)}.
     *
     * @return the package-private Disguise marker
     */
    public static Object disguiseHandle() {
        return EffectConcealment.DISGUISE;
    }

    /**
     * @param handle a concealment handle, possibly null
     * @return true if the handle came from {@link #stealthHandle()}
     */
    public static boolean isStealthHandle(Object handle) {
        return EffectConcealment.isStealth(handle);
    }

    /**
     * @param handle a concealment handle, possibly null
     * @return true if the handle came from {@link #disguiseHandle()}
     */
    public static boolean isDisguiseHandle(Object handle) {
        return EffectConcealment.isDisguise(handle);
    }

    /**
     * Applies a vanilla potion effect and stamps it with the caster's circle.
     *
     * <p>Duration is converted from ticks to milliseconds exactly once, here.
     * Duration is deliberately <b>not</b> part of the dispel contest — using it
     * would mean a freshly-drunk speed potion is easier to dispel than the same
     * potion thirty seconds later, which reads as a bug.
     *
     * @param caster the entity that cast the spell; a mob stamps circle 0
     * @param target the entity receiving the effect
     * @param effect the effect to apply
     * @param source the spell applying it; null applies the effect without stamping
     */
    public static void applyPotion(LivingEntity caster, LivingEntity target, PotionEffect effect, Spell source) {
        if (target == null || effect == null) return;
        target.addPotionEffect(effect);
        if (source == null) return;

        long now = System.currentTimeMillis();
        EffectRegistry.record(new ActiveEffect(
                target.getUniqueId(),
                EffectKind.POTION,
                source.getId(),
                source.getElement(),
                circleOf(caster),
                now,
                now + (long) effect.getDuration() * ActiveEffect.TICKS_TO_MILLIS,
                effect.getType()));
    }

    /**
     * Applies a pooled modifier and stamps it. Equivalent to
     * {@link #applyModifier(LivingEntity, LivingEntity, AppliedModifier, Spell, int)}
     * with no self-expiry.
     *
     * @param caster   the player that cast the spell; a mob stamps circle 0
     * @param target   the entity receiving the modifier
     * @param modifier the modifier to apply
     * @param source   the spell applying it
     */
    public static void applyModifier(LivingEntity caster, LivingEntity target, AppliedModifier modifier, Spell source) {
        applyModifier(caster, target, modifier, source, 0);
    }

    /**
     * Applies a pooled modifier and stamps it with the caster's circle.
     *
     * <p>A {@link PotionAppliedModifier} supplies its own tick count and is
     * stamped as {@link EffectKind#POTION}; an
     * {@link AttributeAppliedModifier} stamps {@link EffectKind#ATTRIBUTE_MODIFIER}
     * and anything else stamps {@link EffectKind#STAT_MODIFIER}. Both non-potion
     * kinds carry the modifier itself as the handle, because that is the only
     * object that knows how to undo them.
     *
     * <p>Attribute and stat modifiers have no duration of their own — the
     * duration lives in the caller's expiry task — so a {@code durationTicks} of
     * zero or less means "no self-expiry", which stamps
     * {@code Long.MAX_VALUE} and leaves removal to that task.
     *
     * @param caster        the player that cast the spell; a mob stamps circle 0
     * @param target        the entity receiving the modifier
     * @param modifier      the modifier to apply
     * @param source        the spell applying it
     * @param durationTicks how long the modifier lasts, in ticks; <= 0 means no self-expiry
     */
    public static void applyModifier(LivingEntity caster,
                                     LivingEntity target,
                                     AppliedModifier modifier,
                                     Spell source,
                                     int durationTicks) {
        if (target == null || modifier == null) return;
        modifier.apply(target, source);
        if (source == null) return;

        long now = System.currentTimeMillis();
        UUID targetId = target.getUniqueId();
        int circle = circleOf(caster);

        if (modifier instanceof PotionAppliedModifier potion) {
            int ticks = potion.getDurationTicks() > 0 ? potion.getDurationTicks() : durationTicks;
            EffectRegistry.record(new ActiveEffect(
                    targetId,
                    EffectKind.POTION,
                    source.getId(),
                    source.getElement(),
                    circle,
                    now,
                    ticks > 0 ? now + (long) ticks * ActiveEffect.TICKS_TO_MILLIS : Long.MAX_VALUE,
                    potion));
            return;
        }

        EffectKind kind = modifier instanceof AttributeAppliedModifier
                ? EffectKind.ATTRIBUTE_MODIFIER
                : EffectKind.STAT_MODIFIER;
        EffectRegistry.record(new ActiveEffect(
                targetId,
                kind,
                source.getId(),
                source.getElement(),
                circle,
                now,
                durationTicks > 0 ? now + (long) durationTicks * ActiveEffect.TICKS_TO_MILLIS : Long.MAX_VALUE,
                modifier));
    }

    /**
     * Stamps a concealment illusion. Unlike the other two methods this does not
     * apply anything — the owning spell has already run its own NMS work by the
     * time it calls this. It exists so the illusion is visible to the dispel.
     *
     * <p>There is no caster parameter because both concealment spells are
     * self-applied: caster and target are the same player, so the target's own
     * circle is the caster's circle.
     *
     * @param target the hiding player
     * @param source the spell that hid them
     * @param handle {@link #stealthHandle()} or {@link #disguiseHandle()}
     */
    public static void applyConcealment(Player target, Spell source, Object handle) {
        if (target == null || source == null || handle == null) return;
        long now = System.currentTimeMillis();
        MagicProfile profile = ProfileManager.getProfile(target, MagicProfile.class);
        EffectRegistry.record(new ActiveEffect(
                target.getUniqueId(),
                EffectKind.CONCEALMENT,
                source.getId(),
                source.getElement(),
                profile == null ? 0 : profile.getCircleLevel(),
                now,
                Long.MAX_VALUE,
                handle));
    }

    /**
     * The circle of the applier, or 0 when there is no profile to read.
     *
     * <p>{@link ProfileManager} keys profiles by player, so a mob caster has no
     * circle. Stamping 0 rather than skipping the stamp keeps the entry visible
     * to the dispel at all — it just contests at the lowest band.
     *
     * @param caster the applier
     * @return the caster's circle level, or 0 for a mob or an unknown profile
     */
    private static int circleOf(LivingEntity caster) {
        if (!(caster instanceof OfflinePlayer player)) return 0;
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        return profile == null ? 0 : profile.getCircleLevel();
    }
}