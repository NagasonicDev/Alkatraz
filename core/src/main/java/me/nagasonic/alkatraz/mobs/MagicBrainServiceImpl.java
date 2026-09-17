package me.nagasonic.alkatraz.mobs;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.mobs.MagicBrainService;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Core {@link MagicBrainService}: resolves a mob's brain id from persistence
 * and applies it through the active NMS layer.
 *
 * <p>Id resolution order: explicit {@link MagicBrains#BRAIN_KEY} tag, then the
 * {@link MagicEntityType#NBT_KEY} tag of a magic mob, then {@code null}.
 */
public class MagicBrainServiceImpl implements MagicBrainService {

    private static void ensureMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(Alkatraz.getInstance(), task);
        }
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
        if (Bukkit.isPrimaryThread()) {
            MagicBrains.reload(brainId);
            GoalBrain brain = MagicBrains.brain(brainId);
            if (brain == null) return false;
            NBT.modifyPersistentData(entity, nbt -> { nbt.setString(MagicBrains.BRAIN_KEY, brainId); });
            Alkatraz.getNms().applyBrain(entity, brain);
            return true;
        }
        ensureMainThread(() -> {
            MagicBrains.reload(brainId);
            GoalBrain brain = MagicBrains.brain(brainId);
            if (brain == null) return;
            NBT.modifyPersistentData(entity, nbt -> { nbt.setString(MagicBrains.BRAIN_KEY, brainId); });
            Alkatraz.getNms().applyBrain(entity, brain);
        });
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
        Alkatraz.getNms().applyBrain(entity, brain);
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
                Alkatraz.getNms().applyBrain(entity, brain);
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
            NBT.modifyPersistentData(entity, nbt -> { nbt.removeKey(MagicBrains.BRAIN_KEY); });
            Alkatraz.getNms().applyBrain(entity, GoalBrain.builder().build());
        });
        return true;
    }

    @Override
    public @Nullable String getBrain(LivingEntity entity) {
        if (entity == null) return null;
        return resolveBrainId(entity);
    }
}