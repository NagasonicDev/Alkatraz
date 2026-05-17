package me.nagasonic.alkatraz.nms.entity.goals;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * GoalKeepSpellRange
 *
 * Keeps a magic mob within a comfortable casting band relative to its attack
 * target. The mob will:
 *
 *  • Back away if the target is closer than {@code minDistance}.
 *  • Walk toward the target if it is farther than {@code maxDistance}.
 *  • Stop moving and hold position while inside the preferred band, letting
 *    {@link CastSpellGoal} fire.
 *
 * This goal runs in parallel with GoalCastSpell (they control different
 * flags), so the mob repositions and animates its casting simultaneously.
 *
 * Priority guidance (lower number = higher priority):
 *   1 — SwimGoal
 *   2 — GoalKeepSpellRange   ← insert here
 *   3 — GoalCastSpell
 *   4 — vanilla look/stroll goals
 */
public class KeepSpellRangeGoal extends Goal {

    private final Mob   mob;
    private final double minDistance;   // back away below this
    private final double maxDistance;   // close in above this
    private final double speed;

    /** How many ticks between path recalculations. */
    private static final int PATH_RECALC_INTERVAL = 10;
    private int recalcTimer = 0;

    public KeepSpellRangeGoal(Mob mob, double minDistance, double maxDistance, double speed) {
        this.mob         = mob;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.speed       = speed;

        // MOVE — we reposition; LOOK is intentionally left out so GoalCastSpell
        // can own the look direction while we handle movement.
        setFlags(EnumSet.of(Flag.MOVE));
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        double distSq = mob.distanceToSqr(target);
        // Active whenever outside the band
        return distSq < (minDistance * minDistance) || distSq > (maxDistance * maxDistance);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        double distSq = mob.distanceToSqr(target);
        // Keep running until firmly inside the preferred band
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

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        recalcTimer--;
        if (recalcTimer > 0) return;
        recalcTimer = PATH_RECALC_INTERVAL;

        double distSq = mob.distanceToSqr(target);

        if (distSq < minDistance * minDistance) {
            // Too close — strafe backward away from target
            strafeAwayFrom(target);
        } else {
            // Too far — path toward target, stopping at maxDistance
            moveTowardTarget(target);
        }
    }

    // -------------------------------------------------------------------------
    // Movement helpers
    // -------------------------------------------------------------------------

    /**
     * Moves directly away from the target by projecting a point behind the mob
     * and pathing to it. Falls back to a raw velocity nudge if navigation fails.
     */
    private void strafeAwayFrom(LivingEntity target) {
        Vec3 toMob = mob.position().subtract(target.position()).normalize();
        Vec3 retreatPos = mob.position().add(toMob.scale(minDistance + 1.0));

        PathNavigation nav = mob.getNavigation();
        boolean pathed = nav.moveTo(retreatPos.x, retreatPos.y, retreatPos.z, speed);
        if (!pathed) {
            // Fallback: nudge velocity directly (works on flat ground)
            mob.setDeltaMovement(mob.getDeltaMovement().add(toMob.scale(0.15)));
        }
    }

    /** Paths toward the target, aiming for a point just inside maxDistance. */
    private void moveTowardTarget(LivingEntity target) {
        Vec3 toTarget = target.position().subtract(mob.position()).normalize();
        double desiredDist = (minDistance + maxDistance) / 2.0;
        Vec3 dest = target.position().subtract(toTarget.scale(desiredDist));

        mob.getNavigation().moveTo(dest.x, dest.y, dest.z, speed);
    }
}
