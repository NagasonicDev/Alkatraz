package me.nagasonic.alkatraz.spells.implementation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarpConfigTest {

    @Test
    void defaultsFromEmptyMap() {
        WarpConfig cfg = WarpConfig.fromConfig(Map.of());
        assertEquals(2, cfg.baseSlots());
        assertEquals(List.of(25, 50, 75), cfg.masteryBonusSlots());
        assertEquals(500.0, cfg.baseDistance());
        assertEquals(100.0, cfg.distancePerCircle());
        assertEquals(0.5, cfg.masteryDistanceBonus());
        assertEquals(2, cfg.windUpSeconds());
        assertEquals(0.25, cfg.moveCancelThreshold());
    }

    @Test
    void defaultsFromNull() {
        WarpConfig cfg = WarpConfig.fromConfig(null);
        assertEquals(2, cfg.baseSlots());
        assertEquals(List.of(25, 50, 75), cfg.masteryBonusSlots());
        assertEquals(500.0, cfg.baseDistance());
        assertEquals(2, cfg.windUpSeconds());
    }

    @Test
    void explicitValuesParsed() {
        WarpConfig cfg = WarpConfig.fromConfig(Map.of(
                "base_slots", 3,
                "mastery_bonus_slots", List.of(30, 60),
                "base_distance", 700.0,
                "distance_per_circle", 50.0,
                "mastery_distance_bonus", 1.0,
                "wind_up_duration", 4,
                "move_cancel_threshold", 0.5
        ));
        assertEquals(3, cfg.baseSlots());
        assertEquals(List.of(30, 60), cfg.masteryBonusSlots());
        assertEquals(700.0, cfg.baseDistance());
        assertEquals(50.0, cfg.distancePerCircle());
        assertEquals(1.0, cfg.masteryDistanceBonus());
        assertEquals(4, cfg.windUpSeconds());
        assertEquals(0.5, cfg.moveCancelThreshold());
    }

    @Test
    void masteryBonusSlotsIgnoredWhenNotNumeric() {
        WarpConfig cfg = WarpConfig.fromConfig(Map.of("mastery_bonus_slots", List.of("x", 25)));
        assertEquals(List.of(25, 50, 75), cfg.masteryBonusSlots());
    }

    @Test
    void masteryBonusSlotsFallbackWhenEmpty() {
        WarpConfig cfg = WarpConfig.fromConfig(Map.of("mastery_bonus_slots", List.of()));
        assertEquals(List.of(25, 50, 75), cfg.masteryBonusSlots());
    }

    @Test
    void maxSlotsAtMasteryBreakpoints() {
        WarpConfig cfg = WarpConfig.fromConfig(Map.of());
        assertEquals(2, cfg.maxSlots(0));
        assertEquals(2, cfg.maxSlots(24));
        assertEquals(3, cfg.maxSlots(25));
        assertEquals(3, cfg.maxSlots(49));
        assertEquals(4, cfg.maxSlots(50));
        assertEquals(4, cfg.maxSlots(74));
        assertEquals(5, cfg.maxSlots(75));
        assertEquals(5, cfg.maxSlots(100));
    }

    @Test
    void maxSlotsAbsolute() {
        assertEquals(5, WarpConfig.fromConfig(Map.of()).maxSlotsAbsolute());
    }

    @Test
    void masteryForSlot() {
        WarpConfig cfg = WarpConfig.fromConfig(Map.of());
        assertEquals(0, cfg.masteryForSlot(0));
        assertEquals(0, cfg.masteryForSlot(1));
        assertEquals(25, cfg.masteryForSlot(2));
        assertEquals(50, cfg.masteryForSlot(3));
        assertEquals(75, cfg.masteryForSlot(4));
        assertEquals(0, cfg.masteryForSlot(5));
    }

    @Test
    void maxDistanceFormula() {
        WarpConfig cfg = WarpConfig.fromConfig(Map.of());
        assertEquals(500.0, cfg.maxDistance(6, 0, 100));
        assertEquals(600.0, cfg.maxDistance(7, 0, 100));
        assertEquals(700.0, cfg.maxDistance(8, 0, 100));
        assertEquals(750.0, cfg.maxDistance(6, 100, 100));
        assertEquals(750.0, cfg.maxDistance(7, 50, 100));
        assertEquals(500.0, cfg.maxDistance(5, 0, 100));
        assertEquals(500.0, cfg.maxDistance(6, 0, 0));
    }

    @Test
    void windUpTicks() {
        WarpConfig cfg = WarpConfig.fromConfig(Map.of());
        assertEquals(40, cfg.windUpTicks(0, 100));
        assertEquals(30, cfg.windUpTicks(100, 100));
        WarpConfig threeSec = WarpConfig.fromConfig(Map.of("wind_up_duration", 3));
        assertEquals(60, threeSec.windUpTicks(0, 100));
        assertEquals(45, threeSec.windUpTicks(100, 100));
    }
}