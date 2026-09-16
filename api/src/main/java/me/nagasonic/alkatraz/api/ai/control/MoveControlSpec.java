package me.nagasonic.alkatraz.api.ai.control;

import me.nagasonic.alkatraz.api.ai.AiSpec;

/**
 * Describes the movement-control actuator a mob should use. Vanilla default is
 * kept when a profile omits this slot.
 */
public sealed interface MoveControlSpec extends AiSpec
        permits MoveControlSpec.Flying, MoveControlSpec.Water, MoveControlSpec.Generic {

    /** Flying movement that can optionally keep the mob afloat. */
    record Flying(double speed, boolean canFloat) implements MoveControlSpec {
        public Flying {
            if (speed < 0) throw new IllegalArgumentException("speed must be non-negative");
        }
    }

    /** Swimming/water movement at the given speed. */
    record Water(double speed) implements MoveControlSpec {
        public Water {
            if (speed < 0) throw new IllegalArgumentException("speed must be non-negative");
        }
    }

    /** Generic ground movement at the given speed. */
    record Generic(double speed) implements MoveControlSpec {
        public Generic {
            if (speed < 0) throw new IllegalArgumentException("speed must be non-negative");
        }
    }
}
