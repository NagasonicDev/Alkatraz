package me.nagasonic.alkatraz.nms_v26_R1.entity.definitions;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.items.magic.MagicItemServices;
import me.nagasonic.alkatraz.mobs.MagicBrains;
import me.nagasonic.alkatraz.mobs.MagicEntity;
import me.nagasonic.alkatraz.mobs.MagicEntityRegistry;
import me.nagasonic.alkatraz.mobs.MobProfile;
import me.nagasonic.alkatraz.mobs.ai.AiApplier;
import me.nagasonic.alkatraz.util.ColorFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

/**
 * Inert NMS scaffold for creating magic variants of {@code bat}. Identity and AI
 * come from core ({@link MagicBrains}) keyed by the stored {@link MagicEntityType}.
 */
public class NMSMagicBat extends Bat implements MagicEntity {

    private final MagicData magicData = new MagicData();
    private final MagicEntityType magicType;

    @Override
    public final MagicData getMagicData() { return magicData; }

    public MagicEntityType entityType() { return magicType; }

    public GoalBrain brain() { return MagicBrains.brain(magicType); }

    protected NMSMagicBat(EntityType<? extends Bat> type, Level level, MagicEntityType magicType) {
        super(type, level);
        this.magicType = magicType;
        registerGoals();

        MobProfile profile = MagicEntityRegistry.getProfile(magicType)
                .orElseThrow(() -> new IllegalStateException(
                        magicType.getId() + " profile not loaded - did you call MagicEntities.registerProfiles()?"));

        initMagic(profile, magicType, (org.bukkit.entity.LivingEntity) getBukkitEntity());
    }

    @Override
    protected final void registerGoals() {
        if (magicType == null) return;
        AiApplier.applyGoalBrain(this, brain());
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        double range = MagicBrains.meleeRange(magicType);
        if (range > 0 && distanceTo(target) >= range) return false;
        return super.doHurtTarget(level, target);
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return Component.literal(ColorFormat.format(MagicBrains.displayName(magicType)));
    }

    public static NMSMagicBat spawn(MagicEntityType magicType, Location location) {
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        NMSMagicBat mob = new NMSMagicBat(EntityType.BAT, level, magicType);
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