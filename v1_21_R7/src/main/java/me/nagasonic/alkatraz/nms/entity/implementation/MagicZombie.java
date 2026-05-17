package me.nagasonic.alkatraz.nms.entity.implementation;

import me.nagasonic.alkatraz.items.wands.WandRegistry;
import me.nagasonic.alkatraz.nms.entity.MagicEntity;
import me.nagasonic.alkatraz.nms.entity.MagicEntityRegistry;
import me.nagasonic.alkatraz.nms.entity.MagicProfile;
import me.nagasonic.alkatraz.nms.entity.goals.CastSpellGoal;
import me.nagasonic.alkatraz.nms.entity.goals.KeepSpellRangeGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R7.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R7.inventory.CraftItemStack;

/**
 * MagicZombie
 *
 * A zombie that hangs back at spell-casting range rather than charging in.
 * Visually identical to a vanilla zombie; behaviourally it keeps 6-12 blocks
 * from its target and selects a spell to cast from its yml roster.
 *
 * Spawning
 * ────────
 * Use the static {@link #spawn(Location)} factory — it handles NMS level
 * resolution, positioning and finalizeSpawn in one call:
 *
 *   MagicZombie.spawn(player.getLocation());
 */
public class MagicZombie extends Zombie implements MagicEntity {

    // -------------------------------------------------------------------------
    // MagicEntity state
    // -------------------------------------------------------------------------

    private final MagicData magicData = new MagicData();

    @Override
    public MagicData getMagicData() {
        return magicData;
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final double MIN_CAST_DIST = 6.0;
    private static final double MAX_CAST_DIST = 12.0;
    private static final double CAST_RANGE    = 14.0;
    private static final int    CAST_COOLDOWN = 40;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public MagicZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);

        MagicProfile profile = MagicEntityRegistry.getProfile("magic_zombie")
                .orElseThrow(() -> new IllegalStateException(
                        "MagicZombie profile not loaded — did you call MagicEntityRegistry.registerAll()?"));

        initMagic(profile, this);
    }

    // -------------------------------------------------------------------------
    // Static spawn factory
    // -------------------------------------------------------------------------

    /**
     * Creates and spawns a MagicZombie at the given Bukkit location.
     *
     * @param location target location (world must be loaded)
     * @return the spawned MagicZombie
     */
    public static MagicZombie spawn(Location location) {
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        MagicZombie zombie = new MagicZombie(EntityType.ZOMBIE, level);
        zombie.setPos(location.getX(), location.getY(), location.getZ());
        zombie.setItemInHand(InteractionHand.MAIN_HAND, CraftItemStack.asNMSCopy(WandRegistry.getWand("WOODEN_WAND").getItem()));

        zombie.finalizeSpawn(level,
                level.getCurrentDifficultyAt(zombie.blockPosition()),
                EntitySpawnReason.COMMAND, null);

        level.addFreshEntityWithPassengers(zombie);
        return zombie;
    }

    // -------------------------------------------------------------------------
    // Goals
    // -------------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        goalSelector.removeAllGoals(g -> true);
        targetSelector.removeAllGoals(g -> true);

        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new KeepSpellRangeGoal(
                this, MIN_CAST_DIST, MAX_CAST_DIST, 1.1D));
        goalSelector.addGoal(3, new CastSpellGoal(
                this, this, CAST_RANGE, CAST_COOLDOWN));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, true));
    }

    // -------------------------------------------------------------------------
    // Suppress vanilla melee
    // -------------------------------------------------------------------------

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        return distanceTo(target) < MIN_CAST_DIST * 0.5
                && super.doHurtTarget(level, target);
    }

    // -------------------------------------------------------------------------
    // Display name
    // -------------------------------------------------------------------------

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.literal("§8Arcane Zombie");
    }
}