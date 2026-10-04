package me.nagasonic.alkatraz.spells.effect;

import me.nagasonic.alkatraz.api.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The registry's own bookkeeping, with no Bukkit static mocked at all.
 *
 * <p>That is possible because {@link EffectRegistry#record(ActiveEffect)},
 * {@link EffectRegistry#remove(ActiveEffect)} and
 * {@link EffectRegistry#clear(UUID)} never call the clock: both timestamps are
 * constructor parameters of {@link ActiveEffect}. The single clock read lives in
 * {@code effectsOn}, and the package-private
 * {@link EffectRegistry#effectsOn(UUID, long)} overload makes it injectable, so
 * the expiry boundary can be asserted exactly rather than approximately.
 */
class EffectRegistryTest {

    private final Set<UUID> touched = new HashSet<>();

    @AfterEach
    void clearRegistry() {
        for (UUID id : touched) EffectRegistry.clear(id);
        touched.clear();
    }

    private UUID target() {
        UUID id = UUID.randomUUID();
        touched.add(id);
        return id;
    }

    private ActiveEffect effect(UUID targetId, long appliedAt, long expiresAt) {
        return new ActiveEffect(
                targetId,
                EffectKind.POTION,
                "buff",
                Element.LIGHT,
                3,
                appliedAt,
                expiresAt,
                null);
    }

    @Test
    void recordThenReadBack() {
        UUID id = target();
        ActiveEffect stamped = effect(id, 100L, 200L);
        EffectRegistry.record(stamped);

        List<ActiveEffect> found = EffectRegistry.effectsOn(id, 150L);
        assertEquals(1, found.size(), "one recorded effect must be readable");
        assertEquals(stamped, found.get(0), "the record must survive the round trip intact");
        assertEquals(3, found.get(0).sourceCircle(), "the caster's circle must be stamped on the effect");
    }

    @Test
    void expiredEntriesArePrunedBeforeReturning() {
        UUID id = target();
        EffectRegistry.record(effect(id, 0L, 100L));
        EffectRegistry.record(effect(id, 0L, 5_000L));

        List<ActiveEffect> found = EffectRegistry.effectsOn(id, 1_000L);
        assertEquals(1, found.size(), "the entry that expired at 100ms must be dropped, the live one kept");
        assertEquals(5_000L, found.get(0).expiresAtMillis(), "the surviving entry must be the unexpired one");
    }

    @Test
    void expiryBoundaryIsInclusive() {
        UUID id = target();
        EffectRegistry.record(effect(id, 0L, 1_000L));

        assertEquals(1, EffectRegistry.effectsOn(id, 999L).size(),
                "one millisecond before expiry the entry is still live");
        assertEquals(0, EffectRegistry.effectsOn(id, 1_000L).size(),
                "expiry is inclusive: expiresAtMillis == now prunes, because a 0-tick effect is already over");
        assertEquals(0, EffectRegistry.effectsOn(id, 1_001L).size(),
                "one millisecond after expiry the entry is gone");
    }

    @Test
    void maxValueEntriesSurvivePruning() {
        UUID id = target();
        ActiveEffect concealment = new ActiveEffect(
                id,
                EffectKind.CONCEALMENT,
                "stealth",
                Element.NONE,
                2,
                0L,
                Long.MAX_VALUE,
                EffectApplicator.stealthHandle());
        EffectRegistry.record(concealment);

        // The public overload, reading the real clock. A concealment entry's
        // expiresAtMillis is Long.MAX_VALUE and the current time is ~1.7e12, so the
        // inclusive comparison is nowhere near the boundary and the entry survives.
        List<ActiveEffect> found = EffectRegistry.effectsOn(id);
        assertEquals(1, found.size(),
                "a concealment entry expires at Long.MAX_VALUE so it must never be pruned away — "
                        + "it is removed explicitly by the reversal instead");
        assertEquals(concealment, found.get(0), "the concealment entry must come back unchanged");
    }

    @Test
    void removeDropsOnlyThatEntry() {
        UUID id = target();
        ActiveEffect first = effect(id, 0L, 5_000L);
        ActiveEffect second = new ActiveEffect(
                id, EffectKind.ATTRIBUTE_MODIFIER, "buff", Element.LIGHT, 3, 0L, 5_000L, "handle");
        EffectRegistry.record(first);
        EffectRegistry.record(second);

        EffectRegistry.remove(first);

        List<ActiveEffect> found = EffectRegistry.effectsOn(id, 1_000L);
        assertEquals(1, found.size(), "removing one entry must not remove the other");
        assertEquals(second, found.get(0), "the surviving entry must be the one that was not removed");
    }

    @Test
    void clearDropsEverythingForOneTargetOnly() {
        UUID mine = target();
        UUID theirs = target();
        EffectRegistry.record(effect(mine, 0L, 5_000L));
        EffectRegistry.record(effect(theirs, 0L, 5_000L));

        EffectRegistry.clear(mine);

        assertEquals(0, EffectRegistry.effectsOn(mine, 1_000L).size(),
                "clear must empty the named target's list");
        assertEquals(1, EffectRegistry.effectsOn(theirs, 1_000L).size(),
                "clear must not touch any other target");
    }

    @Test
    void pruneAllDropsExpiredEntriesEverywhereAndKeepsLiveOnes() {
        UUID stale = target();
        UUID live = target();
        EffectRegistry.record(effect(stale, 0L, 1L));
        EffectRegistry.record(effect(live, 0L, Long.MAX_VALUE));

        EffectRegistry.pruneAll();

        assertEquals(0, EffectRegistry.effectsOn(stale, 1_000L).size(),
                "pruneAll must drop an entry whose expiry is already in the past");
        assertEquals(1, EffectRegistry.effectsOn(live, 1_000L).size(),
                "pruneAll must keep an entry that has not expired");
    }
}