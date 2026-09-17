package me.nagasonic.alkatraz.api.mobs;

import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MagicAiServiceTest {

    private static MagicAiService noOp() {
        return new MagicAiService() {
            @Override public boolean setBrain(LivingEntity entity, String brainId) { return false; }
            @Override public boolean reloadBrain(LivingEntity entity) { return false; }
            @Override public int reloadBrains() { return 0; }
            @Override public boolean clearBrain(LivingEntity entity) { return false; }
            @Override public @Nullable String getBrain(LivingEntity entity) { return null; }
            @Override public <T> boolean setMemory(LivingEntity entity, MemoryKey<T> key, T value) { return false; }
            @Override public <T> boolean setMemoryWithExpiry(LivingEntity entity, MemoryKey<T> key, T value, long expireTicks) { return false; }
            @Override public <T> Optional<T> getMemory(LivingEntity entity, MemoryKey<T> key) { return Optional.empty(); }
            @Override public boolean eraseMemory(LivingEntity entity, MemoryKey<?> key) { return false; }
            @Override public boolean overrideActivity(LivingEntity entity, @Nullable String activityId) { return false; }
            @Override public Optional<String> currentActivity(LivingEntity entity) { return Optional.empty(); }
        };
    }

    @Test
    void interfaceExtendsBrainService() {
        assertTrue(MagicBrainService.class.isAssignableFrom(MagicAiService.class));
    }

    @Test
    void holderRoundTrips() {
        MagicAiService original = MagicAiService.getInstance();
        MagicAiService fake = noOp();
        MagicAiService.setInstance(fake);
        assertSame(fake, MagicAiService.getInstance());
        MagicAiService.setInstance(original);
        assertSame(original, MagicAiService.getInstance());
    }
}