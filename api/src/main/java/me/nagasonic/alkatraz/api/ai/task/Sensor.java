package me.nagasonic.alkatraz.api.ai.task;

import me.nagasonic.alkatraz.api.ai.AiSpec;

/**
 * A memory producer. Sensors run on a cadence and write observations (e.g. who
 * is nearby) into the {@link TaskBrainContext} blackboard. Plain sensors take
 * parameters; {@link CustomSensor} implementations provide their own logic.
 */
public sealed interface Sensor extends AiSpec
        permits Sensor.NearestLivingEntities, Sensor.NearestPlayers, Sensor.HurtBy, CustomSensor {

    /** Writes the nearest {@code radius}-block living entities. */
    record NearestLivingEntities(int radius) implements Sensor {
        public NearestLivingEntities {
            if (radius < 0) throw new IllegalArgumentException("radius must be non-negative");
        }
    }

    /** Writes the nearest {@code radius}-block players. */
    record NearestPlayers(int radius) implements Sensor {
        public NearestPlayers {
            if (radius < 0) throw new IllegalArgumentException("radius must be non-negative");
        }
    }

    /** Records whether the mob took damage recently. */
    record HurtBy() implements Sensor {}
}
