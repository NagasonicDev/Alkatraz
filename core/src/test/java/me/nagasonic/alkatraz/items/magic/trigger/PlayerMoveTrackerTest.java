package me.nagasonic.alkatraz.items.magic.trigger;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMoveTrackerTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000ee");

    private static PlayerMoveTracker.MoveState state(String blockKey, String blockType,
                                                     boolean onGround, boolean inWater,
                                                     boolean inLava, boolean climbing,
                                                     String biome, double y) {
        return new PlayerMoveTracker.MoveState(blockKey, blockType, onGround, inWater, inLava, climbing, biome, y);
    }

    @Test
    void walkingOntoNewBlockFiresStepOnBlock() {
        PlayerMoveTracker.MoveState prev = state("a", "GRASS_BLOCK", true, false, false, false, "plains", 64);
        PlayerMoveTracker.MoveState curr = state("b", "GRASS_BLOCK", true, false, false, false, "plains", 64);
        assertTrue(PlayerMoveTracker.transitions(prev, curr).contains("on_step_on_block"));
    }

    @Test
    void leavingGroundWhileRisingFiresJump() {
        PlayerMoveTracker.MoveState prev = state("a", "GRASS_BLOCK", true, false, false, false, "plains", 64);
        PlayerMoveTracker.MoveState curr = state("a", "GRASS_BLOCK", false, false, false, false, "plains", 64.5);
        assertTrue(PlayerMoveTracker.transitions(prev, curr).contains("on_jump"));
    }

    @Test
    void airborneRiseWithoutGroundingDoesNotRefireJump() {
        PlayerMoveTracker.MoveState prev = state("a", "GRASS_BLOCK", false, false, false, false, "plains", 64.5);
        PlayerMoveTracker.MoveState curr = state("a", "GRASS_BLOCK", false, false, false, false, "plains", 64.9);
        assertTrue(PlayerMoveTracker.transitions(prev, curr).isEmpty());
    }

    @Test
    void landingFiresLandTrigger() {
        PlayerMoveTracker.MoveState prev = state("a", "GRASS_BLOCK", false, false, false, false, "plains", 70);
        PlayerMoveTracker.MoveState curr = state("a", "GRASS_BLOCK", true, false, false, false, "plains", 70);
        assertTrue(PlayerMoveTracker.transitions(prev, curr).contains("on_land"));
    }

    @Test
    void enteringWaterFiresEnterWater() {
        PlayerMoveTracker.MoveState prev = state("a", "STONE", true, false, false, false, "ocean", 62);
        PlayerMoveTracker.MoveState curr = state("b", "WATER", true, true, false, false, "ocean", 62);
        Set<String> fired = PlayerMoveTracker.transitions(prev, curr);
        assertTrue(fired.contains("on_enter_water"));
        assertTrue(fired.contains("on_step_on_block"));
    }

    @Test
    void enteringLavaFiresEnterLava() {
        PlayerMoveTracker.MoveState prev = state("a", "STONE", true, false, false, false, "nether_wastes", 62);
        PlayerMoveTracker.MoveState curr = state("b", "LAVA", true, false, true, false, "nether_wastes", 62);
        assertTrue(PlayerMoveTracker.transitions(prev, curr).contains("on_enter_lava"));
    }

    @Test
    void climbingFiresClimbTrigger() {
        PlayerMoveTracker.MoveState prev = state("a", "STONE", true, false, false, false, "plains", 64);
        PlayerMoveTracker.MoveState curr = state("b", "LADDER", false, false, false, true, "plains", 64);
        assertTrue(PlayerMoveTracker.transitions(prev, curr).contains("on_climb"));
    }

    @Test
    void changingBiomeFiresEnterBiome() {
        PlayerMoveTracker.MoveState prev = state("a", "GRASS_BLOCK", true, false, false, false, "plains", 64);
        PlayerMoveTracker.MoveState curr = state("b", "GRASS_BLOCK", true, false, false, false, "forest", 64);
        assertTrue(PlayerMoveTracker.transitions(prev, curr).contains("on_enter_biome"));
    }

    @Test
    void identicalStateFiresNothing() {
        PlayerMoveTracker.MoveState s = state("a", "GRASS_BLOCK", true, false, false, false, "plains", 64);
        assertTrue(PlayerMoveTracker.transitions(s, s).isEmpty());
    }

    @Test
    void firstUpdateReturnsEmptyAndStoresState() {
        PlayerMoveTracker tracker = new PlayerMoveTracker();
        PlayerMoveTracker.MoveState s = state("a", "GRASS_BLOCK", true, false, false, false, "plains", 64);
        assertTrue(tracker.update(PLAYER, s).isEmpty());
        assertEquals(s, tracker.state(PLAYER));
    }

    @Test
    void updateComputesAgainstStoredState() {
        PlayerMoveTracker tracker = new PlayerMoveTracker();
        tracker.update(PLAYER, state("a", "GRASS_BLOCK", true, false, false, false, "plains", 64));
        PlayerMoveTracker.MoveState next = state("b", "GRASS_BLOCK", true, false, false, false, "plains", 64);
        assertTrue(tracker.update(PLAYER, next).contains("on_step_on_block"));
    }
}
