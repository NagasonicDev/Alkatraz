package me.nagasonic.alkatraz.mobs;

import me.nagasonic.alkatraz.api.mobs.NativeGoalSpec;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NativeGoalRegistry {

    private static final Set<Class<? extends NativeGoalSpec>> SUPPORTED = new HashSet<>();

    private NativeGoalRegistry() {}

    public static void markSupported(Class<? extends NativeGoalSpec>... specs) {
        Collections.addAll(SUPPORTED, specs);
    }

    public static Set<Class<? extends NativeGoalSpec>> getSupported() {
        return Collections.unmodifiableSet(SUPPORTED);
    }

    public static void assertAllSupported() {
        Set<Class<? extends NativeGoalSpec>> all = new HashSet<>(List.of(
                NativeGoalSpec.Float.class,
                NativeGoalSpec.MeleeAttack.class,
                NativeGoalSpec.WaterAvoidingRandomStroll.class,
                NativeGoalSpec.LookAtPlayer.class,
                NativeGoalSpec.RandomLookAround.class,
                NativeGoalSpec.HurtByTarget.class,
                NativeGoalSpec.NearestAttackableTarget.class,
                NativeGoalSpec.Panic.class,
                NativeGoalSpec.AvoidEntity.class
        ));
        all.removeAll(SUPPORTED);
        if (!all.isEmpty()) {
            throw new IllegalStateException("Unsupported NativeGoalSpec subclasses in this version: " + all);
        }
    }
}
