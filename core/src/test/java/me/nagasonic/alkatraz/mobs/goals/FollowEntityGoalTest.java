package me.nagasonic.alkatraz.mobs.goals;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.api.mobs.GoalFlag;
import me.nagasonic.alkatraz.api.mobs.MobBrainContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FollowEntityGoalTest {

    private static final String UUID_KEY = "summoner_uuid";
    private static final double FOLLOW_RANGE = 16.0;
    private static final double TELEPORT_RANGE = 32.0;
    private static final double SPEED = 1.0;

    private final UUID followedUuid = UUID.randomUUID();

    private FollowEntityGoal goal;
    private MobBrainContext ctx;
    private Mob self;
    private LivingEntity followed;
    private World world;
    private Location selfLocation;

    private MockedStatic<NBT> nbtMock;
    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        goal = new FollowEntityGoal(new FollowEntityGoal.FollowEntityConfig(
                UUID_KEY, FOLLOW_RANGE, TELEPORT_RANGE, SPEED, true));
        ctx = mock(MobBrainContext.class);
        self = mock(Mob.class);
        followed = mock(LivingEntity.class);
        world = mock(World.class);

        nbtMock = mockStatic(NBT.class);
        bukkitMock = mockStatic(Bukkit.class);

        nbtMock.when(() -> NBT.<String>getPersistentData(eq(self), any())).thenReturn(followedUuid.toString());
        bukkitMock.when(() -> Bukkit.getEntity(followedUuid)).thenReturn(followed);

        selfLocation = new Location(world, 0, 0, 0);
        when(ctx.self()).thenReturn(self);
        when(self.getLocation()).thenReturn(selfLocation);
        when(self.getWorld()).thenReturn(world);
        when(followed.getWorld()).thenReturn(world);
        when(followed.isDead()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        nbtMock.close();
        bukkitMock.close();
    }

    private void followedAt(double x, double y, double z) {
        when(followed.getLocation()).thenReturn(new Location(world, x, y, z));
    }

    // ── flags ──────────────────────────────────────────────────────────

    @Test
    void flagsContainsMove() {
        assertEquals(EnumSet.of(GoalFlag.MOVE), goal.flags());
    }

    // ── canStart: resolution failures ──────────────────────────────────

    @Test
    void canStartFalseWhenNoUuid() {
        nbtMock.when(() -> NBT.<String>getPersistentData(eq(self), any())).thenReturn(null);
        assertFalse(goal.canStart(ctx));
    }

    @Test
    void canStartFalseWhenUuidBlank() {
        nbtMock.when(() -> NBT.<String>getPersistentData(eq(self), any())).thenReturn("   ");
        assertFalse(goal.canStart(ctx));
    }

    @Test
    void canStartFalseWhenEntityMissing() {
        bukkitMock.when(() -> Bukkit.getEntity(followedUuid)).thenReturn(null);
        assertFalse(goal.canStart(ctx));
    }

    @Test
    void canStartFalseWhenFollowedDead() {
        when(followed.isDead()).thenReturn(true);
        assertFalse(goal.canStart(ctx));
    }

    // ── canStart: distance / target rules ──────────────────────────────

    @Test
    void canStartTrueWhenFollowedIsTargetEvenWhenClose() {
        followedAt(0, 0, 5);
        when(ctx.getTarget()).thenReturn(followed);
        assertTrue(goal.canStart(ctx));
    }

    @Test
    void canStartFalseWhenClose() {
        followedAt(0, 0, 5);
        assertFalse(goal.canStart(ctx));
    }

    @Test
    void canStartTrueWhenBeyondFollowRange() {
        followedAt(0, 0, 40);
        assertTrue(goal.canStart(ctx));
    }

    @Test
    void canStartTrueWhenCrossWorld() {
        followedAt(0, 0, 1);
        when(followed.getWorld()).thenReturn(mock(World.class));
        assertTrue(goal.canStart(ctx));
    }

    // ── shouldContinue ─────────────────────────────────────────────────

    @Test
    void shouldContinueMirrorsCanStart() {
        followedAt(0, 0, 5);
        assertEquals(goal.canStart(ctx), goal.shouldContinue(ctx));

        followedAt(0, 0, 40);
        assertEquals(goal.canStart(ctx), goal.shouldContinue(ctx));

        followedAt(0, 0, 5);
        when(ctx.getTarget()).thenReturn(followed);
        assertEquals(goal.canStart(ctx), goal.shouldContinue(ctx));
    }

    // ── stop ───────────────────────────────────────────────────────────

    @Test
    void stopCallsStopNavigating() {
        goal.stop(ctx);
        verify(ctx).stopNavigating();
    }

    // ── tick ───────────────────────────────────────────────────────────

    @Test
    void tickClearsTargetWhenTargetIsFollowed() {
        followedAt(0, 0, 5);
        when(ctx.getTarget()).thenReturn(followed);
        goal.tick(ctx);
        verify(ctx).setTarget(null);
        verify(ctx, never()).moveTo(any(), anyDouble());
        verify(self, never()).teleport(any(Location.class));
    }

    @Test
    void tickMovesToFollowedWhenBeyondFollowRange() {
        followedAt(0, 0, 20);
        goal.tick(ctx);
        verify(ctx).moveTo(eq(new Location(world, 0, 0, 20)), eq(SPEED));
        verify(self, never()).teleport(any(Location.class));
    }

    @Test
    void tickTeleportsWhenBeyondTeleportRange() {
        followedAt(0, 0, 100);
        goal.tick(ctx);
        verify(self).teleport(eq(new Location(world, 0, 0, 98.5)));
        verify(ctx).stopNavigating();
        verify(ctx, never()).moveTo(any(), anyDouble());
    }

    @Test
    void tickTeleportsWhenCrossWorld() {
        followedAt(0, 0, 1);
        World other = mock(World.class);
        when(followed.getWorld()).thenReturn(other);
        when(followed.getLocation()).thenReturn(new Location(other, 0, 0, 1));
        goal.tick(ctx);
        verify(self).teleport(new Location(other, 1, 0, 2));
        verify(ctx, never()).moveTo(any(), anyDouble());
    }

    @Test
    void tickDoesNothingWhenClose() {
        followedAt(0, 0, 5);
        goal.tick(ctx);
        verify(ctx, never()).moveTo(any(), anyDouble());
        verify(ctx, never()).setTarget(any());
        verify(self, never()).teleport(any(Location.class));
    }
}