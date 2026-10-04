package me.nagasonic.alkatraz.spells.effect;

/**
 * The four families of timed effect the dispel contest can see.
 *
 * <p>The split is by <em>how the effect is undone</em>, not by what it does to
 * the target: a POTION is undone through
 * {@code LivingEntity.removePotionEffect}, an ATTRIBUTE_MODIFIER or
 * STAT_MODIFIER through {@code AppliedModifier.remove}, and a CONCEALMENT
 * through the owning spell's own reversal. Every Alkatraz timed effect falls
 * into exactly one of these, which is what makes the registry enumerable.
 */
public enum EffectKind {

    /** An ordinary vanilla potion effect. Undone by effect type. */
    POTION,

    /** A vanilla Bukkit attribute modifier. Undone by its AttributeAppliedModifier. */
    ATTRIBUTE_MODIFIER,

    /** A MagicProfile spell modifier. Undone by its StatAppliedModifier. */
    STAT_MODIFIER,

    /**
     * An illusion that hides the caster: Stealth or Disguise only. Undone by the
     * owning spell's own reversal, because only that spell knows which teardown
     * runs. These carry {@code expiresAtMillis == Long.MAX_VALUE} and are removed
     * explicitly — they are the only two effects that survive a restart, so each
     * re-stamps on join.
     */
    CONCEALMENT
}