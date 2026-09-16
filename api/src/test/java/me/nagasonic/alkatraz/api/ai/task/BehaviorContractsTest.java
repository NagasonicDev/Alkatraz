package me.nagasonic.alkatraz.api.ai.task;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BehaviorContractsTest {

    private static class FakeBehavior implements Behavior {
        @Override
        public Map<MemoryKey<?>, MemoryStatus> memoryRequirements() {
            return Map.of(MemoryKey.IS_HURT, MemoryStatus.PRESENT, MemoryKey.ATTACK_TARGET, MemoryStatus.ABSENT);
        }

        @Override
        public boolean canStart(TaskBrainContext ctx) {
            return true;
        }

        @Override
        public boolean shouldContinue(TaskBrainContext ctx) {
            return false;
        }

        @Override
        public void tick(TaskBrainContext ctx) {
        }

        @Override
        public int minDuration() {
            return 0;
        }

        @Override
        public int maxDuration() {
            return 10;
        }
    }

    @Test
    void behavior_defaultLifecycleHooks_areNoOps() {
        AtomicBoolean stopCalled = new AtomicBoolean(false);
        Behavior b = new FakeBehavior() {
            @Override
            public boolean canStart(TaskBrainContext ctx) {
                return true;
            }

            @Override
            public void stop(TaskBrainContext ctx) {
                stopCalled.set(true);
            }
        };
        assertDoesNotThrow(() -> b.start(null));
        assertFalse(b.shouldContinue(null));
        b.stop(null);
        assertTrue(stopCalled.get());
    }

    @Test
    void customBehavior_isRecognisedAsBehavior() {
        CustomBehavior cb = new CustomBehavior() {
            @Override
            public Map<MemoryKey<?>, MemoryStatus> memoryRequirements() {
                return Map.of();
            }

            @Override
            public boolean canStart(TaskBrainContext ctx) {
                return true;
            }

            @Override
            public void tick(TaskBrainContext ctx) {
            }

            @Override
            public int minDuration() {
                return 0;
            }

            @Override
            public int maxDuration() {
                return 1;
            }
        };
        assertTrue(cb instanceof Behavior);
    }

    @Test
    void nativeBehaviorSpec_records_roundTrip() {
        NativeBehaviorSpec.LookAtTargetSink look = new NativeBehaviorSpec.LookAtTargetSink(8, 30);
        assertEquals(8, look.maxLookDistance());
        assertEquals(30, look.probability());

        NativeBehaviorSpec.MoveToTargetSink move = new NativeBehaviorSpec.MoveToTargetSink(2, 1);
        assertEquals(2, move.closeEnough());
        assertEquals(1, move.speed());

        NativeBehaviorSpec.SleepInBed sleep = new NativeBehaviorSpec.SleepInBed();
        assertNotNull(sleep);

        NativeBehaviorSpec.RunOne runOne = new NativeBehaviorSpec.RunOne(List.of(look, move));
        assertEquals(2, runOne.options().size());

        NativeBehaviorSpec.RunSometimes sometimes = new NativeBehaviorSpec.RunSometimes(40, look);
        assertEquals(40, sometimes.chance());
        assertSame(look, sometimes.delegate());

        NativeBehaviorSpec.SetEntityLookTarget setLook = new NativeBehaviorSpec.SetEntityLookTarget(12, 20);
        assertEquals(12, setLook.maxDistance());
        assertEquals(20, setLook.probability());

        NativeBehaviorSpec.StartAttacking start = new NativeBehaviorSpec.StartAttacking(ctx -> Optional.of(ctx.getEntity()));
        assertNotNull(start.func());

        NativeBehaviorSpec.StopAttackingIfTargetInvalid stop = new NativeBehaviorSpec.StopAttackingIfTargetInvalid(120);
        assertEquals(120, stop.unknownExpiry());
    }

    @Test
    void nativeBehaviorSpec_negativeFields_throw() {
        assertThrows(IllegalArgumentException.class, () -> new NativeBehaviorSpec.LookAtTargetSink(-1, 30));
        assertThrows(IllegalArgumentException.class, () -> new NativeBehaviorSpec.LookAtTargetSink(8, -1));
        assertThrows(IllegalArgumentException.class, () -> new NativeBehaviorSpec.MoveToTargetSink(-2, 1));
        assertThrows(IllegalArgumentException.class, () -> new NativeBehaviorSpec.MoveToTargetSink(2, -1));
        assertThrows(IllegalArgumentException.class, () -> new NativeBehaviorSpec.RunSometimes(-1, new NativeBehaviorSpec.SleepInBed()));
        assertThrows(IllegalArgumentException.class, () -> new NativeBehaviorSpec.SetEntityLookTarget(-12, 20));
        assertThrows(IllegalArgumentException.class, () -> new NativeBehaviorSpec.SetEntityLookTarget(12, -20));
        assertThrows(IllegalArgumentException.class, () -> new NativeBehaviorSpec.StopAttackingIfTargetInvalid(-1));
    }

    @Test
    void prioritizedBehavior_acceptsBehaviorAndNativeSpec() {
        PrioritizedBehavior fromBehavior = new PrioritizedBehavior(3, new FakeBehavior(), -1);
        assertEquals(3, fromBehavior.priority());
        assertEquals(-1, fromBehavior.chancePerRun());
        assertTrue(fromBehavior.behaviorOrSpec() instanceof Behavior);

        PrioritizedBehavior fromSpec = new PrioritizedBehavior(1, new NativeBehaviorSpec.SleepInBed(), 50);
        assertTrue(fromSpec.behaviorOrSpec() instanceof NativeBehaviorSpec);
        assertEquals(50, fromSpec.chancePerRun());
    }

    @Test
    void prioritizedBehavior_rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class, () -> new PrioritizedBehavior(-1, new FakeBehavior(), -1));
        assertThrows(NullPointerException.class, () -> new PrioritizedBehavior(1, null, -1));
        assertThrows(IllegalArgumentException.class, () -> new PrioritizedBehavior(1, new Object(), -1));
        assertThrows(IllegalArgumentException.class, () -> new PrioritizedBehavior(1, new FakeBehavior(), 101));
        assertThrows(IllegalArgumentException.class, () -> new PrioritizedBehavior(1, new FakeBehavior(), -2));
    }

    @Test
    void timedMemory_validatesArguments() {
        assertThrows(NullPointerException.class, () -> new TimedMemory(null, 10));
        assertThrows(IllegalArgumentException.class, () -> new TimedMemory(MemoryKey.IS_HURT, -1));
        TimedMemory m = new TimedMemory(MemoryKey.IS_HURT, 200);
        assertEquals(MemoryKey.IS_HURT, m.key());
        assertEquals(200, m.expireTicks());
    }
}
