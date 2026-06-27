package me.nagasonic.alkatraz.nms.entity.implementation;

import me.nagasonic.alkatraz.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.MobBrain;
import me.nagasonic.alkatraz.mobs.SpellCastConfig;
import me.nagasonic.alkatraz.nms.entity.definitions.NMSMagicZombie;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R7.CraftWorld;

/**
 * A melee-focused zombie that occasionally casts a spell on a long cooldown.
 * No range-keeping: it simply rushes the target and punches.
 */
public final class ZombieFighter extends NMSMagicZombie {

    // -------------------------------------------------------------------------
    // Brain
    // -------------------------------------------------------------------------

    // minCastDist / maxCastDist = 0 → KeepSpellRangeGoal is skipped.
    private static final MobBrain BRAIN = MobBrain.builder()
            .canSwim(true)
            .spellCast(new SpellCastConfig(0, 0, 14.0, 1800))
            .meleeAttack(true)
            .lookAtPlayerRange(8.0f)
            .randomStroll(true)
            .build();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public ZombieFighter(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    @Override
    protected MagicEntityType entityType() { return MagicEntityType.ZOMBIE_FIGHTER; }

    @Override
    protected MobBrain brain() { return BRAIN; }

    // -------------------------------------------------------------------------
    // Extra goals — villager and iron golem targeting (zombie-specific)
    // -------------------------------------------------------------------------

    /**
     * ZombieFighter retains vanilla zombie aggression toward villagers and iron
     * golems. These are registered after {@link me.nagasonic.alkatraz.nms.entity.GoalBuilder}
     * populates the standard targets, so priorities are relative to what is
     * already there (HurtBy=1, Player=2 → these start at 3).
     */
    @Override
    protected void registerExtraGoals() {
        if (this.level().spigotConfig.zombieAggressiveTowardsVillager) {
            targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                    this, AbstractVillager.class, false));
        }
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this, IronGolem.class, true));
    }

    // -------------------------------------------------------------------------
    // Static spawn factory
    // -------------------------------------------------------------------------

    /**
     * Creates and spawns a ZombieFighter at the given Bukkit location.
     *
     * @param location target location (world must be loaded)
     * @return the spawned ZombieFighter
     */
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

    // -------------------------------------------------------------------------
    // Display name
    // -------------------------------------------------------------------------

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.literal("§8Zombie Fighter");
    }
}
