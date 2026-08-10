package me.nagasonic.alkatraz.items.magic.trigger;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player movement state between {@code PlayerMoveEvent}s so
 * movement-based engraving triggers ({@code on_jump}, {@code on_land},
 * {@code on_enter_water}, {@code on_climb}, ...) can fire exactly once when a
 * state transition happens.
 */
public class PlayerMoveTracker {

    public static final String STEP_ON_BLOCK = "on_step_on_block";
    public static final String JUMP = "on_jump";
    public static final String LAND = "on_land";
    public static final String ENTER_WATER = "on_enter_water";
    public static final String ENTER_LAVA = "on_enter_lava";
    public static final String CLIMB = "on_climb";
    public static final String ENTER_BIOME = "on_enter_biome";

    /**
     * Immutable snapshot of a player's movement-relevant state.
     *
     * @param blockKey   identity of the block position (world + x/y/z)
     * @param blockType  name of the block at the player's feet
     * @param onGround   whether the player stands on ground
     * @param inWater    whether the player is inside water
     * @param inLava     whether the player is inside lava
     * @param climbing   whether the player is climbing a ladder/vine
     * @param biome      current biome name
     * @param y          world Y coordinate
     */
    public record MoveState(String blockKey, String blockType, boolean onGround,
                            boolean inWater, boolean inLava, boolean climbing,
                            String biome, double y) {
    }

    private final Map<UUID, MoveState> states = new ConcurrentHashMap<>();

    /**
     * Computes which movement triggers fire when moving from {@code previous}
     * to {@code current}. Pure function of the two states.
     */
    public static Set<String> transitions(MoveState previous, MoveState current) {
        Set<String> fired = new LinkedHashSet<>();
        if (!previous.blockKey().equals(current.blockKey())) {
            fired.add(STEP_ON_BLOCK);
        }
        if (previous.onGround() && !current.onGround() && current.y() > previous.y()) {
            fired.add(JUMP);
        }
        if (!previous.onGround() && current.onGround()) {
            fired.add(LAND);
        }
        if (!previous.inWater() && current.inWater()) {
            fired.add(ENTER_WATER);
        }
        if (!previous.inLava() && current.inLava()) {
            fired.add(ENTER_LAVA);
        }
        if (!previous.climbing() && current.climbing()) {
            fired.add(CLIMB);
        }
        if (!previous.biome().equals(current.biome())) {
            fired.add(ENTER_BIOME);
        }
        return fired;
    }

    /**
     * Records a player's new state and returns the triggers that fired during
     * the transition from the previously recorded state (empty on first call).
     */
    public Set<String> update(UUID playerId, MoveState current) {
        MoveState previous = states.get(playerId);
        Set<String> fired = previous == null ? Set.of() : transitions(previous, current);
        states.put(playerId, current);
        return fired;
    }

    public MoveState state(UUID playerId) {
        return states.get(playerId);
    }

    public void remove(UUID playerId) {
        states.remove(playerId);
    }
}
