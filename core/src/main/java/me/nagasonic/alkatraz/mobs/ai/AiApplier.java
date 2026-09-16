package me.nagasonic.alkatraz.mobs.ai;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.mobs.Goal;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.api.mobs.NativeGoalSpec;
import me.nagasonic.alkatraz.nms.NMS;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/**
 * The single orchestrator that turns a declarative {@link GoalBrain} into
 * applied native goals. Lives in core: it wipes the selectors, iterates the
 * brain entries, dispatches each entry to either the {@link AiSpecRegistry}
 * (native specs) or the module's {@link NMS#bridgeCustomGoal custom-goal
 * bridge}, and attaches the result to the correct selector.
 *
 * <p>Application targets: a live Bukkit mob ({@link #applyGoalBrain(LivingEntity,
 * GoalBrain)}) or an already-unwrapped native handle ({@link #applyGoalBrain(Object,
 * GoalBrain)}, used by the NMS mob definitions at construction time).
 */
public final class AiApplier {

    private AiApplier() {}

    /** Applies a goal brain to a live Bukkit mob, replacing all current goals. */
    public static void applyGoalBrain(LivingEntity entity, GoalBrain brain) {
        if (!(entity instanceof Mob)) return;
        NMS nms = Alkatraz.getNms();
        if (nms == null) return;
        applyGoalBrain(nms.unwrapMob(entity), brain);
    }

    /**
     * Applies a goal brain to an already-unwrapped native mob handle (passes
     * through the NMS primitives, which cast the {@code Object} back to the
     * version's {@code net.minecraft.world.entity.Mob}).
     */
    public static void applyGoalBrain(Object nativeMob, GoalBrain brain) {
        if (nativeMob == null || brain == null) return;
        NMS nms = Alkatraz.getNms();
        if (nms == null) return;
        nms.wipeGoals(nativeMob);
        for (GoalBrain.Entry entry : brain.entries()) {
            Object goal;
            Object goalOrSpec = entry.goalOrSpec();
            if (goalOrSpec instanceof NativeGoalSpec spec) {
                goal = AiSpecRegistry.build(nativeMob, spec);
            } else if (goalOrSpec instanceof Goal apiGoal) {
                goal = nms.bridgeCustomGoal(nativeMob, apiGoal);
            } else {
                throw new IllegalArgumentException("Unknown goal type: " + goalOrSpec.getClass());
            }
            nms.addGoal(nativeMob, goal, entry.priority(), entry.isTargetGoal());
        }
    }
}