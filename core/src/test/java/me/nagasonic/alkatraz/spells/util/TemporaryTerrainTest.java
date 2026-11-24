package me.nagasonic.alkatraz.spells.util;

import me.nagasonic.alkatraz.hooks.Protection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class TemporaryTerrainTest {

    /** A Block whose type actually changes on setType, so restore is observable. */
    private static Block statefulBlock() {
        Block block = mock(Block.class);
        AtomicReference<Material> type = new AtomicReference<>(Material.AIR);
        when(block.getType()).thenAnswer(inv -> type.get());
        doAnswer(inv -> {
            type.set(inv.getArgument(0));
            return null;
        }).when(block).setType(any(Material.class), anyBoolean());
        return block;
    }

    @Test
    void budgetTruncatesAndRestoreIsIdempotent() {
        try (MockedStatic<Protection> protection = mockStatic(Protection.class)) {
            protection.when(() -> Protection.blockEdit(any(), any())).thenReturn(true);

            World world = mock(World.class);
            Block block = statefulBlock();
            when(world.getBlockAt(any(Location.class))).thenReturn(block);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
            LivingEntity caster = mock(LivingEntity.class);

            TemporaryTerrain terrain = new TemporaryTerrain(caster, 2);

            assertEquals(1, terrain.place(new Location(world, 0, 0, 0), Material.STONE));
            assertEquals(1, terrain.place(new Location(world, 1, 0, 0), Material.STONE));
            // third placement is over budget and must be refused, not overrun
            assertEquals(0, terrain.place(new Location(world, 2, 0, 0), Material.STONE));
            assertEquals(2, terrain.size());
            assertEquals(0, terrain.remainingBudget());

            assertEquals(2, terrain.restore());
            assertTrue(terrain.isRestored());
            assertEquals(0, terrain.restore(), "second restore must be a no-op");
            assertEquals(0, terrain.place(new Location(world, 3, 0, 0), Material.STONE));
        }
    }
}
