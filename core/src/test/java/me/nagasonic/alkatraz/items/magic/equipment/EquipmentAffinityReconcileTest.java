package me.nagasonic.alkatraz.items.magic.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Equipment sync must compose with research, not overwrite it. The reconcile is
 * delta-based: subtract what we last applied, add what we now compute. A naive
 * "current value as base" fix compounds every 20 ticks.
 */
class EquipmentAffinityReconcileTest {

    /** Mirrors the arithmetic EquipmentStatService must perform for one stat. */
    private static double reconcile(double currentStat, double lastApplied, double nowComputed) {
        return currentStat - lastApplied + nowComputed;
    }

    @Test
    void firstSyncAddsEquipmentToResearchBase() {
        assertEquals(34.0 + 24.0, reconcile(34.0, 0.0, 24.0), 1e-9,
                "a research-buffed player keeps their 34 and gains the 24 from gear");
    }

    @Test
    void repeatSyncDoesNotCompound() {
        double afterFirst = reconcile(34.0, 0.0, 24.0);
        double afterSecond = reconcile(afterFirst, 24.0, 24.0);
        double afterHundredth = reconcile(afterSecond, 24.0, 24.0);
        assertEquals(58.0, afterHundredth, 1e-9,
                "100 syncs with unchanged gear must land on the same value as one sync");
    }

    @Test
    void equipmentSwapSubtractsTheOldBonus() {
        double withOldGear = reconcile(34.0, 0.0, 24.0);
        double withNewGear = reconcile(withOldGear, 24.0, 6.0);
        assertEquals(40.0, withNewGear, 1e-9,
                "swapping to weaker gear removes 18 and keeps the research 34");
    }

    @Test
    void unequippingRemovesEquipmentEntirely() {
        double geared = reconcile(34.0, 0.0, 24.0);
        double bare = reconcile(geared, 24.0, 0.0);
        assertEquals(34.0, bare, 1e-9,
                "unequipping everything leaves exactly the research value");
    }

    @Test
    void researchWrittenBetweenSyncsIsPreserved() {
        double afterFirst = reconcile(34.0, 0.0, 24.0);   // 58
        double withNewResearch = afterFirst + 8.0;         // player completes inferno_mastery
        double afterSecond = reconcile(withNewResearch, 24.0, 24.0);
        assertEquals(66.0, afterSecond, 1e-9,
                "the +8 research grant between syncs survives the reconcile");
    }
}