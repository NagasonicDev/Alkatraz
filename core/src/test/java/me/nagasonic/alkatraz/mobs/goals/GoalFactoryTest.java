package me.nagasonic.alkatraz.mobs.goals;

import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.api.mobs.Goal;
import me.nagasonic.alkatraz.api.mobs.MobBrainContext;
import me.nagasonic.alkatraz.api.mobs.NativeGoalSpec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GoalFactory} custom/native routing and defaults.
 */
class GoalFactoryTest {

    // ── helpers ──────────────────────────────────────────────────────────

    private static YamlConfiguration yaml(Object... kv) {
        YamlConfiguration cfg = new YamlConfiguration();
        for (int i = 0; i < kv.length; i += 2) {
            cfg.set((String) kv[i], kv[i + 1]);
        }
        return cfg;
    }

    private static GoalBrain build(ConfigurationSection section) {
        GoalBrain.Builder builder = GoalBrain.builder();
        GoalFactory.applyFromConfig(builder, section);
        return builder.build();
    }

    private static <T> T reflect(Object obj, String fieldName) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        T val = (T) f.get(obj);
        return val;
    }

    // ── 1. customCastSpellUsesRecordDefaults ──────────────────────────────

    @Test
    void customCastSpellUsesRecordDefaults() throws Exception {
        GoalBrain brain = build(yaml("type", "cast_spell"));
        assertEquals(1, brain.entries().size());
        GoalBrain.Entry entry = brain.entries().get(0);
        assertTrue(entry.goalOrSpec() instanceof CastSpellGoal,
                "Expected CastSpellGoal instance");
        assertEquals(1, entry.priority());
        assertFalse(entry.isTargetGoal());

        CastSpellGoal goal = (CastSpellGoal) entry.goalOrSpec();
        assertEquals(14.0, reflect(goal, "castRange"));
    }

    // ── 2. customCastSpellHonorsCustomValues ──────────────────────────────

    @Test
    void customCastSpellHonorsCustomValues() throws Exception {
        GoalBrain brain = build(yaml(
                "type", "cast_spell",
                "min-cast-dist", 5.0,
                "max-cast-dist", 12.0,
                "cast-range", 18.0,
                "cooldown-ticks", 60
        ));
        CastSpellGoal goal = (CastSpellGoal) brain.entries().get(0).goalOrSpec();
        assertEquals(18.0, reflect(goal, "castRange"));
        assertEquals(60, (int) reflect(goal, "globalCooldownTicks"));
    }

    // ── 3. customKeepSpellRangeDefaults ───────────────────────────────────

    @Test
    void customKeepSpellRangeDefaults() throws Exception {
        GoalBrain brain = build(yaml("type", "keep_spell_range"));
        GoalBrain.Entry entry = brain.entries().get(0);
        assertTrue(entry.goalOrSpec() instanceof KeepSpellRangeGoal);
        KeepSpellRangeGoal goal = (KeepSpellRangeGoal) entry.goalOrSpec();
        assertEquals(6.0, reflect(goal, "minDistance"));
        assertEquals(12.0, reflect(goal, "maxDistance"));
        assertEquals(1.1, reflect(goal, "speed"));
    }

    // ── 4. customKeepSpellRangeHonorsCustomValues ─────────────────────────

    @Test
    void customKeepSpellRangeHonorsCustomValues() throws Exception {
        GoalBrain brain = build(yaml(
                "type", "keep_spell_range",
                "min-distance", 8.0,
                "max-distance", 20.0,
                "speed", 1.4
        ));
        KeepSpellRangeGoal goal = (KeepSpellRangeGoal) brain.entries().get(0).goalOrSpec();
        assertEquals(8.0, reflect(goal, "minDistance"));
        assertEquals(20.0, reflect(goal, "maxDistance"));
        assertEquals(1.4, reflect(goal, "speed"));
    }

    // ── 5. applyFromConfigRoutesCustomTargetGoal ─────────────────────────

    @Test
    void applyFromConfigRoutesCustomTargetGoal() {
        GoalBrain brain = build(yaml("type", "cast_spell", "target", true));
        GoalBrain.Entry entry = brain.entries().get(0);
        assertEquals(1, entry.priority());
        assertTrue(entry.isTargetGoal());
        assertTrue(entry.goalOrSpec() instanceof CastSpellGoal);
    }

    // ── 6. applyFromConfigDefaultsPriorityToOneAndNonTarget ───────────────

    @Test
    void applyFromConfigDefaultsPriorityToOneAndNonTarget() {
        GoalBrain brain = build(yaml("type", "water_avoiding_stroll"));
        GoalBrain.Entry entry = brain.entries().get(0);
        assertEquals(1, entry.priority());
        assertFalse(entry.isTargetGoal());
    }

    // ── 7. applyFromConfigRoutesNativeTarget ─────────────────────────────

    @Test
    void applyFromConfigRoutesNativeTarget() {
        GoalBrain brain = build(yaml("type", "hurt_by_target", "target", true));
        GoalBrain.Entry entry = brain.entries().get(0);
        assertTrue(entry.isTargetGoal());
        assertTrue(entry.goalOrSpec() instanceof NativeGoalSpec.HurtByTarget);
    }

    // ── 8. nativeMeleeAttackDefaults / customValues ──────────────────────

    @Test
    void nativeMeleeAttackDefaults() {
        GoalBrain brain = build(yaml("type", "melee_attack"));
        Object rawSpec = brain.entries().get(0).goalOrSpec();
        assertInstanceOf(NativeGoalSpec.MeleeAttack.class, rawSpec);
        NativeGoalSpec.MeleeAttack ma = (NativeGoalSpec.MeleeAttack) rawSpec;
        assertEquals(1.0, ma.speed());
        assertFalse(ma.pauseWhenMobIdle());
    }

    @Test
    void nativeMeleeAttackCustomValues() {
        GoalBrain brain = build(yaml("type", "melee_attack", "speed", 1.6, "pause-when-mob-idle", true));
        NativeGoalSpec.MeleeAttack ma = (NativeGoalSpec.MeleeAttack) brain.entries().get(0).goalOrSpec();
        assertEquals(1.6, ma.speed());
        assertTrue(ma.pauseWhenMobIdle());
    }

    // ── 9. nativeWaterAvoidingStrollSpeed ─────────────────────────────────

    @Test
    void nativeWaterAvoidingStrollDefaultSpeed() {
        GoalBrain brain = build(yaml("type", "water_avoiding_stroll"));
        NativeGoalSpec.WaterAvoidingRandomStroll spec =
                (NativeGoalSpec.WaterAvoidingRandomStroll) brain.entries().get(0).goalOrSpec();
        assertEquals(0.8, spec.speed());
    }

    @Test
    void nativeWaterAvoidingStrollCustomSpeed() {
        GoalBrain brain = build(yaml("type", "water_avoiding_stroll", "speed", 2.5));
        NativeGoalSpec.WaterAvoidingRandomStroll spec =
                (NativeGoalSpec.WaterAvoidingRandomStroll) brain.entries().get(0).goalOrSpec();
        assertEquals(2.5, spec.speed());
    }

    // ── 10. nativeLookAtPlayerRange ───────────────────────────────────────

    @Test
    void nativeLookAtPlayerDefaultRange() {
        GoalBrain brain = build(yaml("type", "look_at_player"));
        NativeGoalSpec.LookAtPlayer spec =
                (NativeGoalSpec.LookAtPlayer) brain.entries().get(0).goalOrSpec();
        assertEquals(8.0f, spec.range());
    }

    @Test
    void nativeLookAtPlayerCustomRange() {
        GoalBrain brain = build(yaml("type", "look_at_player", "range", 5.0));
        NativeGoalSpec.LookAtPlayer spec =
                (NativeGoalSpec.LookAtPlayer) brain.entries().get(0).goalOrSpec();
        assertEquals(5.0f, spec.range());
    }

    // ── 11. nativeRandomLookAroundAndHurtByTarget ────────────────────────

    @Test
    void nativeRandomLookAround() {
        GoalBrain brain = build(yaml("type", "random_look_around"));
        assertInstanceOf(NativeGoalSpec.RandomLookAround.class, brain.entries().get(0).goalOrSpec());
    }

    @Test
    void nativeHurtByTarget() {
        GoalBrain brain = build(yaml("type", "hurt_by_target"));
        assertInstanceOf(NativeGoalSpec.HurtByTarget.class, brain.entries().get(0).goalOrSpec());
    }

    // ── 12. nativePanicDefaultSpeed ───────────────────────────────────────

    @Test
    void nativePanicDefaultSpeed() {
        GoalBrain brain = build(yaml("type", "panic"));
        NativeGoalSpec.Panic spec = (NativeGoalSpec.Panic) brain.entries().get(0).goalOrSpec();
        assertEquals(1.25, spec.speed());
    }

    @Test
    void nativePanicCustomSpeed() {
        GoalBrain brain = build(yaml("type", "panic", "speed", 1.5));
        NativeGoalSpec.Panic spec = (NativeGoalSpec.Panic) brain.entries().get(0).goalOrSpec();
        assertEquals(1.5, spec.speed());
    }

    // ── 13. nativeFloatSpec ───────────────────────────────────────────────

    @Test
    void nativeFloatSpec() {
        GoalBrain brain = build(yaml("type", "float"));
        assertInstanceOf(NativeGoalSpec.Float.class, brain.entries().get(0).goalOrSpec());
    }

    // ── 14. nativeAvoidEntityDefaults ─────────────────────────────────────

    @Test
    void nativeAvoidEntityDefaults() {
        GoalBrain brain = build(yaml("type", "avoid_entity"));
        NativeGoalSpec.AvoidEntity spec =
                (NativeGoalSpec.AvoidEntity) brain.entries().get(0).goalOrSpec();
        assertEquals(org.bukkit.entity.Player.class, spec.avoidClass());
        assertEquals(8.0f, spec.maxDist());
        assertEquals(0.8, spec.walkSpeed());
        assertEquals(1.2, spec.sprintSpeed());
    }

    @Test
    void nativeAvoidEntityCustomValues() {
        GoalBrain brain = build(yaml(
                "type", "avoid_entity",
                "avoid-class", "org.bukkit.entity.Zombie",
                "max-dist", 10.0,
                "walk-speed", 1.0,
                "sprint-speed", 2.0
        ));
        NativeGoalSpec.AvoidEntity spec =
                (NativeGoalSpec.AvoidEntity) brain.entries().get(0).goalOrSpec();
        assertEquals(org.bukkit.entity.Zombie.class, spec.avoidClass());
        assertEquals(10.0f, spec.maxDist());
        assertEquals(1.0, spec.walkSpeed());
        assertEquals(2.0, spec.sprintSpeed());
    }

    // ── 15. nativeNearestAttackableTargetDefaults ─────────────────────────

    @Test
    void nativeNearestAttackableTargetDefaults() {
        GoalBrain brain = build(yaml("type", "nearest_attackable_target"));
        NativeGoalSpec.NearestAttackableTarget spec =
                (NativeGoalSpec.NearestAttackableTarget) brain.entries().get(0).goalOrSpec();
        assertEquals(org.bukkit.entity.Player.class, spec.targetClass());
        assertTrue(spec.mustSee());
    }

    @Test
    void nativeNearestAttackableTargetCustomValues() {
        GoalBrain brain = build(yaml(
                "type", "nearest_attackable_target",
                "target-class", "org.bukkit.entity.Zombie",
                "must-see", false
        ));
        NativeGoalSpec.NearestAttackableTarget spec =
                (NativeGoalSpec.NearestAttackableTarget) brain.entries().get(0).goalOrSpec();
        assertEquals(org.bukkit.entity.Zombie.class, spec.targetClass());
        assertFalse(spec.mustSee());
    }

    // ── 16. invalidTargetClassFallsBackToPlayer ──────────────────────────

    @Test
    void invalidTargetClassFallsBackToPlayer() {
        GoalBrain brain = build(yaml(
                "type", "nearest_attackable_target",
                "target-class", "not.a.real.Class"
        ));
        NativeGoalSpec.NearestAttackableTarget spec =
                (NativeGoalSpec.NearestAttackableTarget) brain.entries().get(0).goalOrSpec();
        assertEquals(org.bukkit.entity.Player.class, spec.targetClass());
    }

    @Test
    void invalidAvoidClassFallsBackToPlayer() {
        GoalBrain brain = build(yaml(
                "type", "avoid_entity",
                "avoid-class", "not.a.real.Class"
        ));
        NativeGoalSpec.AvoidEntity spec =
                (NativeGoalSpec.AvoidEntity) brain.entries().get(0).goalOrSpec();
        assertEquals(org.bukkit.entity.Player.class, spec.avoidClass());
    }

    // ── 17. unknownTypeAddsNothing ────────────────────────────────────────

    @Test
    void unknownTypeAddsNothing() {
        GoalBrain brain = build(yaml("type", "wobble", "priority", 5));
        assertTrue(brain.entries().isEmpty());
    }

    // ── 18. addCustomGoal / addNativeGoal registers new factory ──────────

    @Test
    void addCustomGoalRegistersNewFactory() {
        String uniqueKey = "test_custom_" + UUID.randomUUID();
        Goal dummyGoal = new Goal() {
            @Override
            public boolean canStart(MobBrainContext ctx) { return false; }
            @Override
            public void tick(MobBrainContext ctx) {}
        };
        GoalFactory.addCustomGoal(uniqueKey, section -> dummyGoal);
        GoalBrain brain = build(yaml("type", uniqueKey));
        assertEquals(1, brain.entries().size());
        assertSame(dummyGoal, brain.entries().get(0).goalOrSpec());
    }

    @Test
    void addNativeGoalRegistersNewFactory() {
        String uniqueKey = "test_native_" + UUID.randomUUID();
        NativeGoalSpec nativeSpec = new NativeGoalSpec.Float();
        GoalFactory.addNativeGoal(uniqueKey, section -> nativeSpec);
        GoalBrain brain = build(yaml("type", uniqueKey));
        assertEquals(1, brain.entries().size());
        assertSame(nativeSpec, brain.entries().get(0).goalOrSpec());
    }
}
