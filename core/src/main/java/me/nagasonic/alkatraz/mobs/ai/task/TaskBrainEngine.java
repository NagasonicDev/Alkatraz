package me.nagasonic.alkatraz.mobs.ai.task;

import de.tr7zw.changeme.nbtapi.NBTCompound;
import me.nagasonic.alkatraz.api.ai.task.ActivitySpec;
import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.ai.task.TaskBrain;
import me.nagasonic.alkatraz.api.ai.task.TaskBrainContext;
import me.nagasonic.alkatraz.api.ai.task.TimedMemory;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.mobs.ai.AiApplier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-entity TaskBrain state: the memory store, the current activity, the
 * pinned activity and the sensors. Implements {@link TaskBrainContext} so
 * sensors observe and write the blackboard through the api surface.
 *
 * <p>Memory here is keyed to an entity-local clock: {@code nowTick} drives
 * expiry, and is seeded with the ticker's global tick at attach so persisted
 * expiries survive detach/attach.
 */
public final class TaskBrainEngine implements TaskBrainContext {

    private final Mob entity;
    private final TaskBrain brain;
    private final Map<String, GoalBrain> activityBrains;
    private final MobMemoryStore store;
    private long nowTick;
    private String currentActivity;
    @Nullable
    private String pinnedActivity;

    private TaskBrainEngine(Mob entity, TaskBrain brain, Map<String, GoalBrain> activityBrains,
                            MobMemoryStore store, @Nullable String pinnedActivity, long initialTick) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.brain = Objects.requireNonNull(brain, "brain");
        this.activityBrains = activityBrains == null ? Map.of() : Map.copyOf(activityBrains);
        this.store = Objects.requireNonNull(store, "store");
        this.pinnedActivity = pinnedActivity;
        this.nowTick = initialTick;
        this.currentActivity = brain.defaultActivity();
        for (MemoryKey<?> key : brain.coreMemories()) {
            store.register(key);
        }
        reconsider();
    }

    public static TaskBrainEngine create(Mob entity, TaskBrain brain, Map<String, GoalBrain> activityBrains,
                                         MobMemoryStore store, @Nullable String pinnedActivity, long initialTick) {
        Objects.requireNonNull(store, "store");
        return new TaskBrainEngine(entity, brain, activityBrains, store, pinnedActivity, initialTick);
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }

    public TaskBrain brain() {
        return brain;
    }

    public MobMemoryStore store() {
        return store;
    }

    public String currentActivity() {
        return currentActivity;
    }

    @Nullable
    public String pinnedActivity() {
        return pinnedActivity;
    }

    /** Pins or unpins an activity; an unknown pinned id degrades to normal selection. */
    public void setPinnedActivity(@Nullable String id) {
        if (Objects.equals(this.pinnedActivity, id)) return;
        this.pinnedActivity = id;
        reconsider();
    }

    /** Advances the engine by one heartbeat: sensors, then re-evaluation. */
    public void tick(long nowTick) {
        this.nowTick = Math.max(this.nowTick, nowTick);
        SensorRunner.run(brain.coreSensors(), this);
        for (ActivitySpec activity : brain.activities()) {
            if (activity.id().equals(currentActivity)) {
                SensorRunner.run(activity.sensors(), this);
                break;
            }
        }
        reconsider();
    }

    /** Recomputes the active activity, applying the transition if it changed. */
    public void reconsider() {
        String wanted = ActivityScheduler.select(brain, pinnedActivity,
                key -> store.present(key, nowTick),
                ActivityScheduler.bucketOf(entity.getWorld() == null ? 0 : entity.getWorld().getTime()));
        if (wanted.equals(currentActivity)) return;
        switchTo(wanted);
    }

    private void switchTo(String wanted) {
        for (ActivitySpec activity : brain.activities()) {
            if (activity.id().equals(currentActivity)) {
                for (MemoryKey<?> key : activity.memoriesOnExit()) store.erase(key);
                break;
            }
        }
        for (ActivitySpec activity : brain.activities()) {
            if (activity.id().equals(wanted)) {
                for (TimedMemory timed : activity.memoriesOnEnter()) {
                    if (timed.expireTicks() == -1) {
                        store.set(timed.key(), timed.value());
                    } else {
                        store.set(timed.key(), timed.value(), nowTick + timed.expireTicks());
                    }
                }
                break;
            }
        }
        currentActivity = wanted;
        GoalBrain goals = activityBrains.get(wanted);
        if (goals != null) {
            LivingEntity entity = getEntity();
            if (entity instanceof Mob mob) AiApplier.applyGoalBrain(mob, goals);
        }
    }

    @Override
    public <T> Optional<T> getMemory(MemoryKey<T> key) {
        return store.get(key, nowTick);
    }

    @Override
    public <T> void setMemory(MemoryKey<T> key, T value) {
        store.set(key, value);
        reconsider();
    }

    @Override
    public <T> void setMemoryWithExpiry(MemoryKey<T> key, T value, long ticks) {
        if (ticks == -1) {
            store.set(key, value);
        } else {
            store.set(key, value, nowTick + ticks);
        }
        reconsider();
    }

    @Override
    public void eraseMemory(MemoryKey<?> key) {
        store.erase(key);
        reconsider();
    }

    @Override
    public boolean hasMemory(MemoryKey<?> key) {
        return store.present(key, nowTick);
    }

    /** Persists the memory store into the caller-supplied NBT compound. */
    public void writeMemoryTo(NBTCompound nbt) {
        store.writeTo(nbt, nowTick);
    }
}