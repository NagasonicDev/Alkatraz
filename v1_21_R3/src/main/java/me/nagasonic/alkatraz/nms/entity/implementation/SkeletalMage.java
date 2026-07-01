package me.nagasonic.alkatraz.nms.entity.implementation;

import me.nagasonic.alkatraz.items.wands.WandRegistry;
import me.nagasonic.alkatraz.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.MobBrain;
import me.nagasonic.alkatraz.mobs.SpellCastConfig;
import me.nagasonic.alkatraz.nms.entity.definitions.NMSMagicSkeleton;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R3.inventory.CraftItemStack;

public final class SkeletalMage extends NMSMagicSkeleton {

    private static final MobBrain BRAIN = MobBrain.builder()
            .canSwim(true)
            .spellCast(new SpellCastConfig(6.0, 12.0, 14.0, 30))
            .meleeAttack(false)
            .lookAtPlayerRange(8.0f)
            .randomStroll(true)
            .build();

    public SkeletalMage(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
    }

    @Override
    protected MagicEntityType entityType() { return MagicEntityType.SKELETAL_MAGE; }

    @Override
    protected MobBrain brain() { return BRAIN; }

    public static SkeletalMage spawn(Location location) {
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        SkeletalMage skelly = new SkeletalMage(EntityType.SKELETON, level);
        skelly.setPos(location.getX(), location.getY(), location.getZ());
        skelly.setItemInHand(InteractionHand.MAIN_HAND,
                CraftItemStack.asNMSCopy(WandRegistry.getWand("WOODEN_WAND").getItem()));

        skelly.finalizeSpawn(level,
                level.getCurrentDifficultyAt(skelly.blockPosition()),
                EntitySpawnReason.COMMAND, null);

        skelly.setItemInHand(InteractionHand.MAIN_HAND,
                CraftItemStack.asNMSCopy(WandRegistry.getWand("WOODEN_WAND").getItem()));

        level.addFreshEntityWithPassengers(skelly);
        return skelly;
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.literal("§8Skeletal Mage");
    }
}
