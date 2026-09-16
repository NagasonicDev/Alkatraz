package me.nagasonic.alkatraz.nms_v1_21_R5.entity.definitions;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.api.mobs.MobBrain;
import me.nagasonic.alkatraz.items.magic.MagicItemServices;
import me.nagasonic.alkatraz.mobs.MagicBrains;
import me.nagasonic.alkatraz.mobs.MagicEntity;
import me.nagasonic.alkatraz.mobs.MagicEntityRegistry;
import me.nagasonic.alkatraz.mobs.MobProfile;
import me.nagasonic.alkatraz.nms_v1_21_R5.entity.GoalBuilder;
import me.nagasonic.alkatraz.util.ColorFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R5.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R5.inventory.CraftItemStack;

/**
 * NMS base for all skeleton-based magic mobs. No {@code doHurtTarget} override
 * (skeleton mages have no melee-range gate).
 */
public class NMSMagicSkeleton extends Skeleton implements MagicEntity {

    private final MagicData magicData = new MagicData();
    private final MagicEntityType magicType;

    @Override
    public final MagicData getMagicData() { return magicData; }

    public MagicEntityType entityType() { return magicType; }

    public MobBrain brain() { return MagicBrains.brain(magicType); }

    protected NMSMagicSkeleton(EntityType<? extends Skeleton> type, Level level, MagicEntityType magicType) {
        super(type, level);
        this.magicType = magicType;

        MobProfile profile = MagicEntityRegistry.getProfile(magicType)
                .orElseThrow(() -> new IllegalStateException(
                        magicType.getId() + " profile not loaded - did you call MagicEntities.registerProfiles()?"));

        initMagic(profile, magicType, (org.bukkit.entity.LivingEntity) getBukkitEntity());
    }

    @Override
    protected final void registerGoals() {
        GoalBuilder.apply(this, this, brain());
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return Component.literal(ColorFormat.format(MagicBrains.displayName(magicType)));
    }

    public static NMSMagicSkeleton spawn(MagicEntityType magicType, Location location) {
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        NMSMagicSkeleton mob = new NMSMagicSkeleton(EntityType.SKELETON, level, magicType);
        mob.setPos(location.getX(), location.getY(), location.getZ());

        mob.finalizeSpawn(level,
                level.getCurrentDifficultyAt(mob.blockPosition()),
                EntitySpawnReason.COMMAND, null);

        String wand = MagicBrains.wand(magicType);
        if (wand != null) {
            mob.setItemInHand(InteractionHand.MAIN_HAND,
                    CraftItemStack.asNMSCopy(MagicItemServices.get().createItem(MagicKeys.alkatraz(wand))));
        }

        level.addFreshEntityWithPassengers(mob);
        return mob;
    }
}