package me.nagasonic.alkatraz.api.ai.control;

import me.nagasonic.alkatraz.api.ai.AiSpec;

/**
 * Describes the look-control actuator a mob should use.
 */
public sealed interface LookControlSpec extends AiSpec
        permits LookControlSpec.BodyRotation, LookControlSpec.Vanilla {

    /** Rotates the body toward movement direction with the given max yaw change per tick. */
    record BodyRotation(int maxYawChange) implements LookControlSpec {
        public BodyRotation {
            if (maxYawChange < 0) throw new IllegalArgumentException("maxYawChange must be non-negative");
        }
    }

    /** Keep the vanilla look control. */
    record Vanilla() implements LookControlSpec {}
}
