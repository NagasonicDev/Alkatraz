package me.nagasonic.alkatraz.api.mobs;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class GoalTest {

    private static final class TestGoal implements Goal {
        final boolean canStart;

        TestGoal(boolean canStart) {
            this.canStart = canStart;
        }

        @Override
        public boolean canStart(MobBrainContext context) {
            return canStart;
        }

        @Override
        public void tick(MobBrainContext context) {
        }
    }

    @Test
    void shouldContinue_defaultsToCanStart() {
        assertTrue(new TestGoal(true).shouldContinue(null));
        assertFalse(new TestGoal(false).shouldContinue(null));
    }

    @Test
    void startAndStop_areNoOpsByDefault() {
        TestGoal g = new TestGoal(true);
        assertDoesNotThrow(() -> g.start(null));
        assertDoesNotThrow(() -> g.stop(null));
    }

    @Test
    void flags_defaultToEmpty() {
        assertEquals(EnumSet.noneOf(GoalFlag.class), new TestGoal(true).flags());
    }

    @Test
    void goalFlag_hasExactlyFourConstants() {
        assertArrayEquals(
                new GoalFlag[]{GoalFlag.MOVE, GoalFlag.LOOK, GoalFlag.JUMP, GoalFlag.TARGET},
                GoalFlag.values());
    }
}