package me.nagasonic.alkatraz.spells.implementation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkeletalKnightsConfigTest {

    @Test
    void defaultsFromEmptyMap() {
        SkeletalKnightsConfig cfg = SkeletalKnightsConfig.fromConfig(Map.of());
        assertEquals(3, cfg.knightCount());
        assertEquals(30, cfg.knightDuration());
        assertEquals(6.0, cfg.knightPower());
        assertEquals(5.0, cfg.summonRange());
        assertEquals(6, cfg.windUpSeconds());
        assertEquals(0.25, cfg.moveCancelThreshold());
    }

    @Test
    void defaultsFromNull() {
        SkeletalKnightsConfig cfg = SkeletalKnightsConfig.fromConfig(null);
        assertEquals(3, cfg.knightCount());
        assertEquals(6, cfg.windUpSeconds());
    }

    @Test
    void explicitValuesParsed() {
        SkeletalKnightsConfig cfg = SkeletalKnightsConfig.fromConfig(Map.of(
                "knight_count", 4,
                "knight_duration", 45,
                "knight_power", 8.0,
                "summon_range", 8.0,
                "wind_up_duration", 3,
                "move_cancel_threshold", 0.5
        ));
        assertEquals(4, cfg.knightCount());
        assertEquals(45, cfg.knightDuration());
        assertEquals(8.0, cfg.knightPower());
        assertEquals(8.0, cfg.summonRange());
        assertEquals(3, cfg.windUpSeconds());
        assertEquals(0.5, cfg.moveCancelThreshold());
    }

    @Test
    void windUpTicks() {
        SkeletalKnightsConfig cfg = SkeletalKnightsConfig.fromConfig(Map.of());
        assertEquals(120, cfg.windUpTicks(0, 100));
        assertEquals(90, cfg.windUpTicks(100, 100));
        SkeletalKnightsConfig threeSec = SkeletalKnightsConfig.fromConfig(Map.of("wind_up_duration", 3));
        assertEquals(60, threeSec.windUpTicks(0, 100));
        assertEquals(45, threeSec.windUpTicks(100, 100));
    }
}
