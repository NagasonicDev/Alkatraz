package me.nagasonic.alkatraz.mobs.ai;

import me.nagasonic.alkatraz.api.mobs.NativeGoalSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
}