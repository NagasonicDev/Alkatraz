package me.nagasonic.alkatraz.nms_v1_19_R3.entity;

import me.nagasonic.alkatraz.api.mobs.NativeGoalSpec;
import me.nagasonic.alkatraz.mobs.NativeGoalRegistry;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

public final class NativeGoalFactory {

    private NativeGoalFactory() {}

    public static net.minecraft.world.entity.ai.goal.Goal build(NativeGoalSpec spec, Mob mob) {
        if (spec instanceof NativeGoalSpec.Float) {
            return new FloatGoal(mob);
        }
        if (spec instanceof NativeGoalSpec.MeleeAttack ma) {
            return new MeleeAttackGoal((PathfinderMob) mob, ma.speed(), ma.pauseWhenMobIdle());
        }
        if (spec instanceof NativeGoalSpec.WaterAvoidingRandomStroll w) {
            return new WaterAvoidingRandomStrollGoal((PathfinderMob) mob, w.speed());
        }
        if (spec instanceof NativeGoalSpec.LookAtPlayer lp) {
            return new LookAtPlayerGoal(mob, Player.class, lp.range());
        }
        if (spec instanceof NativeGoalSpec.RandomLookAround) {
            return new RandomLookAroundGoal(mob);
        }
        if (spec instanceof NativeGoalSpec.HurtByTarget) {
            return new HurtByTargetGoal((PathfinderMob) mob);
        }
        if (spec instanceof NativeGoalSpec.NearestAttackableTarget nat) {
            Class<? extends net.minecraft.world.entity.LivingEntity> nmsClass = mapEntityClass(nat.targetClass());
            return new NearestAttackableTargetGoal<>(mob, nmsClass, nat.mustSee());
        }
        if (spec instanceof NativeGoalSpec.Panic p) {
            return new PanicGoal((PathfinderMob) mob, p.speed());
        }
        if (spec instanceof NativeGoalSpec.AvoidEntity ae) {
            Class<? extends net.minecraft.world.entity.LivingEntity> nmsClass = mapEntityClass(ae.avoidClass());
            return new AvoidEntityGoal<>((PathfinderMob) mob, nmsClass, ae.maxDist(), ae.walkSpeed(), ae.sprintSpeed());
        }
        throw new IllegalArgumentException("Unknown NativeGoalSpec: " + spec.getClass().getSimpleName());
    }

    private static Class<? extends net.minecraft.world.entity.LivingEntity> mapEntityClass(Class<? extends org.bukkit.entity.LivingEntity> bukkitClass) {
        if (bukkitClass == org.bukkit.entity.Player.class) return net.minecraft.world.entity.player.Player.class;
        if (bukkitClass == org.bukkit.entity.AbstractVillager.class) return net.minecraft.world.entity.npc.AbstractVillager.class;
        if (bukkitClass == org.bukkit.entity.IronGolem.class) return net.minecraft.world.entity.animal.IronGolem.class;
        return net.minecraft.world.entity.player.Player.class;
    }

    public static void registerCoverage() {
        NativeGoalRegistry.markSupported(
                NativeGoalSpec.Float.class,
                NativeGoalSpec.MeleeAttack.class,
                NativeGoalSpec.WaterAvoidingRandomStroll.class,
                NativeGoalSpec.LookAtPlayer.class,
                NativeGoalSpec.RandomLookAround.class,
                NativeGoalSpec.HurtByTarget.class,
                NativeGoalSpec.NearestAttackableTarget.class,
                NativeGoalSpec.Panic.class,
                NativeGoalSpec.AvoidEntity.class
        );
    }
}