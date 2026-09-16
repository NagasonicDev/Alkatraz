package me.nagasonic.alkatraz.api.ai.task;

/**
 * Marker for a {@link Behavior} implemented by the host plugin or core module,
 * with no native counterpart. Custom behaviors run through the engine's general
 * bridge and never require a per-version NMS leaf-builder.
 */
public interface CustomBehavior extends Behavior {
}
