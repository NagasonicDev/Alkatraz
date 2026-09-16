package me.nagasonic.alkatraz.api.mobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoalBrainTest {

    private static final class NoOpGoal implements Goal {
        @Override
        public boolean canStart(MobBrainContext context) {
            return true;
        }

        @Override
        public void tick(MobBrainContext context) {
        }
    }

    @Test
    void builder_preservesRegistrationOrder() {
        GoalBrain brain = GoalBrain.builder()
                .addNative(1, new NativeGoalSpec.Float())
                .addGoal(2, new NoOpGoal())
                .addNative(3, new NativeGoalSpec.WaterAvoidingRandomStroll(0.6))
                .build();
        assertEquals(3, brain.entries().size());
        assertEquals(1, brain.entries().get(0).priority());
        assertTrue(brain.entries().get(0).goalOrSpec() instanceof NativeGoalSpec);
        assertFalse(brain.entries().get(0).isTargetGoal());
        assertTrue(brain.entries().get(1).goalOrSpec() instanceof Goal);
        assertEquals(3, brain.entries().get(2).priority());
    }

    @Test
    void targetMethods_flagEntriesAsTargets() {
        GoalBrain brain = GoalBrain.builder()
                .addTargetGoal(2, new NoOpGoal())
                .addNativeTarget(1, new NativeGoalSpec.NearestAttackableTarget(org.bukkit.entity.Player.class, true))
                .build();
        assertTrue(brain.entries().get(0).isTargetGoal());
        assertTrue(brain.entries().get(1).isTargetGoal());
    }

    @Test
    void entriesView_isUnmodifiable() {
        GoalBrain brain = GoalBrain.builder().addNative(1, new NativeGoalSpec.Float()).build();
        assertThrows(UnsupportedOperationException.class, () -> brain.entries().remove(0));
        assertThrows(UnsupportedOperationException.class, () -> brain.entries().add(
                new GoalBrain.Entry(5, new NoOpGoal(), false)));
    }

    @Test
    void nullArguments_areRejected() {
        assertThrows(NullPointerException.class, () -> GoalBrain.builder().addGoal(1, null));
        assertThrows(NullPointerException.class, () -> GoalBrain.builder().addNative(1, null));
        assertThrows(NullPointerException.class, () -> GoalBrain.builder().addTargetGoal(1, null));
        assertThrows(NullPointerException.class, () -> GoalBrain.builder().addNativeTarget(1, null));
    }
}