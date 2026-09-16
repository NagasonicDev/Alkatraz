package me.nagasonic.alkatraz.api.ai.control;

import me.nagasonic.alkatraz.api.ai.AiSpec;

/**
 * Jump-control vocabulary: whether a mob uses the vanilla random jump control
 * or none. The actual jump chance is carried by {@link ControlProfile}'s
 * {@code jumpChance} slot.
 */
public enum JumpControlSpec implements AiSpec {
    RANDOM,
    NONE
}
