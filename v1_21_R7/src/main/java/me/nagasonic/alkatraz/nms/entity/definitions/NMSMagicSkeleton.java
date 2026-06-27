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

/**
 * Abstract NMS base for all skeleton-based magic mobs in this version module.
 *
 * <p>Mirrors {@link NMSMagicZombie} exactly but extends {@link Skeleton} instead,
 * since Java does not allow multiple inheritance. Any shared behaviour between the
 * two base classes should live in {@link MagicEntity} default methods or
 * {@link GoalBuilder}.
 */
public abstract class NMSMagicSkeleton extends Skeleton implements MagicEntity {

    // -------------------------------------------------------------------------
    // MagicEntity state
    // -------------------------------------------------------------------------

    private final MagicData magicData = new MagicData();

    @Override
    public final MagicData getMagicData() { return magicData; }

    // -------------------------------------------------------------------------
    // Subclass contract
    // -------------------------------------------------------------------------

    /** The canonical type of this mob, used to look up its {@link MobProfile}. */
    protected abstract MagicEntityType entityType();

    /** Declarative description of this mob's AI — evaluated once in {@link #registerGoals()}. */
    protected abstract MobBrain brain();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    protected NMSMagicSkeleton(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);

        MobProfile profile = MagicEntityRegistry.getProfile(entityType())
                .orElseThrow(() -> new IllegalStateException(
                        entityType().getId() + " profile not loaded — did you call MagicEntities.registerProfiles()?"));

        initMagic(profile, entityType(), (org.bukkit.entity.LivingEntity) getBukkitEntity());
    }

    // -------------------------------------------------------------------------
    // Goals
    // -------------------------------------------------------------------------

    @Override
    protected final void registerGoals() {
        GoalBuilder.apply(this, this, brain());
        registerExtraGoals();
    }

    /**
     * Hook for subclasses that need goals beyond what {@link MobBrain} covers.
     * The default implementation does nothing.
     */
    protected void registerExtraGoals() {}
}
