package me.nagasonic.alkatraz.nms_v1_20_R4.entity;

import me.nagasonic.alkatraz.api.mobs.MobBrainContext;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class NmsMobBrainContext implements MobBrainContext {

    private final Mob mob;

    public NmsMobBrainContext(Mob mob) {
        this.mob = mob;
    }

    @Override
    public org.bukkit.entity.Mob self() {
        return (org.bukkit.entity.Mob) mob.getBukkitEntity();
    }

    @Override
    public LivingEntity getTarget() {
        net.minecraft.world.entity.LivingEntity nmsTarget = mob.getTarget();
        return nmsTarget != null ? (LivingEntity) nmsTarget.getBukkitEntity() : null;
    }

    @Override
    public void setTarget(LivingEntity target) {
        mob.setTarget(target != null ? (net.minecraft.world.entity.LivingEntity) ((org.bukkit.craftbukkit.v1_20_R4.entity.CraftLivingEntity) target).getHandle() : null);
    }

    @Override
    public boolean moveTo(Location destination, double speed) {
        PathNavigation nav = mob.getNavigation();
        return nav.moveTo(destination.getX(), destination.getY(), destination.getZ(), speed);
    }

    @Override
    public void strafeAwayFrom(Location target, double keepDistance, double speed) {
        Vec3 selfPos = mob.position();
        Vec3 targetPos = new Vec3(target.getX(), target.getY(), target.getZ());
        Vec3 toMob = selfPos.subtract(targetPos).normalize();
        Vec3 retreatPos = selfPos.add(toMob.scale(keepDistance + 1.0));
        PathNavigation nav = mob.getNavigation();
        boolean pathed = nav.moveTo(retreatPos.x, retreatPos.y, retreatPos.z, speed);
        if (!pathed) {
            mob.setDeltaMovement(mob.getDeltaMovement().add(toMob.scale(0.15)));
        }
    }

    @Override
    public Vector getVelocity() {
        Vec3 v = mob.getDeltaMovement();
        return new Vector(v.x, v.y, v.z);
    }

    @Override
    public void setVelocity(Vector velocity) {
        mob.setDeltaMovement(new Vec3(velocity.getX(), velocity.getY(), velocity.getZ()));
    }

    @Override
    public boolean hasLineOfSight(Entity entity) {
        if (entity instanceof org.bukkit.craftbukkit.v1_20_R4.entity.CraftEntity craftEntity) {
            return mob.hasLineOfSight(craftEntity.getHandle());
        }
        return false;
    }

    @Override
    public void lookAt(Location eyeLocation) {
        mob.getLookControl().setLookAt(eyeLocation.getX(), eyeLocation.getY(), eyeLocation.getZ(),
                30.0F, mob.getMaxHeadXRot());
    }

    @Override
    public boolean isNavigating() {
        return !mob.getNavigation().isDone();
    }

    @Override
    public double distanceSq(Entity entity) {
        return mob.distanceToSqr(((org.bukkit.craftbukkit.v1_20_R4.entity.CraftEntity) entity).getHandle());
    }

    @Override
    public ItemStack getMainHandItem() {
        return org.bukkit.craftbukkit.v1_20_R4.inventory.CraftItemStack.asBukkitCopy(mob.getMainHandItem());
    }

    @Override
    public void stopNavigating() {
        mob.getNavigation().stop();
    }
}
