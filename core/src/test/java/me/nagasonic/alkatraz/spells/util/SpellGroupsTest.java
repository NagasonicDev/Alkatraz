package me.nagasonic.alkatraz.spells.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpellGroupsTest {

    @AfterEach
    void tearDown() {
        SpellGroups.shutdown();
    }

    private static LivingEntity entity(UUID id) {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getUniqueId()).thenReturn(id);
        return entity;
    }

    @Test
    void carryReleaseAndLeaderLookup() {
        UUID leaderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        LivingEntity leader = entity(leaderId);
        LivingEntity member = entity(memberId);

        SpellGroups.carry(leader, member);
        assertTrue(SpellGroups.isCarried(member));
        assertEquals(leaderId, SpellGroups.leaderOf(member));

        SpellGroups.release(leader);
        assertFalse(SpellGroups.isCarried(member));
        assertNull(SpellGroups.leaderOf(member));
    }

    @Test
    void memberIsNeverItsOwnLeaderAndReleaseMemberWorks() {
        UUID leaderId = UUID.randomUUID();
        LivingEntity leader = entity(leaderId);
        SpellGroups.carry(leader, leader);
        assertFalse(SpellGroups.isCarried(leader));

        LivingEntity member = entity(UUID.randomUUID());
        SpellGroups.carry(leader, member);
        SpellGroups.releaseMember(member);
        assertFalse(SpellGroups.isCarried(member));
    }

    @Test
    void boxOutlineIsAFortyEightPointCube() {
        World world = mock(World.class);
        java.util.List<Location> out = new java.util.ArrayList<>();
        SpellMarks.boxOutline(new Location(world, 3, 4, 5), out);
        assertEquals(48, out.size());
        for (Location point : out) {
            assertTrue(point.getX() >= 3.06 && point.getX() <= 3.94);
            assertTrue(point.getY() >= 4.06 && point.getY() <= 4.94);
            assertTrue(point.getZ() >= 5.06 && point.getZ() <= 5.94);
        }
    }
}
