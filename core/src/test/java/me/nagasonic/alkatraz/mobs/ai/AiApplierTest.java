package me.nagasonic.alkatraz.mobs.ai;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.mobs.Goal;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.api.mobs.NativeGoalSpec;
import me.nagasonic.alkatraz.nms.NMS;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiApplierTest {

    private static final Object NATIVE_MOB = new Object();
    private NMS nmsMock;

    @BeforeEach
    void setUp() {
        nmsMock = mock(NMS.class);
        AiSpecRegistry.register(NativeGoalSpec.Float.class, (mob, spec) -> "builtElement");
    }

    @AfterEach
    void tearDown() {
        AiSpecRegistry.clear();
    }

    @Test
    void nonMobLivingEntityIsIgnored() {
        LivingEntity entity = mock(LivingEntity.class);
        GoalBrain brain = GoalBrain.builder().build();

        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            AiApplier.applyGoalBrain(entity, brain);
            ignored.verify(Alkatraz::getNms, never());
        }
    }

    @Test
    void nullNativeMobIsNoOp() {
        GoalBrain brain = GoalBrain.builder().build();

        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            AiApplier.applyGoalBrain((Object) null, brain);
            ignored.verify(Alkatraz::getNms, never());
        }
    }

    @Test
    void nullBrainIsNoOp() {
        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            AiApplier.applyGoalBrain(NATIVE_MOB, null);
            ignored.verify(Alkatraz::getNms, never());
        }
    }

    @Test
    void nullNmsIsNoOp() {
        GoalBrain brain = GoalBrain.builder().build();

        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            ignored.when(Alkatraz::getNms).thenReturn(null);
            assertDoesNotThrow(() -> AiApplier.applyGoalBrain(NATIVE_MOB, brain));
        }
    }

    @Test
    void goalEntryBridgesAndAddsGoal() {
        Goal goal = mock(Goal.class);
        when(nmsMock.bridgeCustomGoal(NATIVE_MOB, goal)).thenReturn("bridged");

        GoalBrain brain = GoalBrain.builder().addGoal(2, goal).build();

        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            ignored.when(Alkatraz::getNms).thenReturn(nmsMock);
            AiApplier.applyGoalBrain(NATIVE_MOB, brain);

            verify(nmsMock).wipeGoals(NATIVE_MOB);
            verify(nmsMock).bridgeCustomGoal(NATIVE_MOB, goal);
            verify(nmsMock).addGoal(NATIVE_MOB, "bridged", 2, false);
        }
    }

    @Test
    void nativeSpecEntryBuildsFromRegistry() {
        NativeGoalSpec.Float spec = new NativeGoalSpec.Float();
        GoalBrain brain = GoalBrain.builder().addNative(3, spec).build();

        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            ignored.when(Alkatraz::getNms).thenReturn(nmsMock);
            AiApplier.applyGoalBrain(NATIVE_MOB, brain);

            verify(nmsMock).wipeGoals(NATIVE_MOB);
            verify(nmsMock).addGoal(NATIVE_MOB, "builtElement", 3, false);
            verify(nmsMock, never()).bridgeCustomGoal(any(), any());
        }
    }

    @Test
    void targetEntriesRouteToAddGoalWithTargetFlag() {
        Goal goal = mock(Goal.class);
        NativeGoalSpec.Float spec = new NativeGoalSpec.Float();
        when(nmsMock.bridgeCustomGoal(NATIVE_MOB, goal)).thenReturn("bridgedTarget");

        GoalBrain brain = GoalBrain.builder()
                .addTargetGoal(4, goal)
                .addNativeTarget(8, spec)
                .build();

        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            ignored.when(Alkatraz::getNms).thenReturn(nmsMock);
            AiApplier.applyGoalBrain(NATIVE_MOB, brain);

            verify(nmsMock).addGoal(NATIVE_MOB, "bridgedTarget", 4, true);
            verify(nmsMock).addGoal(NATIVE_MOB, "builtElement", 8, true);
        }
    }

    @Test
    void emptyBrainWipesGoalsOnly() {
        GoalBrain brain = GoalBrain.builder().build();

        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            ignored.when(Alkatraz::getNms).thenReturn(nmsMock);
            AiApplier.applyGoalBrain(NATIVE_MOB, brain);

            verify(nmsMock).wipeGoals(NATIVE_MOB);
            verify(nmsMock, never()).addGoal(any(), any(), anyInt(), anyBoolean());
        }
    }

    @Test
    void unknownEntryThrows() throws Exception {
        GoalBrain.Entry badEntry = new GoalBrain.Entry(1, new Object(), false);
        Constructor<GoalBrain> ctor = GoalBrain.class.getDeclaredConstructor(List.class);
        ctor.setAccessible(true);
        GoalBrain brain = ctor.newInstance(List.of(badEntry));

        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            ignored.when(Alkatraz::getNms).thenReturn(nmsMock);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> AiApplier.applyGoalBrain(NATIVE_MOB, brain));

            assertTrue(ex.getMessage().contains("Unknown goal type"));
            verify(nmsMock).wipeGoals(NATIVE_MOB);
        }
    }

    @Test
    void livingEntityRouteUnwrapsAndApplies() {
        Mob mob = mock(Mob.class);
        Goal goal = mock(Goal.class);
        when(nmsMock.unwrapMob(mob)).thenReturn(NATIVE_MOB);
        when(nmsMock.bridgeCustomGoal(NATIVE_MOB, goal)).thenReturn("bridgedGoal");

        GoalBrain brain = GoalBrain.builder().addGoal(5, goal).build();

        try (MockedStatic<Alkatraz> ignored = mockStatic(Alkatraz.class)) {
            ignored.when(Alkatraz::getNms).thenReturn(nmsMock);
            AiApplier.applyGoalBrain(mob, brain);

            verify(nmsMock).unwrapMob(mob);
            verify(nmsMock).wipeGoals(NATIVE_MOB);
            verify(nmsMock).addGoal(NATIVE_MOB, "bridgedGoal", 5, false);
        }
    }
}
