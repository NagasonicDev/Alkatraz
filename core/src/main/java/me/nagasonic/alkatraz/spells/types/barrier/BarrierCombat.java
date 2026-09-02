package me.nagasonic.alkatraz.spells.types.barrier;

public final class BarrierCombat {

    private BarrierCombat() {}

    public record BarrierHit(double newHitpoints, boolean broken, boolean countered) {
        public boolean punchedThrough() {
            return broken && !countered;
        }
    }

    /**
     * Resolves one barrier hit, mirroring the current collide() semantics:
     * - a hit that leaves the barrier with HP remaining is always countered
     *   (broken=false, countered=true);
     * - a hit that breaks the barrier (HP <= 0) punches through only when the
     *   pre-damage ratio damage/hitpoints reaches punchThroughRatio (e.g. 1.25,
     *   a 25% overkill); otherwise the blow is still countered.
     */
    public static BarrierHit resolveHit(double hitpoints, double damage, double punchThroughRatio) {
        double next = hitpoints - damage;
        if (next > 0) {
            return new BarrierHit(next, false, true);
        }
        double ratio = hitpoints <= 0 ? 0 : damage / hitpoints;
        boolean punchedThrough = ratio >= punchThroughRatio;
        return new BarrierHit(0, true, !punchedThrough);
    }
}
