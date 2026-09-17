package me.nagasonic.alkatraz.mobs;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.mobs.ai.task.TaskBrainEngine;
import me.nagasonic.alkatraz.mobs.ai.task.TaskBrainTicker;
import me.nagasonic.alkatraz.nms.NMS;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MagicAiServiceImplTest {

    private MockedStatic<Bukkit> bukkitMock;
    private MockedStatic<NBT> nbtMock;
    private MockedStatic<MagicBrains> brainsMock;
    private MockedStatic<TaskBrainTicker> tickerMock;
    private MockedStatic<Alkatraz> alkatrazMock;
    private MagicAiServiceImpl service;
    private Mob mob;

    @BeforeEach
    void setUp() {
        bukkitMock = mockStatic(Bukkit.class);
        nbtMock = mockStatic(NBT.class);
        brainsMock = mockStatic(MagicBrains.class);
        tickerMock = mockStatic(TaskBrainTicker.class);
        alkatrazMock = mockStatic(Alkatraz.class);

        bukkitMock.when(Bukkit::isPrimaryThread).thenReturn(true);
        alkatrazMock.when(Alkatraz::getInstance).thenReturn(mock(me.nagasonic.alkatraz.Alkatraz.class));

        mob = mock(Mob.class);
        service = new MagicAiServiceImpl();
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
        nbtMock.close();
        brainsMock.close();
        tickerMock.close();
        alkatrazMock.close();
    }

    @Test
    void setMemoryDelegatesToAttachedEngine() {
        TaskBrainEngine engine = mock(TaskBrainEngine.class);
        tickerMock.when(() -> TaskBrainTicker.engine(mob)).thenReturn(engine);

        assertTrue(service.setMemory(mob, MemoryKey.IS_HURT, true));
        verify(engine).setMemory(MemoryKey.IS_HURT, true);
    }

    @Test
    void getMemoryReturnsEmptyForUnattached() {
        tickerMock.when(() -> TaskBrainTicker.engine(mob)).thenReturn(null);
        assertEquals(Optional.empty(), service.getMemory(mob, MemoryKey.IS_HURT));
        assertFalse(service.setMemory(mob, MemoryKey.IS_HURT, true));
    }

    @Test
    void overrideActivityPinsThroughTicker() {
        tickerMock.when(() -> TaskBrainTicker.engine(mob)).thenReturn(mock(TaskBrainEngine.class));
        tickerMock.when(() -> TaskBrainTicker.setPinnedActivity(mob, "FIGHT")).thenReturn(true);

        assertTrue(service.overrideActivity(mob, "FIGHT"));
        tickerMock.verify(() -> TaskBrainTicker.setPinnedActivity(mob, "FIGHT"));
    }

    @Test
    void overrideActivitySchedulesOffMainThread() {
        AtomicBoolean mainThread = new AtomicBoolean(false);
        bukkitMock.when(Bukkit::isPrimaryThread).thenAnswer(inv -> mainThread.get());
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
        tickerMock.when(() -> TaskBrainTicker.setPinnedActivity(mob, "FIGHT")).thenReturn(true);
        doAnswer(inv -> {
            mainThread.set(true);
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));

        assertTrue(service.overrideActivity(mob, "FIGHT"));
        tickerMock.verify(() -> TaskBrainTicker.setPinnedActivity(mob, "FIGHT"));
    }

    @Test
    void currentActivityFromEngine() {
        TaskBrainEngine engine = mock(TaskBrainEngine.class);
        when(engine.currentActivity()).thenReturn("FIGHT");
        tickerMock.when(() -> TaskBrainTicker.engine(mob)).thenReturn(engine);

        assertEquals(Optional.of("FIGHT"), service.currentActivity(mob));
    }

    @Test
    void setBrainTaskBrainAttachesEngine() {
        brainsMock.when(() -> MagicBrains.reload("zombie_mage")).thenAnswer(inv -> null);
        brainsMock.when(() -> MagicBrains.brain("zombie_mage")).thenReturn(GoalBrain.builder().build());
        brainsMock.when(() -> MagicBrains.isTaskBrain("zombie_mage")).thenReturn(true);

        assertTrue(service.setBrain(mob, "zombie_mage"));
        tickerMock.verify(() -> TaskBrainTicker.attach(mob));
    }

    @Test
    void setBrainFlatBrainAppliesThroughNms() {
        GoalBrain brain = GoalBrain.builder().build();
        brainsMock.when(() -> MagicBrains.reload("zombie_fighter")).thenAnswer(inv -> null);
        brainsMock.when(() -> MagicBrains.brain("zombie_fighter")).thenReturn(brain);
        brainsMock.when(() -> MagicBrains.isTaskBrain("zombie_fighter")).thenReturn(false);

        NMS nms = mock(NMS.class);
        alkatrazMock.when(Alkatraz::getNms).thenReturn(nms);

        assertTrue(service.setBrain(mob, "zombie_fighter"));
        tickerMock.verify(() -> TaskBrainTicker.detach(mob, true));
        verify(nms).applyBrain(mob, brain);
    }

    @Test
    void clearBrainDetachesAndEmpties() {
        NMS nms = mock(NMS.class);
        alkatrazMock.when(Alkatraz::getNms).thenReturn(nms);

        assertTrue(service.clearBrain(mob));
        tickerMock.verify(() -> TaskBrainTicker.detach(mob, true));
        verify(nms).applyBrain(eq(mob), any(GoalBrain.class));
    }

    @Test
    void eraseMemoryDelegatesToEngine() {
        TaskBrainEngine engine = mock(TaskBrainEngine.class);
        tickerMock.when(() -> TaskBrainTicker.engine(mob)).thenReturn(engine);

        assertTrue(service.eraseMemory(mob, MemoryKey.IS_HURT));
        verify(engine).eraseMemory(MemoryKey.IS_HURT);
    }

    @Test
    void refuseInvalidArguments() {
        assertFalse(service.setMemory(null, MemoryKey.IS_HURT, true));
        assertFalse(service.setBrain(null, "zombie_mage"));
        assertNull(service.getBrain(null));
    }
}