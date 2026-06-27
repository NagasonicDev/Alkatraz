package me.nagasonic.alkatraz.nms.entity.implementation;

import me.nagasonic.alkatraz.items.wands.WandRegistry;
import me.nagasonic.alkatraz.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.MobBrain;
import me.nagasonic.alkatraz.mobs.SpellCastConfig;
import me.nagasonic.alkatraz.nms.entity.definitions.NMSMagicZombie;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R7.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R7.inventory.CraftItemStack;

/**
 * A ranged zombie mage that keeps its distance and casts spells frequently.
 * Melee is suppressed unless the target walks right up to it.
 */
public final class ZombieMage extends NMSMagicZombie {

    // -------------------------------------------------------------------------
    // Brain
    // -------------------------------------------------------------------------

    private static final MobBrain BRAIN = MobBrain.builder()
            .canSwim(true)
            .spellCast(new SpellCastConfig(6.0, 12.0, 14.0, 40))
            .meleeAttack(false)
            .lookAtPlayerRange(8.0f)
            .randomStroll(true)
            .build();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public ZombieMage(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    @Override
    protected MagicEntityType entityType() { return MagicEntityType.ZOMBIE_MAGE; }

    @Override
    protected MobBrain brain() { return BRAIN; }

    // -------------------------------------------------------------------------
    // Static spawn factory
    // -------------------------------------------------------------------------

    /**
     * Creates and spawns a ZombieMage at the given Bukkit location.
     *
     * @param location target location (world must be loaded)
     * @return the spawned ZombieMage
     */
    public static ZombieMage spawn(Location location) {
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        ZombieMage zombie = new ZombieMage(EntityType.ZOMBIE, level);
        zombie.setPos(location.getX(), location.getY(), location.getZ());
        zombie.setItemInHand(InteractionHand.MAIN_HAND,
                CraftItemStack.asNMSCopy(WandRegistry.getWand("WOODEN_WAND").getItem()));

        zombie.finalizeSpawn(level,
                level.getCurrentDifficultyAt(zombie.blockPosition()),
                EntitySpawnReason.COMMAND, null);

        level.addFreshEntityWithPassengers(zombie);
        return zombie;
    }

    // -------------------------------------------------------------------------
    // Suppress vanilla melee at close range
    // -------------------------------------------------------------------------

    /** Only allow a melee hit if the target is well within the minimum cast distance. */
    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        return distanceTo(target) < BRAIN.spellCast().minCastDist() * 0.5
                && super.doHurtTarget(level, target);
    }

    // -------------------------------------------------------------------------
    // Display name
    // -------------------------------------------------------------------------

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.literal("§8Zombie Mage");
    }
}
