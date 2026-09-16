package me.nagasonic.alkatraz.mobs.ai;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Core registry of native leaf-builders, populated once per module from
 * {@code registerMagicEntities()}. Each entry maps a sealed api spec class
 * (e.g. a {@code NativeGoalSpec} subclass) to a builder that constructs the
 * matching native Minecraft object for that module's version.
 *
 * <p>Core never touches NMS types: handles and built objects travel as
 * {@link Object}; the module-cast happens inside each registered builder.
 * Registration for a single module of all required specs is enforced at
 * startup by {@link #assertAllSupported(Class[])}.
 */
public final class AiSpecRegistry {

    private static final Map<Class<?>, BiFunction<Object, Object, Object>> BUILDERS = new HashMap<>();

    private AiSpecRegistry() {}

    /** Registers a native builder for {@code specClass}; requires the exact spec class. */
    public static <S> void register(Class<S> specClass, BiFunction<Object, S, Object> builder) {
        @SuppressWarnings("unchecked")
        BiFunction<Object, Object, Object> erased =
                (BiFunction<Object, Object, Object>) (BiFunction<Object, S, Object>) builder;
        BUILDERS.put(specClass, erased);
    }

    public static boolean isSupported(Class<?> specClass) {
        return BUILDERS.containsKey(specClass);
    }

    /** Builds the native object for {@code spec} (dispatched on {@code spec.getClass()}). */
    public static Object build(Object nativeMob, Object spec) {
        BiFunction<Object, Object, Object> builder = BUILDERS.get(spec.getClass());
        if (builder == null) {
            throw new IllegalStateException("No registered builder for spec: "
                    + spec.getClass().getName());
        }
        return builder.apply(nativeMob, spec);
    }

    /** Throws if any {@code required} spec class has no registered builder. */
    public static void assertAllSupported(Class<?>... required) {
        Set<Class<?>> missing = new HashSet<>();
        for (Class<?> specClass : required) {
            if (!BUILDERS.containsKey(specClass)) missing.add(specClass);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Unsupported spec classes in this version: " + missing);
        }
    }

    /** Resets the registry (test support / full reload). */
    public static void clear() {
        BUILDERS.clear();
    }

    /** Unmodifiable snapshot of registered spec classes (test/audit support). */
    public static Set<Class<?>> registered() {
        return Collections.unmodifiableSet(new HashSet<>(BUILDERS.keySet()));
    }
}