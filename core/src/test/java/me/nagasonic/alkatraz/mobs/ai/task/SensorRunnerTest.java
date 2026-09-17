package me.nagasonic.alkatraz.mobs.ai.task;

import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.ai.task.Sensor;
import me.nagasonic.alkatraz.api.ai.task.TaskBrainContext;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SensorRunnerTest {

    private TaskBrainContext ctx(LivingEntity self, List<Entity> nearby) {
        TaskBrainContext ctx = mock(TaskBrainContext.class);
        when(ctx.getEntity()).thenReturn(self);
        when(self.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(nearby);
        return ctx;
    }

    @Test
    void nearestLivingWritesHostilesExcludingSelf() {
        LivingEntity self = mock(LivingEntity.class);
        LivingEntity zombie = mock(LivingEntity.class);
        Entity block = mock(Entity.class);
        TaskBrainContext ctx = ctx(self, List.of(zombie, block, self));

        SensorRunner.run(List.of(new Sensor.NearestLivingEntities(16)), ctx);

        verify(ctx).setMemory(eq(MemoryKey.NEAREST_HOSTILES), eq(List.of(zombie)));
    }

    @Test
    void nearestLivingWithNoHostilesErases() {
        LivingEntity self = mock(LivingEntity.class);
        TaskBrainContext ctx = ctx(self, List.of());

        SensorRunner.run(List.of(new Sensor.NearestLivingEntities(8)), ctx);

        verify(ctx).eraseMemory(MemoryKey.NEAREST_HOSTILES);
    }

    @Test
    void nearestPlayersFiltersNonPlayers() {
        LivingEntity self = mock(LivingEntity.class);
        Player player = mock(Player.class);
        LivingEntity zombie = mock(LivingEntity.class);
        TaskBrainContext ctx = ctx(self, List.of(player, zombie));

        SensorRunner.run(List.of(new Sensor.NearestPlayers(10)), ctx);

        verify(ctx).setMemory(eq(MemoryKey.NEAREST_HOSTILES), eq(List.of((LivingEntity) player)));
    }

    @Test
    void hurtByIsIgnoredByTheRunner() {
        LivingEntity self = mock(LivingEntity.class);
        TaskBrainContext ctx = ctx(self, List.of());

        SensorRunner.run(List.of(new Sensor.HurtBy()), ctx);

        verify(ctx, never()).setMemory(any(), any());
        verify(ctx, never()).eraseMemory(any());
    }

    @Test
    void hurtExpireConstantIs200() {
        assertEquals(200, SensorRunner.HURT_EXPIRE_TICKS);
    }
}