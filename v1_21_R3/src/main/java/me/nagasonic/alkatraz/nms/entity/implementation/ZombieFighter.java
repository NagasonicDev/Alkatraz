package me.nagasonic.alkatraz.nms.entity.implementation;

import me.nagasonic.alkatraz.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.MobBrain;
import me.nagasonic.alkatraz.mobs.SpellCastConfig;
import me.nagasonic.alkatraz.nms.entity.definitions.NMSMagicZombie;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R3.CraftWorld;

public final class ZombieFighter extends NMSMagicZombie {

    private static final MobBrain BRAIN = MobBrain.builder()
            .canSwim(true)
            .spellCast(new SpellCastConfig(0, 0, 14.0, 1800))
            .meleeAttack(true)
            .lookAtPlayerRange(8.0f)
            .randomStroll(true)
            .build();

    public ZombieFighter(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected MagicEntityType entityType() { return MagicEntityType.ZOMBIE_FIGHTER; }

    @Override
    protected MobBrain brain() { return BRAIN; }

    @Override
    protected void registerExtraGoals() {
        if (this.level().spigotConfig.zombieAggressiveTowardsVillager) {
            targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                    this, AbstractVillager.class, false));
        }
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this, IronGolem.class, true));
    }

    public static ZombieFighter spawn(Location location) {
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        ZombieFighter zombie = new ZombieFighter(EntityType.ZOMBIE, level);
        zombie.setPos(location.getX(), location.getY(), location.getZ());

        zombie.finalizeSpawn(level,
                level.getCurrentDifficultyAt(zombie.blockPosition()),
                EntitySpawnReason.COMMAND, null);

        level.addFreshEntityWithPassengers(zombie);
        return zombie;
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.literal("§8Zombie Fighter");
    }
}
