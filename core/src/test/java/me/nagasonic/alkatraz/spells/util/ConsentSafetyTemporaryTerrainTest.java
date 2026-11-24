package me.nagasonic.alkatraz.spells.util;

import me.nagasonic.alkatraz.hooks.Protection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Occupancy guard on placement and five-second deferred restore. */
class ConsentSafetyTemporaryTerrainTest {

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
    void neverPlacesIntoAPlayerOccupiedBlock() {
        try (MockedStatic<Protection> protection = mockStatic(Protection.class)) {
            protection.when(() -> Protection.blockEdit(any(), any())).thenReturn(true);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            Player occupant = mock(Player.class);
            when(occupant.isOnline()).thenReturn(true);
            when(occupant.getLocation()).thenReturn(new Location(world, 0.5, 0.1, 0.5));
            when(world.getPlayers()).thenReturn(List.of(occupant));
            Block block = statefulBlock();
            when(world.getBlockAt(any(Location.class))).thenReturn(block);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);

            TemporaryTerrain terrain = new TemporaryTerrain(mock(LivingEntity.class), 4);
            assertEquals(0, terrain.place(new Location(world, 0, 0, 0), Material.STONE));
            assertEquals(0, terrain.size());
        }
    }

    @Test
    void occupiedRestoreDefersForFiveSecondsThenRestores() {
        try (MockedStatic<Protection> protection = mockStatic(Protection.class)) {
            protection.when(() -> Protection.blockEdit(any(), any())).thenReturn(true);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            List<Player> players = new ArrayList<>();
            when(world.getPlayers()).thenAnswer(inv -> players);
            Block block = statefulBlock();
            when(world.getBlockAt(any(Location.class))).thenReturn(block);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);

            TemporaryTerrain terrain = new TemporaryTerrain(mock(LivingEntity.class), 4);
            Location cell = new Location(world, 0, 0, 0);
            assertEquals(1, terrain.place(cell, Material.STONE));

            Player occupant = mock(Player.class);
            when(occupant.isOnline()).thenReturn(true);
            when(occupant.getLocation()).thenReturn(new Location(world, 0.5, 0.1, 0.5));
            players.add(occupant);

            assertEquals(0, terrain.restoreWhenClear(1_000L, null, null));
            assertTrue(terrain.isDeferred());
            assertEquals(0, terrain.restoreWhenClear(5_999L, null, null));
            assertTrue(terrain.isDeferred());
            assertEquals(Material.STONE, block.getType());

            assertEquals(1, terrain.restoreWhenClear(6_000L, null, null));
            assertFalse(terrain.isDeferred());
            assertTrue(terrain.isRestored());
            assertEquals(Material.AIR, block.getType());
        }
    }
}
