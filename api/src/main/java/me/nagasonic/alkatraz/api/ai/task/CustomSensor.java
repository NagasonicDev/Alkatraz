package me.nagasonic.alkatraz.api.ai.task;

/**
 * A {@link Sensor} whose {@link #sense} logic is provided by the host plugin or
 * core module. Custom sensors run through the engine's general sensor runner
 * with no per-version NMS counterpart.
 */
public non-sealed interface CustomSensor extends Sensor {

    /** Performs one sensing pass, writing results into {@code ctx}. */
    void sense(TaskBrainContext ctx);
}
