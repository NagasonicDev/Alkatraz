package me.nagasonic.alkatraz.mobs.goals;

import me.nagasonic.alkatraz.api.mobs.GoalFlag;
import me.nagasonic.alkatraz.api.mobs.MobBrainContext;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeepSpellRangeGoalTest {

    private static final double MIN = 6.0;
    private static final double MAX = 12.0;
    private static final double SPEED = 1.1;

    private KeepSpellRangeGoal goal;
    private MobBrainContext ctx;
    private LivingEntity target;
    private Mob self;
    private Location targetLocation;
    private Location selfLocation;

    @BeforeEach
    void setUp() {
        goal = new KeepSpellRangeGoal(MIN, MAX, SPEED);
        ctx = mock(MobBrainContext.class);
        target = mock(LivingEntity.class);
        self = mock(Mob.class);

        targetLocation = new Location(null, 0, 0, 100);
        selfLocation = new Location(null, 0, 0, 0);

        when(ctx.getTarget()).thenReturn(target);
        when(ctx.self()).thenReturn(self);
        when(self.getLocation()).thenReturn(selfLocation);
        when(target.getLocation()).thenReturn(targetLocation);
    }

    @Test
    void flagsContainsMove() {
        assertEquals(EnumSet.of(GoalFlag.MOVE), goal.flags());
    }

    @Test
    void canStartNullTargetIsFalse() {
        when(ctx.getTarget()).thenReturn(null);
        assertFalse(goal.canStart(ctx));
    }

    @Test
    void canStartDeadTargetIsFalse() {
        when(target.isDead()).thenReturn(true);
        assertFalse(goal.canStart(ctx));
    }

    @Test
    void canStartInBandIsFalse() {
        when(ctx.distanceSq(target)).thenReturn(81.0);
        assertFalse(goal.canStart(ctx));
    }

    @Test
    void canStartTooCloseIsTrue() {
        when(ctx.distanceSq(target)).thenReturn(25.0);
        assertTrue(goal.canStart(ctx));
    }

    @Test
    void canStartTooFarIsTrue() {
        when(ctx.distanceSq(target)).thenReturn(400.0);
        assertTrue(goal.canStart(ctx));
    }

    @Test
    void stopCallsStopNavigating() {
        goal.stop(ctx);
        verify(ctx).stopNavigating();
    }

    @Test
    void tickTooCloseStrafeAway() {
        when(ctx.distanceSq(target)).thenReturn(25.0);
        goal.start(ctx);
        goal.tick(ctx);
        verify(ctx).strafeAwayFrom(targetLocation, MIN, SPEED);
    }

    @Test
    void tickTooFarMovesToMidpoint() {
        when(ctx.distanceSq(target)).thenReturn(400.0);
        goal.start(ctx);
        goal.tick(ctx);

        ArgumentCaptor<Location> captor = ArgumentCaptor.forClass(Location.class);
        verify(ctx).moveTo(captor.capture(), eq(SPEED));
        Location dest = captor.getValue();
        assertEquals(0.0, dest.getX(), 1e-6);
        assertEquals(91.0, dest.getZ(), 1e-6);
    }

    @Test
    void tickRecalculatesEveryTenthTick() {
        when(ctx.distanceSq(target)).thenReturn(400.0);
        goal.start(ctx);

        goal.tick(ctx);

        for (int i = 0; i < 10; i++) {
            goal.tick(ctx);
        }

        verify(ctx, times(2)).moveTo(any(Location.class), eq(SPEED));
    }

    @Test
    void shouldContinueMirrorsCanStart() {
        when(ctx.distanceSq(target)).thenReturn(81.0);
        assertEquals(goal.canStart(ctx), goal.shouldContinue(ctx));

        when(ctx.distanceSq(target)).thenReturn(25.0);
        assertEquals(goal.canStart(ctx), goal.shouldContinue(ctx));

        when(target.isDead()).thenReturn(true);
        assertEquals(goal.canStart(ctx), goal.shouldContinue(ctx));
    }
}
