package me.nagasonic.alkatraz.nms.entity.definitions;

import me.nagasonic.alkatraz.mobs.MagicEntity;
import me.nagasonic.alkatraz.mobs.MagicEntityRegistry;
import me.nagasonic.alkatraz.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.MobProfile;
import me.nagasonic.alkatraz.mobs.MobBrain;
import me.nagasonic.alkatraz.nms.entity.GoalBuilder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * Abstract NMS base for all zombie-based magic mobs in this version module.
 *
 * <p>Subclasses supply their identity and AI shape via {@link #entityType()} and
 * {@link #brain()} respectively. All boilerplate — profile loading, NBT stamping,
 * goal registration — lives here and never needs to be repeated.
 *
 * <p>Example subclass:
 * <pre>
 *   public final class ZombieMage extends NmsMagicZombie {
 *
 *       private static final MobBrain BRAIN = MobBrain.builder()
 *           .canSwim(true)
 *           .spellCast(new SpellCastConfig(6.0, 12.0, 14.0, 40))
 *           .meleeAttack(false)
 *           .build();
 *
 *       public ZombieMage(EntityType&lt;? extends Zombie&gt; type, Level level) {
 *           super(type, level);
 *       }
 *
 *       &#64;Override protected MagicEntityType entityType() { return MagicEntityType.ZOMBIE_MAGE; }
 *       &#64;Override protected MobBrain        brain()      { return BRAIN; }
 *   }
 * </pre>
 */
public abstract class NMSMagicZombie extends Zombie implements MagicEntity {

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

    protected NMSMagicZombie(EntityType<? extends Zombie> type, Level level) {
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
     * The default implementation does nothing. Called immediately after
     * {@link GoalBuilder#apply} so subclasses can add to the already-populated
     * goal selector without overriding {@link #registerGoals()} directly.
     */
    protected void registerExtraGoals() {}
}
