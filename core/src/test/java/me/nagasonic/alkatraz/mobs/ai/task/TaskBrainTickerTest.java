package me.nagasonic.alkatraz.mobs.ai.task;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.NBTCompound;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.ai.task.ActivitySpec;
import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.ai.task.TaskBrain;
import me.nagasonic.alkatraz.api.ai.task.TimeOfDayPredicate;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.mobs.MagicBrains;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskBrainTickerTest {

    private static final String BRAIN_ID = "zombie_mage";

    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<Alkatraz> alkatraz;
    private MockedStatic<NBT> nbt;
    private MockedStatic<MagicBrains> brains;
    private BukkitScheduler scheduler;
    private AtomicReference<Runnable> scheduledTask;
    private Plugin plugin;
    private Mob mob;
    private World world;

    @BeforeEach
    void setUp() {
        resetTicker();
        scheduler = mock(BukkitScheduler.class);
        scheduledTask = new AtomicReference<>();
        when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenAnswer(inv -> {
            scheduledTask.set(inv.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTask(any(), any(Runnable.class))).thenReturn(mock(BukkitTask.class));

        plugin = mock(Alkatraz.class);
        world = mock(World.class);
        when(world.getTime()).thenReturn(6000L);
        mob = mock(Mob.class);
        when(mob.getWorld()).thenReturn(world);
        when(mob.isDead()).thenReturn(false);
        when(mob.isValid()).thenReturn(true);
        when(mob.getUniqueId()).thenReturn(UUID.randomUUID());

        bukkit = mockStatic(Bukkit.class);
        alkatraz = mockStatic(Alkatraz.class);
        nbt = mockStatic(NBT.class);
        brains = mockStatic(MagicBrains.class);

        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        alkatraz.when(Alkatraz::getInstance).thenReturn(plugin);

        NBTCompound compound = mock(NBTCompound.class);
        when(compound.getString(anyString())).thenReturn(BRAIN_ID);
        when(compound.getKeys()).thenReturn(java.util.Collections.emptySet());
        when(compound.getCompound(anyString())).thenReturn(mock(NBTCompound.class));
        nbt.when(() -> NBT.getPersistentData(any(Entity.class), any())).thenAnswer(inv -> {
            Function<NBTCompound, ?> fn = inv.getArgument(1);
            return fn.apply(compound);
        });

        brains.when(() -> MagicBrains.isTaskBrain(BRAIN_ID)).thenReturn(true);
        brains.when(() -> MagicBrains.taskBrain(BRAIN_ID)).thenReturn(simpleBrain());
        brains.when(() -> MagicBrains.activityBrains(BRAIN_ID)).thenReturn(Map.of(
                "IDLE", GoalBrain.builder().build(),
                "FIGHT", GoalBrain.builder().build()));

        TaskBrainTicker.start();
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
        alkatraz.close();
        nbt.close();
        brains.close();
        resetTicker();
    }

    private static void resetTicker() {
        // package-private seam: clears the static map so tests are isolated
        TaskBrainTicker.clearAttached();
    }

    private static TaskBrain simpleBrain() {
        return TaskBrain.builder()
                .addCoreMemory(MemoryKey.IS_HURT)
                .addActivity(new ActivitySpec("IDLE", 1, List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), TimeOfDayPredicate.DAY))
                .addActivity(new ActivitySpec("FIGHT", 10, List.of(), List.of(),
                        List.of(MemoryKey.IS_HURT), List.of(), List.of(), List.of(), TimeOfDayPredicate.DAY))
                .build();
    }

    @Test
    void attachCreatesEngineAndTickAdvancesIt() {
        assertTrue(TaskBrainTicker.attach(mob));
        TaskBrainEngine engine = TaskBrainTicker.engine(mob);
        assertNotNull(engine);
        assertEquals("IDLE", engine.currentActivity());
        assertDoesNotThrow(scheduledTask::get);
        scheduledTask.get().run();
        assertNotNull(TaskBrainTicker.engine(mob));
    }

    @Test
    void damageMarksIsHurtAndSwitchesActivity() {
        TaskBrainTicker.attach(mob);
        TaskBrainEngine engine = TaskBrainTicker.engine(mob);
        assertEquals("IDLE", engine.currentActivity());

        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(mob);
        new TaskBrainTicker().onDamage(event);

        assertTrue(engine.hasMemory(MemoryKey.IS_HURT));
        assertEquals("FIGHT", engine.currentActivity());
    }

    @Test
    void deadEntityIsDetachedAndPersistedOnTick() {
        TaskBrainTicker.attach(mob);
        when(mob.isValid()).thenReturn(false);
        scheduledTask.get().run();
        assertNull(TaskBrainTicker.engine(mob));
        nbt.verify(() -> NBT.modifyPersistentData(eq(mob), any(Consumer.class)), atLeastOnce());
    }

    @Test
    void detachPersistsMemory() {
        TaskBrainTicker.attach(mob);
        TaskBrainTicker.detach(mob, true);
        assertNull(TaskBrainTicker.engine(mob));
        nbt.verify(() -> NBT.modifyPersistentData(eq(mob), any(Consumer.class)));
    }

    @Test
    void setPinnedActivityPersistsTag() {
        TaskBrainTicker.attach(mob);
        assertTrue(TaskBrainTicker.setPinnedActivity(mob, "FIGHT"));
        assertEquals("FIGHT", TaskBrainTicker.engine(mob).pinnedActivity());
        nbt.verify(() -> NBT.modifyPersistentData(eq(mob), any(Consumer.class)));

        assertTrue(TaskBrainTicker.setPinnedActivity(mob, null));
        assertNull(TaskBrainTicker.engine(mob).pinnedActivity());
    }

    @Test
    void shutdownPersistsEverythingAndCancelsTask() {
        TaskBrainTicker.attach(mob);
        TaskBrainTicker.shutdown();
        assertEquals(0, TaskBrainTicker.attachedCount());
        verify(scheduler).cancelTask(anyInt());
    }
}