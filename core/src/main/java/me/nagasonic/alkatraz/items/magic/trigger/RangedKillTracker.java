package me.nagasonic.alkatraz.items.magic.trigger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Associates a victim entity with the ranged weapon (and its shooter) that
 * dealt damage to them, so an {@code on_ranged_kill} trigger can resolve the
 * killing weapon when the victim dies shortly afterwards.
 *
 * @param <W> the weapon association type
 */
public class RangedKillTracker<W> {

    public record Entry<W>(UUID shooter, W weapon) {
    }

    private record TimedEntry<W>(UUID shooter, W weapon, long recordedAt) {
    }

    private final long ttlMillis;
    private final Map<UUID, TimedEntry<W>> entries = new ConcurrentHashMap<>();

    /** Timestamp of the last full sweep; {@code true} forces the first call through. */
    private long lastCleanup;
    private boolean swept;

    public RangedKillTracker(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public void record(UUID victim, UUID shooter, W weapon) {
        entries.put(victim, new TimedEntry<>(shooter, weapon, System.currentTimeMillis()));
    }

    /**
     * Returns the entry for a victim if it exists and has not expired, removing
     * it if it has expired.
     */
    public Optional<Entry<W>> resolve(UUID victim, long nowMillis) {
        TimedEntry<W> entry = entries.get(victim);
        if (entry == null) {
            return Optional.empty();
        }
        if (nowMillis - entry.recordedAt() > ttlMillis) {
            entries.remove(victim);
            return Optional.empty();
        }
        return Optional.of(new Entry<>(entry.shooter(), entry.weapon()));
    }

    public void cleanup(long nowMillis) {
        // Time-gated: an O(n) sweep on every projectile hit is wasteful when entries
        // expire slowly. The resolve() TTL check still drops stale entries on lookup, so
        // an entry can outlive the sweep window by at most one interval.
        if (swept && nowMillis - lastCleanup < ttlMillis) {
            return;
        }
        swept = true;
        lastCleanup = nowMillis;
        entries.entrySet().removeIf(e -> nowMillis - e.getValue().recordedAt() > ttlMillis);
    }
}
