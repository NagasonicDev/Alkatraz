package me.nagasonic.alkatraz.mobs.goals;

import me.nagasonic.alkatraz.api.mobs.Goal;
import me.nagasonic.alkatraz.api.mobs.GoalFlag;
import me.nagasonic.alkatraz.api.mobs.MobBrainContext;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.EnumSet;

public class KeepSpellRangeGoal implements Goal {

    private static final int PATH_RECALC_INTERVAL = 10;

    private final double minDistance;
    private final double maxDistance;
    private final double speed;
    private int recalcTimer = 0;

    public KeepSpellRangeGoal(double minDistance, double maxDistance, double speed) {
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.speed = speed;
    }

    @Override
    public EnumSet<GoalFlag> flags() {
        return EnumSet.of(GoalFlag.MOVE);
    }

    @Override
    public boolean canStart(MobBrainContext ctx) {
        LivingEntity target = ctx.getTarget();
        if (target == null || target.isDead()) return false;
        double distSq = ctx.distanceSq(target);
        return distSq < minDistance * minDistance || distSq > maxDistance * maxDistance;
    }

    @Override
    public boolean shouldContinue(MobBrainContext ctx) {
        return canStart(ctx);
    }

    @Override
    public void start(MobBrainContext ctx) {
        recalcTimer = 0;
    }

    @Override
    public void stop(MobBrainContext ctx) {
        ctx.stopNavigating();
    }

    @Override
    public void tick(MobBrainContext ctx) {
        LivingEntity target = ctx.getTarget();
        if (target == null) return;
        recalcTimer--;
        if (recalcTimer > 0) return;
        recalcTimer = PATH_RECALC_INTERVAL;
        double distSq = ctx.distanceSq(target);
        if (distSq < minDistance * minDistance) {
            ctx.strafeAwayFrom(target.getLocation(), minDistance, speed);
        } else {
            moveTowardTarget(ctx, target);
        }
    }

    private void moveTowardTarget(MobBrainContext ctx, LivingEntity target) {
        Location selfLoc = ctx.self().getLocation();
        Location targetLoc = target.getLocation();
        Vector toTarget = targetLoc.toVector().subtract(selfLoc.toVector()).normalize();
        double desiredDist = (minDistance + maxDistance) / 2.0;
        Location dest = targetLoc.clone().subtract(toTarget.multiply(desiredDist));
        ctx.moveTo(dest, speed);
    }
}
