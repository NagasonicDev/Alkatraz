package me.nagasonic.alkatraz.mobs.ai.task;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.NBTCompound;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.ai.task.TaskBrain;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.MagicBrains;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The TaskBrain heartbeat: a Bukkit-main-thread task that ticks every attached
 * {@link TaskBrainEngine} every {@value #TICK_INTERVAL} ticks, plus damage
 * tracking that marks {@code IS_HURT}.
 */
public final class TaskBrainTicker implements Listener {

    /** NBT tag holding the pinned activity id. */
    public static final String ACTIVITY_KEY = "alkatraz_activity";
    /** NBT tag holding the memory snapshot. */
    public static final String MEMORY_KEY = "alkatraz_memory";

    public static final long TICK_INTERVAL = 20;

    private static final TaskBrainTicker LISTENER = new TaskBrainTicker();
    private static final Map<UUID, TaskBrainEngine> attached = new HashMap<>();
    private static final Object lock = new Object();
    private static int taskId = -1;
    private static long currentTick;

    TaskBrainTicker() {}

    /** Starts the repeating beat and registers the damage listener. Main thread. */
    public static void start() {
        Plugin plugin = Alkatraz.getInstance();
        taskId = Bukkit.getScheduler()
                .runTaskTimer(plugin, TaskBrainTicker::tickAll, TICK_INTERVAL, TICK_INTERVAL)
                .getTaskId();
        Bukkit.getPluginManager().registerEvents(LISTENER, plugin);
    }

    /** Persists and detaches every engine, then cancels the beat. */
    public static void shutdown() {
        for (TaskBrainEngine engine : attached.values()) {
            detachWithPersistence(engine);
        }
        clearAttached();
        Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
    }

    /** Attaches an engine to a mob whose brain has a tasks section. Main thread. */
    public static boolean attach(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return false;
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(Alkatraz.getInstance(), () -> attach(entity));
            return false;
        }
        detach(entity, true);
        String brainId = resolveBrainId(entity);
        if (brainId == null || !MagicBrains.isTaskBrain(brainId)) return false;
        TaskBrain taskBrain = MagicBrains.taskBrain(brainId);
        Map<String, GoalBrain> activityBrains = MagicBrains.activityBrains(brainId);
        if (taskBrain == null || activityBrains == null) return false;

        MobMemoryStore store = readMemory(entity);
        String pin = NBT.getPersistentData(entity, nbt -> {
            String value = nbt.getString(ACTIVITY_KEY);
            return value == null || value.isBlank() ? null : value;
        });

        TaskBrainEngine engine = TaskBrainEngine.create(
                mob, taskBrain, activityBrains, store, pin, currentTick);
        synchronized (lock) {
            attached.put(entity.getUniqueId(), engine);
        }
        return true;
    }

    /** Detaches an engine, optionally persisting its memory snapshot first. */
    public static void detach(LivingEntity entity, boolean persist) {
        TaskBrainEngine engine;
        synchronized (lock) {
            engine = attached.remove(entity.getUniqueId());
        }
        if (engine != null && persist) detachWithPersistence(engine);
    }

    @Nullable
    public static TaskBrainEngine engine(LivingEntity entity) {
        synchronized (lock) {
            return attached.get(entity.getUniqueId());
        }
    }

    public static boolean setPinnedActivity(LivingEntity entity, @Nullable String activityId) {
        TaskBrainEngine engine = engine(entity);
        if (engine == null) return false;
        engine.setPinnedActivity(activityId);
        NBT.modifyPersistentData(entity, nbt -> {
            if (activityId == null) nbt.removeKey(ACTIVITY_KEY);
            else nbt.setString(ACTIVITY_KEY, activityId);
        });
        return true;
    }

    public static int attachedCount() {
        synchronized (lock) {
            return attached.size();
        }
    }

    private static void detachWithPersistence(TaskBrainEngine engine) {
        LivingEntity entity = engine.getEntity();
        NBT.modifyPersistentData(entity, nbt -> {
            engine.writeMemoryTo((NBTCompound) nbt.getOrCreateCompound(MEMORY_KEY));
        });
    }

    private static MobMemoryStore readMemory(LivingEntity entity) {
        return NBT.getPersistentData(entity, nbt -> MobMemoryStore.readFrom((NBTCompound) nbt.getCompound(MEMORY_KEY)));
    }

    private static void tickAll() {
        Map<UUID, TaskBrainEngine> snapshot;
        synchronized (lock) {
            snapshot = Map.copyOf(attached);
        }
        for (TaskBrainEngine engine : snapshot.values()) {
            LivingEntity entity = engine.getEntity();
            if (entity.isDead() || !entity.isValid()) {
                detach(entity, true);
                continue;
            }
            engine.tick(currentTick);
        }
        currentTick += TICK_INTERVAL;
    }

    private static String resolveBrainId(LivingEntity entity) {
        String explicit = NBT.getPersistentData(entity, nbt -> nbt.getString(MagicBrains.BRAIN_KEY));
        if (explicit != null && !explicit.isBlank()) return explicit;
        String magicType = NBT.getPersistentData(entity, nbt -> nbt.getString(MagicEntityType.NBT_KEY));
        if (magicType != null && !magicType.isBlank()) return magicType;
        return null;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        TaskBrainEngine engine = engine(victim);
        if (engine == null) return;
        engine.setMemoryWithExpiry(MemoryKey.IS_HURT, Boolean.TRUE, SensorRunner.HURT_EXPIRE_TICKS);
    }

    /** Test seam: clears all attached engines and resets the clock. Package-private. */
    static void clearAttached() {
        synchronized (lock) {
            attached.clear();
        }
        currentTick = 0;
    }
}