package me.nagasonic.alkatraz.nms.entity.goals;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class KeepSpellRangeGoal extends Goal {

    private final Mob   mob;
    private final double minDistance;
    private final double maxDistance;
    private final double speed;

    private static final int PATH_RECALC_INTERVAL = 10;
    private int recalcTimer = 0;

    public KeepSpellRangeGoal(Mob mob, double minDistance, double maxDistance, double speed) {
        this.mob         = mob;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.speed       = speed;

        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        double distSq = mob.distanceToSqr(target);
        return distSq < (minDistance * minDistance) || distSq > (maxDistance * maxDistance);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        double distSq = mob.distanceToSqr(target);
        return distSq < (minDistance * minDistance) || distSq > (maxDistance * maxDistance);
    }

    @Override
    public void start() {
        recalcTimer = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        recalcTimer--;
        if (recalcTimer > 0) return;
        recalcTimer = PATH_RECALC_INTERVAL;

        double distSq = mob.distanceToSqr(target);

        if (distSq < minDistance * minDistance) {
            strafeAwayFrom(target);
        } else {
            moveTowardTarget(target);
        }
    }

    private void strafeAwayFrom(LivingEntity target) {
        Vec3 toMob = mob.position().subtract(target.position()).normalize();
        Vec3 retreatPos = mob.position().add(toMob.scale(minDistance + 1.0));

        PathNavigation nav = mob.getNavigation();
        boolean pathed = nav.moveTo(retreatPos.x, retreatPos.y, retreatPos.z, speed);
        if (!pathed) {
            mob.setDeltaMovement(mob.getDeltaMovement().add(toMob.scale(0.15)));
        }
    }

    private void moveTowardTarget(LivingEntity target) {
        Vec3 toTarget = target.position().subtract(mob.position()).normalize();
        double desiredDist = (minDistance + maxDistance) / 2.0;
        Vec3 dest = target.position().subtract(toTarget.scale(desiredDist));

        mob.getNavigation().moveTo(dest.x, dest.y, dest.z, speed);
    }
}
