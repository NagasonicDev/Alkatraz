package me.nagasonic.alkatraz.nms.entity.implementation;

import me.nagasonic.alkatraz.items.wands.WandRegistry;
import me.nagasonic.alkatraz.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.MobBrain;
import me.nagasonic.alkatraz.mobs.SpellCastConfig;
import me.nagasonic.alkatraz.nms.entity.definitions.NMSMagicZombie;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;

public final class ZombieMage extends NMSMagicZombie {

    private static final MobBrain BRAIN = MobBrain.builder()
            .canSwim(true)
            .spellCast(new SpellCastConfig(6.0, 12.0, 14.0, 40))
            .meleeAttack(false)
            .lookAtPlayerRange(8.0f)
            .randomStroll(true)
            .build();

    public ZombieMage(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected MagicEntityType entityType() { return MagicEntityType.ZOMBIE_MAGE; }

    @Override
    protected MobBrain brain() { return BRAIN; }

    public static ZombieMage spawn(Location location) {
        ServerLevel level = (ServerLevel) ((CraftWorld) location.getWorld()).getHandle();

        ZombieMage zombie = new ZombieMage(EntityType.ZOMBIE, level);
        zombie.setPos(location.getX(), location.getY(), location.getZ());
        zombie.setItemInHand(InteractionHand.MAIN_HAND,
                CraftItemStack.asNMSCopy(WandRegistry.getWand("WOODEN_WAND").getItem()));

        zombie.finalizeSpawn(level,
                level.getCurrentDifficultyAt(zombie.blockPosition()),
                MobSpawnType.COMMAND, null, null);

        level.addFreshEntityWithPassengers(zombie);
        return zombie;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        return distanceTo(target) < BRAIN.spellCast().minCastDist() * 0.5
                && super.doHurtTarget(target);
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.literal("§8Zombie Mage");
    }
}
