package me.nagasonic.alkatraz.mobs;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.api.mobs.MagicAiService;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.ai.task.TaskBrainEngine;
import me.nagasonic.alkatraz.mobs.ai.task.TaskBrainTicker;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Core {@link MagicAiService}: extends {@link MagicBrainServiceImpl} and routes
 * task-brain mobs through the {@link TaskBrainTicker} so activities (not the
 * flat brain) are applied.
 */
public final class MagicAiServiceImpl extends MagicBrainServiceImpl implements MagicAiService {

    private static void ensureMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(Alkatraz.getInstance(), task);
    }

    private static @Nullable String resolveBrainId(LivingEntity entity) {
        String explicit = NBT.getPersistentData(entity, nbt -> nbt.getString(MagicBrains.BRAIN_KEY));
        if (explicit != null && !explicit.isBlank()) return explicit;
        String magicType = NBT.getPersistentData(entity, nbt -> nbt.getString(MagicEntityType.NBT_KEY));
        if (magicType != null && !magicType.isBlank()) return magicType;
        return null;
    }

    @Override
    public boolean setBrain(LivingEntity entity, String brainId) {
        if (entity == null || brainId == null || brainId.isBlank()) return false;
        if (!(entity instanceof Mob)) return false;
        if (!Bukkit.isPrimaryThread()) {
            ensureMainThread(() -> setBrain(entity, brainId));
            return true;
        }
        MagicBrains.reload(brainId);
        GoalBrain brain = MagicBrains.brain(brainId);
        if (brain == null) return false;
        NBT.modifyPersistentData(entity, nbt -> { nbt.setString(MagicBrains.BRAIN_KEY, brainId); });
        if (MagicBrains.isTaskBrain(brainId)) {
            TaskBrainTicker.attach(entity);
        } else {
            TaskBrainTicker.detach(entity, true);
            Alkatraz.getNms().applyBrain(entity, brain);
        }
        return true;
    }

    @Override
    public boolean reloadBrain(LivingEntity entity) {
        if (entity == null) return false;
        if (!(entity instanceof Mob)) return false;
        if (!Bukkit.isPrimaryThread()) {
            ensureMainThread(() -> reloadBrain(entity));
            return true;
        }
        String brainId = resolveBrainId(entity);
        if (brainId == null) return false;
        MagicBrains.reload(brainId);
        GoalBrain brain = MagicBrains.brain(brainId);
        if (brain == null) return false;
        if (MagicBrains.isTaskBrain(brainId)) {
            TaskBrainTicker.attach(entity);
        } else {
            TaskBrainTicker.detach(entity, true);
            Alkatraz.getNms().applyBrain(entity, brain);
        }
        return true;
    }

    @Override
    public int reloadBrains() {
        if (!Bukkit.isPrimaryThread()) {
            ensureMainThread(this::reloadBrains);
            return 0;
        }
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!(entity instanceof Mob)) continue;
                String brainId = resolveBrainId(entity);
                if (brainId == null) continue;
                MagicBrains.reload(brainId);
                GoalBrain brain = MagicBrains.brain(brainId);
                if (brain == null) continue;
                if (MagicBrains.isTaskBrain(brainId)) {
                    TaskBrainTicker.attach(entity);
                } else {
                    TaskBrainTicker.detach(entity, true);
                    Alkatraz.getNms().applyBrain(entity, brain);
                }
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean clearBrain(LivingEntity entity) {
        if (entity == null) return false;
        if (!(entity instanceof Mob)) return false;
        ensureMainThread(() -> {
            TaskBrainTicker.detach(entity, true);
            NBT.modifyPersistentData(entity, nbt -> {
                nbt.removeKey(MagicBrains.BRAIN_KEY);
                nbt.removeKey(TaskBrainTicker.ACTIVITY_KEY);
                nbt.removeKey(TaskBrainTicker.MEMORY_KEY);
            });
            Alkatraz.getNms().applyBrain(entity, GoalBrain.builder().build());
        });
        return true;
    }

    @Override
    public <T> boolean setMemory(LivingEntity entity, MemoryKey<T> key, T value) {
        if (entity == null || key == null || value == null) return false;
        if (!Bukkit.isPrimaryThread()) {
            ensureMainThread(() -> setMemory(entity, key, value));
            return true;
        }
        TaskBrainEngine engine = TaskBrainTicker.engine(entity);
        if (engine == null) return false;
        engine.setMemory(key, value);
        return true;
    }

    @Override
    public <T> boolean setMemoryWithExpiry(LivingEntity entity, MemoryKey<T> key, T value, long expireTicks) {
        if (entity == null || key == null || value == null) return false;
        if (!Bukkit.isPrimaryThread()) {
            ensureMainThread(() -> setMemoryWithExpiry(entity, key, value, expireTicks));
            return true;
        }
        TaskBrainEngine engine = TaskBrainTicker.engine(entity);
        if (engine == null) return false;
        engine.setMemoryWithExpiry(key, value, expireTicks);
        return true;
    }

    @Override
    public <T> Optional<T> getMemory(LivingEntity entity, MemoryKey<T> key) {
        if (entity == null || key == null) return Optional.empty();
        TaskBrainEngine engine = TaskBrainTicker.engine(entity);
        if (engine == null) return Optional.empty();
        return engine.getMemory(key);
    }

    @Override
    public boolean eraseMemory(LivingEntity entity, MemoryKey<?> key) {
        if (entity == null || key == null) return false;
        if (!Bukkit.isPrimaryThread()) {
            ensureMainThread(() -> eraseMemory(entity, key));
            return true;
        }
        TaskBrainEngine engine = TaskBrainTicker.engine(entity);
        if (engine == null) return false;
        engine.eraseMemory(key);
        return true;
    }

    @Override
    public boolean overrideActivity(LivingEntity entity, @Nullable String activityId) {
        if (entity == null) return false;
        if (!Bukkit.isPrimaryThread()) {
            ensureMainThread(() -> overrideActivity(entity, activityId));
            return true;
        }
        return TaskBrainTicker.setPinnedActivity(entity, activityId);
    }

    @Override
    public Optional<String> currentActivity(LivingEntity entity) {
        if (entity == null) return Optional.empty();
        TaskBrainEngine engine = TaskBrainTicker.engine(entity);
        if (engine == null) return Optional.empty();
        return Optional.of(engine.currentActivity());
    }
}