package me.nagasonic.alkatraz.nms_v1_20_R1.entity;

import me.nagasonic.alkatraz.api.mobs.NativeGoalSpec;
import me.nagasonic.alkatraz.mobs.ai.AiSpecRegistry;
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

/**
 * Registers every supported {@link NativeGoalSpec} leaf-builder into the core
 * {@link AiSpecRegistry}. This is the module's only goal-related job; all
 * orchestration lives in core ({@code AiApplier}).
 */
public final class AiSpecRegistration {

    private AiSpecRegistration() {}

    public static void registerAll() {
        AiSpecRegistry.register(NativeGoalSpec.Float.class,
                (mob, spec) -> new FloatGoal((Mob) mob));

        AiSpecRegistry.register(NativeGoalSpec.MeleeAttack.class,
                (mob, spec) -> new MeleeAttackGoal((PathfinderMob) mob,
                        spec.speed(), spec.pauseWhenMobIdle()));

        AiSpecRegistry.register(NativeGoalSpec.WaterAvoidingRandomStroll.class,
                (mob, spec) -> new WaterAvoidingRandomStrollGoal((PathfinderMob) mob, spec.speed()));

        AiSpecRegistry.register(NativeGoalSpec.LookAtPlayer.class,
                (mob, spec) -> new LookAtPlayerGoal((Mob) mob, Player.class, spec.range()));

        AiSpecRegistry.register(NativeGoalSpec.RandomLookAround.class,
                (mob, spec) -> new RandomLookAroundGoal((Mob) mob));

        AiSpecRegistry.register(NativeGoalSpec.HurtByTarget.class,
                (mob, spec) -> new HurtByTargetGoal((PathfinderMob) mob));

        AiSpecRegistry.register(NativeGoalSpec.NearestAttackableTarget.class,
                (mob, spec) -> new NearestAttackableTargetGoal<>((Mob) mob,
                        mapEntityClass(spec.targetClass()), spec.mustSee()));

        AiSpecRegistry.register(NativeGoalSpec.Panic.class,
                (mob, spec) -> new PanicGoal((PathfinderMob) mob, spec.speed()));

        AiSpecRegistry.register(NativeGoalSpec.AvoidEntity.class,
                (mob, spec) -> new AvoidEntityGoal<>((PathfinderMob) mob,
                        mapEntityClass(spec.avoidClass()),
                        spec.maxDist(), spec.walkSpeed(), spec.sprintSpeed()));
    }

    private static Class<? extends net.minecraft.world.entity.LivingEntity> mapEntityClass(
            Class<? extends org.bukkit.entity.LivingEntity> bukkitClass) {
        if (bukkitClass == org.bukkit.entity.Player.class) return net.minecraft.world.entity.player.Player.class;
        if (bukkitClass == org.bukkit.entity.AbstractVillager.class) return net.minecraft.world.entity.npc.AbstractVillager.class;
        if (bukkitClass == org.bukkit.entity.IronGolem.class) return net.minecraft.world.entity.animal.IronGolem.class;
        return net.minecraft.world.entity.player.Player.class;
    }
}