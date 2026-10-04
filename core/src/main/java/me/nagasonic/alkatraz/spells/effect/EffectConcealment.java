package me.nagasonic.alkatraz.spells.effect;

/**
 * The two concealment teardown markers.
 *
 * <p>Both concealment spells hide the caster, but they are undone by completely
 * different machinery: {@code Stealth} needs three NMS calls per online viewer
 * plus a repeating-task cancel, while {@code Disguise} needs a blocking skin
 * fetch before it touches the profile. A single {@code CONCEALMENT} kind with a
 * boolean handle would push that branch into a type test somewhere far from
 * either spell; two marker types let the reversal dispatch on
 * {@link EffectApplicator#isStealthHandle(Object)} /
 * {@link EffectApplicator#isDisguiseHandle(Object)} and nothing else.
 *
 * <p><b>This class and both marker records are package-private.</b> No spell
 * outside {@code spells.effect} can construct or pattern-match them. Callers
 * obtain one from {@link EffectApplicator#stealthHandle()} or
 * {@link EffectApplicator#disguiseHandle()} and pass it straight back into
 * {@link EffectApplicator#applyConcealment}; readers use the two
 * {@code isXxxHandle} predicates. That is deliberate — the markers carry no
 * state, so widening their visibility would only invite a spell to invent a
 * third one.
 */
final class EffectConcealment {

    private EffectConcealment() {
    }

    /** Marker for a Stealth illusion. Teardown: {@code Stealth.dispel(Player)}. */
    record StealthMarker() {
    }

    /** Marker for a Disguise illusion. Teardown: {@code Disguise.dispel(Player)}. */
    record DisguiseMarker() {
    }

    /** The single Stealth marker. Records compare by value, so this is a singleton in practice. */
    static final StealthMarker STEALTH = new StealthMarker();

    /** The single Disguise marker. Records compare by value, so this is a singleton in practice. */
    static final DisguiseMarker DISGUISE = new DisguiseMarker();

    static boolean isStealth(Object handle) {
        return handle instanceof StealthMarker;
    }

    static boolean isDisguise(Object handle) {
        return handle instanceof DisguiseMarker;
    }
}