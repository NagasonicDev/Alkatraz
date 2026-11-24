package me.nagasonic.alkatraz.spells.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummonLimitsTest {

    @Test
    void capIsEnforcedAndShutdownRemovesEverything() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());

            LivingEntity owner = mock(LivingEntity.class);
            UUID ownerId = UUID.randomUUID();
            when(owner.getUniqueId()).thenReturn(ownerId);

            LivingEntity first = summon(UUID.randomUUID());
            LivingEntity second = summon(UUID.randomUUID());
            bukkit.when(() -> Bukkit.getEntity(first.getUniqueId())).thenReturn(first);
            bukkit.when(() -> Bukkit.getEntity(second.getUniqueId())).thenReturn(second);

            assertTrue(SummonLimits.register(owner, first, 2));
            assertTrue(SummonLimits.register(owner, second, 2));
            assertEquals(2, SummonLimits.count(owner));
            assertFalse(SummonLimits.register(owner, summon(UUID.randomUUID()), 2), "cap must be enforced");

            SummonLimits.despawn(owner);
            verify(first).remove();
            verify(second).remove();
            assertEquals(0, SummonLimits.count(owner));

            SummonLimits.shutdown();
        }
    }

    private static LivingEntity summon(UUID id) {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getUniqueId()).thenReturn(id);
        when(entity.isDead()).thenReturn(false);
        when(entity.isValid()).thenReturn(true);
        when(entity.getScoreboardTags()).thenReturn(new java.util.HashSet<>());
        return entity;
    }
}
