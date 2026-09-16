package me.nagasonic.alkatraz.mobs.goals;

import me.nagasonic.alkatraz.api.mobs.Goal;
import me.nagasonic.alkatraz.api.mobs.MobBrain;
import me.nagasonic.alkatraz.api.mobs.NativeGoalSpec;
import me.nagasonic.alkatraz.api.mobs.SpellCastConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class GoalFactory {

    private static final Map<String, Function<ConfigurationSection, Goal>> CUSTOM = new HashMap<>();
    private static final Map<String, Function<ConfigurationSection, NativeGoalSpec>> NATIVE = new HashMap<>();

    static {
        CUSTOM.put("cast_spell", section -> {
            double minCastDist = section.getDouble("min-cast-dist", 0);
            double maxCastDist = section.getDouble("max-cast-dist", 0);
            double castRange = section.getDouble("cast-range", 14);
            int cooldownTicks = section.getInt("cooldown-ticks", 40);
            return new CastSpellGoal(new SpellCastConfig(minCastDist, maxCastDist, castRange, cooldownTicks));
        });
        CUSTOM.put("keep_spell_range", section -> {
            double minDist = section.getDouble("min-distance", 6);
            double maxDist = section.getDouble("max-distance", 12);
            double speed = section.getDouble("speed", 1.1);
            return new KeepSpellRangeGoal(minDist, maxDist, speed);
        });

        NATIVE.put("float", section -> new NativeGoalSpec.Float());
        NATIVE.put("melee_attack", section -> new NativeGoalSpec.MeleeAttack(
                section.getDouble("speed", 1.0), section.getBoolean("pause-when-mob-idle", false)));
        NATIVE.put("water_avoiding_stroll", section -> new NativeGoalSpec.WaterAvoidingRandomStroll(
                section.getDouble("speed", 0.8)));
        NATIVE.put("look_at_player", section -> new NativeGoalSpec.LookAtPlayer(
                (float) section.getDouble("range", 8.0)));
        NATIVE.put("random_look_around", section -> new NativeGoalSpec.RandomLookAround());
        NATIVE.put("hurt_by_target", section -> new NativeGoalSpec.HurtByTarget());
        NATIVE.put("nearest_attackable_target", section -> {
            String className = section.getString("target-class", "org.bukkit.entity.Player");
            boolean mustSee = section.getBoolean("must-see", true);
            try {
                Class<?> clazz = Class.forName(className);
                if (LivingEntity.class.isAssignableFrom(clazz)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends LivingEntity> livingClass = (Class<? extends LivingEntity>) clazz;
                    return new NativeGoalSpec.NearestAttackableTarget(livingClass, mustSee);
                }
            } catch (ClassNotFoundException ignored) {}
            return new NativeGoalSpec.NearestAttackableTarget(org.bukkit.entity.Player.class, mustSee);
        });
        NATIVE.put("panic", section -> new NativeGoalSpec.Panic(section.getDouble("speed", 1.25)));
        NATIVE.put("avoid_entity", section -> {
            String className = section.getString("avoid-class", "org.bukkit.entity.Player");
            float maxDist = (float) section.getDouble("max-dist", 8.0);
            double walkSpeed = section.getDouble("walk-speed", 0.8);
            double sprintSpeed = section.getDouble("sprint-speed", 1.2);
            try {
                Class<?> clazz = Class.forName(className);
                if (LivingEntity.class.isAssignableFrom(clazz)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends LivingEntity> livingClass = (Class<? extends LivingEntity>) clazz;
                    return new NativeGoalSpec.AvoidEntity(livingClass, maxDist, walkSpeed, sprintSpeed);
                }
            } catch (ClassNotFoundException ignored) {}
            return new NativeGoalSpec.AvoidEntity(org.bukkit.entity.Player.class, maxDist, walkSpeed, sprintSpeed);
        });
    }

    private GoalFactory() {}

    public static void addCustomGoal(String type, Function<ConfigurationSection, Goal> factory) {
        CUSTOM.put(type, factory);
    }

    public static void addNativeGoal(String type, Function<ConfigurationSection, NativeGoalSpec> factory) {
        NATIVE.put(type, factory);
    }

    public static void applyFromConfig(MobBrain.Builder builder, ConfigurationSection section) {
        int priority = section.getInt("priority", 1);
        String type = section.getString("type", "");
        boolean target = section.getBoolean("target", false);

        Function<ConfigurationSection, Goal> customFactory = CUSTOM.get(type);
        if (customFactory != null) {
            Goal goal = customFactory.apply(section);
            if (target) builder.addTargetGoal(priority, goal);
            else builder.addGoal(priority, goal);
            return;
        }

        Function<ConfigurationSection, NativeGoalSpec> nativeFactory = NATIVE.get(type);
        if (nativeFactory != null) {
            NativeGoalSpec spec = nativeFactory.apply(section);
            if (target) builder.addNativeTarget(priority, spec);
            else builder.addNative(priority, spec);
        }
    }
}
