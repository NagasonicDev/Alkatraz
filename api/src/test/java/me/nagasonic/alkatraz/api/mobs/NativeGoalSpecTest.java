package me.nagasonic.alkatraz.api.mobs;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeGoalSpecTest {

    @Test
    void allSpecs_constructAndExposeTheirFields() {
        assertNotNull(new NativeGoalSpec.Float());
        assertEquals(1.1, new NativeGoalSpec.MeleeAttack(1.1, true).speed());
        assertTrue(new NativeGoalSpec.MeleeAttack(1.1, true).pauseWhenMobIdle());
        assertEquals(0.6, new NativeGoalSpec.WaterAvoidingRandomStroll(0.6).speed());
        assertEquals(8.0f, new NativeGoalSpec.LookAtPlayer(8.0f).range());
        assertNotNull(new NativeGoalSpec.RandomLookAround());
        assertNotNull(new NativeGoalSpec.HurtByTarget());
        assertEquals(16.0, new NativeGoalSpec.Panic(16.0).speed());

        NativeGoalSpec.NearestAttackableTarget target =
                new NativeGoalSpec.NearestAttackableTarget(LivingEntity.class, false);
        assertEquals(LivingEntity.class, target.targetClass());
        assertFalse(target.mustSee());

        NativeGoalSpec.AvoidEntity avoid = new NativeGoalSpec.AvoidEntity(LivingEntity.class, 7.0f, 1.2, 1.5);
        assertEquals(LivingEntity.class, avoid.avoidClass());
        assertEquals(7.0f, avoid.maxDist());
        assertEquals(1.2, avoid.walkSpeed());
        assertEquals(1.5, avoid.sprintSpeed());
    }
}