package me.nagasonic.alkatraz.nms_v26_R2.entity;

import me.nagasonic.alkatraz.mobs.MagicEntity;
import me.nagasonic.alkatraz.api.mobs.MobBrain;
import me.nagasonic.alkatraz.api.mobs.NativeGoalSpec;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

public class GoalBuilder {
    private GoalBuilder() {}

    public static void apply(Mob mob, MagicEntity magic, MobBrain brain) {
        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);

        NmsMobBrainContext ctx = new NmsMobBrainContext(mob);

        for (MobBrain.Entry entry : brain.entries()) {
            Goal goal;
            if (entry.goalOrSpec() instanceof NativeGoalSpec spec) {
                goal = NativeGoalFactory.build(spec, mob);
            } else if (entry.goalOrSpec() instanceof me.nagasonic.alkatraz.api.mobs.Goal apiGoal) {
                goal = new GoalBridge(apiGoal, ctx);
            } else {
                throw new IllegalArgumentException("Unknown goal type: " + entry.goalOrSpec().getClass());
            }

            if (entry.isTargetGoal()) {
                mob.targetSelector.addGoal(entry.priority(), goal);
            } else {
                mob.goalSelector.addGoal(entry.priority(), goal);
            }
        }
    }
}
