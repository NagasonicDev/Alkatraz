package me.nagasonic.alkatraz.nms.entity.definitions;

import me.nagasonic.alkatraz.mobs.MagicEntity;
import me.nagasonic.alkatraz.mobs.MagicEntityRegistry;
import me.nagasonic.alkatraz.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.MobProfile;
import me.nagasonic.alkatraz.mobs.MobBrain;
import me.nagasonic.alkatraz.nms.entity.GoalBuilder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.Level;

public abstract class NMSMagicSkeleton extends Skeleton implements MagicEntity {

    private final MagicData magicData = new MagicData();

    @Override
    public final MagicData getMagicData() { return magicData; }

    protected abstract MagicEntityType entityType();

    protected abstract MobBrain brain();

    protected NMSMagicSkeleton(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);

        MobProfile profile = MagicEntityRegistry.getProfile(entityType())
                .orElseThrow(() -> new IllegalStateException(
                        entityType().getId() + " profile not loaded — did you call MagicEntities.registerProfiles()?"));

        initMagic(profile, entityType(), (org.bukkit.entity.LivingEntity) getBukkitEntity());
    }

    @Override
    protected final void registerGoals() {
        GoalBuilder.apply(this, this, brain());
        registerExtraGoals();
    }

    protected void registerExtraGoals() {}
}
