package me.nagasonic.alkatraz.nms_v1_20_R4.entity;

import me.nagasonic.alkatraz.api.mobs.GoalFlag;
import me.nagasonic.alkatraz.api.mobs.MobBrainContext;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public final class GoalBridge extends Goal {

    private final me.nagasonic.alkatraz.api.mobs.Goal apiGoal;
    private final MobBrainContext ctx;

    public GoalBridge(me.nagasonic.alkatraz.api.mobs.Goal apiGoal, MobBrainContext ctx) {
        this.apiGoal = apiGoal;
        this.ctx = ctx;
        setFlags(mapFlags(apiGoal.flags()));
    }

    private static EnumSet<Flag> mapFlags(EnumSet<GoalFlag> apiFlags) {
        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
        if (apiFlags.contains(GoalFlag.MOVE)) flags.add(Flag.MOVE);
        if (apiFlags.contains(GoalFlag.LOOK)) flags.add(Flag.LOOK);
        if (apiFlags.contains(GoalFlag.JUMP)) flags.add(Flag.JUMP);
        if (apiFlags.contains(GoalFlag.TARGET)) flags.add(Flag.TARGET);
        return flags;
    }

    @Override
    public boolean canUse() {
        return apiGoal.canStart(ctx);
    }

    @Override
    public boolean canContinueToUse() {
        return apiGoal.shouldContinue(ctx);
    }

    @Override
    public void start() {
        apiGoal.start(ctx);
    }

    @Override
    public void stop() {
        apiGoal.stop(ctx);
    }

    @Override
    public void tick() {
        apiGoal.tick(ctx);
    }
}
