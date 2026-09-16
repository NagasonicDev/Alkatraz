package me.nagasonic.alkatraz.api.mobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Declarative description of a magic mob's goal-based AI behaviour (the
 * "Goal Brain"). An open, ordered list of {@link Entry entries}; each entry is
 * either an api {@link Goal} (version-agnostic, implemented in core or a
 * plugin) or a {@link NativeGoalSpec} (a vanilla goal description the version
 * module translates into its own NMS goal). Ordering is meaningful: a later
 * entry is registered at a higher priority number.
 *
 * <p>Applicability is decided in core ({@code me.nagasonic.alkatraz.mobs.ai.AiApplier});
 * modules only supply native leaf-builders via the AiSpecRegistry.
 *
 * <pre>
 *   private static final GoalBrain BRAIN = GoalBrain.builder()
 *       .addNative(1, new NativeGoalSpec.Float())
 *       .addGoal(2, new CastSpellGoal(new SpellCastConfig(6.0, 12.0, 14.0, 40)))
 *       .addNativeTarget(2, new NativeGoalSpec.NearestAttackableTarget(Player.class, true))
 *       .build();
 * </pre>
 */
public final class GoalBrain {

    /**
     * One goal registration.
     *
     * @param priority    vanilla goal priority (lower runs first)
     * @param goalOrSpec  an api {@link Goal} or a {@link NativeGoalSpec}
     * @param isTargetGoal whether this should be added to the target selector
     */
    public record Entry(int priority, Object goalOrSpec, boolean isTargetGoal) {}

    private final List<Entry> entries;

    private GoalBrain(List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    /** Unmodifiable view of the goal entries, in registration order. */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<Entry> entries = new ArrayList<>();

        private Builder() {}

        /** Adds an api {@link Goal} to the regular goal selector. */
        public Builder addGoal(int priority, Goal goal) {
            return add(priority, goal, false);
        }

        /** Adds an api {@link Goal} to the target selector. */
        public Builder addTargetGoal(int priority, Goal goal) {
            return add(priority, goal, true);
        }

        /** Adds a {@link NativeGoalSpec} to the regular goal selector. */
        public Builder addNative(int priority, NativeGoalSpec spec) {
            return add(priority, spec, false);
        }

        /** Adds a {@link NativeGoalSpec} to the target selector. */
        public Builder addNativeTarget(int priority, NativeGoalSpec spec) {
            return add(priority, spec, true);
        }

        private Builder add(int priority, Object goalOrSpec, boolean isTargetGoal) {
            entries.add(new Entry(priority, Objects.requireNonNull(goalOrSpec), isTargetGoal));
            return this;
        }

        public GoalBrain build() {
            return new GoalBrain(entries);
        }
    }
}