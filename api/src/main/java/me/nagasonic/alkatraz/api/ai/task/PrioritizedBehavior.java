package me.nagasonic.alkatraz.api.ai.task;

/**
 * A behavior (or native behavior description) with a priority inside an
 * activity and an optional per-run chance. {@code chancePerRun} of {@code -1}
 * means runs every evaluation; otherwise it is a percentage {@code 0..100}.
 */
public record PrioritizedBehavior(int priority, Object behaviorOrSpec, int chancePerRun) {

    public PrioritizedBehavior {
        if (priority < 0) throw new IllegalArgumentException("priority must be non-negative");
        if (behaviorOrSpec == null) throw new NullPointerException("behaviorOrSpec");
        if (!(behaviorOrSpec instanceof Behavior) && !(behaviorOrSpec instanceof NativeBehaviorSpec)) {
            throw new IllegalArgumentException("behaviorOrSpec must be a Behavior or a NativeBehaviorSpec");
        }
        if (chancePerRun != -1 && (chancePerRun < 0 || chancePerRun > 100)) {
            throw new IllegalArgumentException("chancePerRun must be -1 or within 0..100");
        }
    }
}
