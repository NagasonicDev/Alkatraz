package me.nagasonic.alkatraz.mobs.ai;

import me.nagasonic.alkatraz.api.mobs.NativeGoalSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AiSpecRegistryTest {

    @AfterEach
    void resetRegistry() {
        AiSpecRegistry.clear();
    }

    @Test
    void build_dispatchesToRegisteredBuilder() {
        AiSpecRegistry.register(NativeGoalSpec.Float.class, (mob, spec) -> "built-" + mob);
        assertEquals("built-mob", AiSpecRegistry.build("mob", new NativeGoalSpec.Float()));
    }

    @Test
    void build_unregisteredSpecThrowsWithClassName() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AiSpecRegistry.build("mob", new NativeGoalSpec.Panic(1.25)));
        assertTrue(ex.getMessage().contains("Panic"));
    }

    @Test
    void isSupported_reflectsRegistration() {
        assertFalse(AiSpecRegistry.isSupported(NativeGoalSpec.MeleeAttack.class));
        AiSpecRegistry.register(NativeGoalSpec.MeleeAttack.class, (mob, spec) -> "x");
        assertTrue(AiSpecRegistry.isSupported(NativeGoalSpec.MeleeAttack.class));
    }

    @Test
    void assertAllSupported_throwsListingMissing() {
        AiSpecRegistry.register(NativeGoalSpec.Float.class, (mob, spec) -> "x");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AiSpecRegistry.assertAllSupported(
                        NativeGoalSpec.Float.class, NativeGoalSpec.AvoidEntity.class));
        assertTrue(ex.getMessage().contains("AvoidEntity"));
    }

    @Test
    void assertAllSupported_passesWhenComplete() {
        AiSpecRegistry.register(NativeGoalSpec.Float.class, (mob, spec) -> "x");
        AiSpecRegistry.register(NativeGoalSpec.AvoidEntity.class, (mob, spec) -> "x");
        assertDoesNotThrow(() -> AiSpecRegistry.assertAllSupported(
                NativeGoalSpec.Float.class, NativeGoalSpec.AvoidEntity.class));
    }

    @Test
    void clear_removesAllBuilders() {
        AiSpecRegistry.register(NativeGoalSpec.Float.class, (mob, spec) -> "x");
        AiSpecRegistry.clear();
        assertEquals(0, AiSpecRegistry.registered().size());
    }

    private record TestSpec() {}

    @Test
    void buildReturnsBuilderResult() {
        Object marker = new Object();
        Object nativeMob = new Object();
        TestSpec spec = new TestSpec();
        Object[] captured = new Object[2];
        AiSpecRegistry.register(TestSpec.class, (mob, s) -> {
            captured[0] = mob;
            captured[1] = s;
            return marker;
        });

        assertSame(marker, AiSpecRegistry.build(nativeMob, spec));
        assertSame(nativeMob, captured[0]);
        assertSame(spec, captured[1]);
    }

    @Test
    void buildWithUnregisteredSpecThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AiSpecRegistry.build("mob", new TestSpec()));
        assertTrue(ex.getMessage().contains(TestSpec.class.getName()));
    }

    @Test
    void duplicateRegisterOverridesBuilder() {
        AiSpecRegistry.register(TestSpec.class, (mob, spec) -> "first");
        AiSpecRegistry.register(TestSpec.class, (mob, spec) -> "second");
        assertEquals("second", AiSpecRegistry.build("mob", new TestSpec()));
    }

    @Test
    void isSupportedTogglesWithRegisterAndClear() {
        assertFalse(AiSpecRegistry.isSupported(TestSpec.class));
        AiSpecRegistry.register(TestSpec.class, (mob, spec) -> "x");
        assertTrue(AiSpecRegistry.isSupported(TestSpec.class));
        AiSpecRegistry.clear();
        assertFalse(AiSpecRegistry.isSupported(TestSpec.class));
    }

    @Test
    void registeredReturnsUnmodifiableSnapshot() {
        AiSpecRegistry.register(TestSpec.class, (mob, spec) -> "x");
        AiSpecRegistry.register(NativeGoalSpec.Float.class, (mob, spec) -> "x");
        Set<Class<?>> snapshot = AiSpecRegistry.registered();
        assertTrue(snapshot.contains(TestSpec.class));
        assertTrue(snapshot.contains(NativeGoalSpec.Float.class));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(Object.class));

        AiSpecRegistry.clear();
        assertTrue(snapshot.contains(TestSpec.class));
        assertFalse(AiSpecRegistry.registered().contains(TestSpec.class));
    }

    @Test
    void assertAllSupportedPassesWhenAllRegistered() {
        AiSpecRegistry.register(TestSpec.class, (mob, spec) -> "x");
        AiSpecRegistry.register(NativeGoalSpec.Float.class, (mob, spec) -> "x");
        assertDoesNotThrow(() -> AiSpecRegistry.assertAllSupported(TestSpec.class, NativeGoalSpec.Float.class));
    }

    @Test
    void assertAllSupportedThrowsNamingMissing() {
        AiSpecRegistry.register(NativeGoalSpec.Float.class, (mob, spec) -> "x");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AiSpecRegistry.assertAllSupported(
                        NativeGoalSpec.Float.class, TestSpec.class));
        assertTrue(ex.getMessage().contains(TestSpec.class.getName()));
    }
}