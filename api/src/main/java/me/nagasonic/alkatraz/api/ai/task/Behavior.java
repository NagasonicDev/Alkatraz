package me.nagasonic.alkatraz.api.ai.task;

import java.util.Map;

/**
 * A runnable unit of behaviour inside an {@link ActivitySpec}. The lifecycle is
 * modelled on vanilla goals but expressed purely against the
 * {@link TaskBrainContext} blackboard, so a behavior can be written once in a
 * plugin or in core with zero NMS knowledge.
 */
public interface Behavior {

    /** Memory states this behavior requires before it may run. */
    Map<MemoryKey<?>, MemoryStatus> memoryRequirements();

    /** Whether this behavior may start at this moment. */
    boolean canStart(TaskBrainContext ctx);

    /** Lifecycle hook; no-op by default. */
    default void start(TaskBrainContext ctx) {}

    /** Whether a running behavior should keep running. Defaults to {@link #canStart}. */
    default boolean shouldContinue(TaskBrainContext ctx) {
        return canStart(ctx);
    }

    /** Executed every tick while running. */
    void tick(TaskBrainContext ctx);

    /** Lifecycle hook; no-op by default. */
    default void stop(TaskBrainContext ctx) {}

    /** Minimum ticks this behavior should stay active. */
    int minDuration();

    /** Maximum ticks this behavior may stay active. */
    int maxDuration();
}
