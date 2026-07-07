package me.nagasonic.alkatraz.nms_v1_21_R7.entity.implementation;

import me.nagasonic.alkatraz.items.magic.MagicItemServices;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.MobBrain;
import me.nagasonic.alkatraz.mobs.SpellCastConfig;
import me.nagasonic.alkatraz.nms_v1_21_R7.entity.definitions.NMSMagicSkeleton;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R7.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R7.inventory.CraftItemStack;

/**
 * A ranged skeleton mage. Behaves identically to ZombieMage in terms of AI but
 * extends {@link Skeleton} so it uses skeleton-specific movement and animations.
 */
public final class SkeletalMage extends NMSMagicSkeleton {

    // -------------------------------------------------------------------------
    // Brain
    // -------------------------------------------------------------------------

    private static final MobBrain BRAIN = MobBrain.builder()
            .canSwim(true)
            .spellCast(new SpellCastConfig(6.0, 12.0, 14.0, 30))
            .meleeAttack(false)
            .lookAtPlayerRange(8.0f)
            .randomStroll(true)
            .build();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public SkeletalMage(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
    }

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    @Override
    protected MagicEntityType entityType() { return MagicEntityType.SKELETAL_MAGE; }

    @Override
    protected MobBrain brain() { return BRAIN; }

    // -------------------------------------------------------------------------
    // Static spawn factory
    // -------------------------------------------------------------------------

    /**
     * Creates and spawns a SkeletalMage at the given Bukkit location.
     *
     * @param location target location (world must be loaded)
     * @return the spawned SkeletalMage
     */
    public static SkeletalMage spawn(Location location) {
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        SkeletalMage skelly = new SkeletalMage(EntityType.SKELETON, level);
        skelly.setPos(location.getX(), location.getY(), location.getZ());
        skelly.setItemInHand(InteractionHand.MAIN_HAND,
                CraftItemStack.asNMSCopy(MagicItemServices.get().createItem(MagicKeys.alkatraz("wooden_wand"))));

        skelly.finalizeSpawn(level,
                level.getCurrentDifficultyAt(skelly.blockPosition()),
                EntitySpawnReason.COMMAND, null);

        // Re-apply wand after finalizeSpawn, which can override held items for skeletons.
        skelly.setItemInHand(InteractionHand.MAIN_HAND,
                CraftItemStack.asNMSCopy(MagicItemServices.get().createItem(MagicKeys.alkatraz("wooden_wand"))));

        level.addFreshEntityWithPassengers(skelly);
        return skelly;
    }

    // -------------------------------------------------------------------------
    // Display name
    // -------------------------------------------------------------------------

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.literal("Ã‚Â§8Skeletal Mage");
    }
}
