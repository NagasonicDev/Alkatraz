package me.nagasonic.alkatraz.spells.effect;

import me.nagasonic.alkatraz.api.Element;

import java.util.UUID;

/**
 * One stamped timed effect: who is carrying it, who put it there, how strong
 * the applier was, and what has to be undone.
 *
 * <p>This record is the whole of spec §4.5. {@code sourceCircle} is the piece
 * that did not exist before: nothing in {@code core/src/main} recorded a level
 * on an applied effect, so {@code dispel} had no way to know whether a buff
 * belonged to a circle-1 caster or a circle-9 one.
 *
 * <p><b>Timestamps are milliseconds from {@link System#currentTimeMillis()},
 * never server ticks.</b> Ticks read more naturally against
 * {@code PotionEffect.getDuration()}, but no tick source exists across
 * 1.19.1 → 1.21.11 without a version shim, and core may not hold NMS. Convert
 * once, at the call site:
 * {@code now + (long) effect.getDuration() * ActiveEffect.TICKS_TO_MILLIS}.
 *
 * <p><b>{@code handle} is deliberately {@link Object}.</b> For
 * {@link EffectKind#ATTRIBUTE_MODIFIER} and {@link EffectKind#STAT_MODIFIER} it
 * is the {@code AppliedModifier} that knows how to undo itself; for
 * {@link EffectKind#POTION} it is the
 * {@code org.bukkit.potion.PotionEffectType}, because the removal API
 * {@code LivingEntity.removePotionEffect(PotionEffectType)} takes a type and
 * not an effect; for {@link EffectKind#CONCEALMENT} it is one of the two
 * package-private markers held by {@link EffectConcealment} and handed out by
 * {@link EffectApplicator#stealthHandle()} /
 * {@link EffectApplicator#disguiseHandle()}, so the reversal knows which
 * teardown to run.
 */
public record ActiveEffect(
        UUID targetId,
        EffectKind kind,
        String sourceSpellId,
        Element element,
        int sourceCircle,
        long appliedAtMillis,
        long expiresAtMillis,
        Object handle) {

    /**
     * Milliseconds per server tick. The single place this factor is written;
     * every conversion goes through it so the number is never retyped.
     */
    public static final long TICKS_TO_MILLIS = 50L;

    /**
     * Expiry test used by the registry's prune.
     *
     * <p>The comparison is <b>inclusive</b>: an effect whose
     * {@code expiresAtMillis} equals {@code now} is already over. That is the
     * boundary a duration of zero lands on, and a zero-duration effect is
     * genuinely over the instant it is stamped.
     *
     * @param nowMillis the current wall-clock time in milliseconds
     * @return true if this entry has expired and must be pruned
     */
    public boolean isExpiredAt(long nowMillis) {
        return expiresAtMillis <= nowMillis;
    }
}