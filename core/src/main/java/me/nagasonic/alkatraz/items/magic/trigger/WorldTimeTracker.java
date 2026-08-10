package me.nagasonic.alkatraz.items.magic.trigger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks per-world day/night transitions so {@code on_day} / {@code on_night}
 * triggers can fire exactly once when dawn (world time 0) or dusk (world time
 * 13000) is crossed.
 */
public class WorldTimeTracker {

    public static final long FULL_CYCLE = 24000L;
    public static final long NIGHT_START = 13000L;

    private final Map<UUID, Long> lastTimes = new HashMap<>();

    /**
     * Returns the trigger key ({@code "on_day"} or {@code "on_night"}) whose
     * boundary was crossed between the two world times, or {@code null} if no
     * transition happened. Times are normalized into a single 0..24000 cycle.
     */
    public static String transitionTrigger(long previous, long current) {
        long prev = Math.floorMod(previous, FULL_CYCLE);
        long curr = Math.floorMod(current, FULL_CYCLE);
        if (curr < prev) {
            return "on_day";
        }
        if (prev < NIGHT_START && curr >= NIGHT_START) {
            return "on_night";
        }
        return null;
    }

    /**
     * Records the current world time for a world and returns the trigger key
     * for the transition since the previous recorded time, or {@code null}.
     */
    public String update(UUID worldId, long currentTime) {
        Long previous = lastTimes.get(worldId);
        String trigger = previous == null ? null : transitionTrigger(previous, currentTime);
        lastTimes.put(worldId, currentTime);
        return trigger;
    }

    public void reset(UUID worldId) {
        lastTimes.remove(worldId);
    }
}
