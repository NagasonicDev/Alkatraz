package me.nagasonic.alkatraz.api.ai.task;

import me.nagasonic.alkatraz.api.ai.AiSpec;

import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Declarative descriptions of vanilla mob behaviors. Each version module later
 * registers a leaf-builder for these specs (like {@code NativeGoalSpec} today),
 * but the description itself is fully version-agnostic and Bukkit-typed.
 */
public sealed interface NativeBehaviorSpec extends AiSpec
        permits NativeBehaviorSpec.LookAtTargetSink,
        NativeBehaviorSpec.MoveToTargetSink,
        NativeBehaviorSpec.SleepInBed,
        NativeBehaviorSpec.RunOne,
        NativeBehaviorSpec.RunSometimes,
        NativeBehaviorSpec.SetEntityLookTarget,
        NativeBehaviorSpec.StartAttacking,
        NativeBehaviorSpec.StopAttackingIfTargetInvalid {

    /** Looks at the target from up to {@code maxLookDistance} blocks away, with the given chance per run. */
    record LookAtTargetSink(int maxLookDistance, int probability) implements NativeBehaviorSpec {
        public LookAtTargetSink {
            if (maxLookDistance < 0 || probability < 0) throw new IllegalArgumentException("LookAtTargetSink fields must be non-negative");
        }
    }

    /** Paths toward the target until within {@code closeEnough} blocks, at the given speed. */
    record MoveToTargetSink(int closeEnough, int speed) implements NativeBehaviorSpec {
        public MoveToTargetSink {
            if (closeEnough < 0 || speed < 0) throw new IllegalArgumentException("MoveToTargetSink fields must be non-negative");
        }
    }

    /** Tries to walk to and sleep in a nearby bed. */
    record SleepInBed() implements NativeBehaviorSpec {}

    /** Runs exactly one option from the list, picked at runtime. */
    record RunOne(List<NativeBehaviorSpec> options) implements NativeBehaviorSpec {
        public RunOne {
            options = List.copyOf(options);
        }
    }

    /** Runs {@code delegate} with the given chance (0..100) per run; otherwise runs nothing. */
    record RunSometimes(int chance, NativeBehaviorSpec delegate) implements NativeBehaviorSpec {
        public RunSometimes {
            if (chance < 0) throw new IllegalArgumentException("RunSometimes chance must be non-negative");
            if (delegate == null) throw new NullPointerException("delegate");
        }
    }

    /** Sets the entity look target within {@code maxDistance}, with the given chance per run. */
    record SetEntityLookTarget(int maxDistance, int probability) implements NativeBehaviorSpec {
        public SetEntityLookTarget {
            if (maxDistance < 0 || probability < 0) throw new IllegalArgumentException("SetEntityLookTarget fields must be non-negative");
        }
    }

    /** Starts attacking whatever {@code func} selects; the factory hook runs at build time in the bridge. */
    record StartAttacking(Function<TaskBrainContext, Optional<LivingEntity>> func) implements NativeBehaviorSpec {
        public StartAttacking {
            if (func == null) throw new NullPointerException("func");
        }
    }

    /** Stops attacking if the current target becomes invalid for longer than {@code unknownExpiry} ticks. */
    record StopAttackingIfTargetInvalid(int unknownExpiry) implements NativeBehaviorSpec {
        public StopAttackingIfTargetInvalid {
            if (unknownExpiry < 0) throw new IllegalArgumentException("unknownExpiry must be non-negative");
        }
    }
}
