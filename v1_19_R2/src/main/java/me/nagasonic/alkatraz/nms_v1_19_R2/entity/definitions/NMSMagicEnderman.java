package me.nagasonic.alkatraz.nms_v1_19_R2.entity.definitions;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.mobs.ai.AiApplier;
import me.nagasonic.alkatraz.items.magic.MagicItemServices;
import me.nagasonic.alkatraz.mobs.MagicBrains;
import me.nagasonic.alkatraz.mobs.MagicEntity;
import me.nagasonic.alkatraz.mobs.MagicEntityRegistry;
import me.nagasonic.alkatraz.mobs.MobProfile;
import me.nagasonic.alkatraz.util.ColorFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_19_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R2.inventory.CraftItemStack;

public class NMSMagicEnderman extends EnderMan implements MagicEntity {

    private final MagicData magicData = new MagicData();
    private final MagicEntityType magicType;

    @Override
    public final MagicData getMagicData() { return magicData; }

    public MagicEntityType entityType() { return magicType; }

    public GoalBrain brain() { return MagicBrains.brain(magicType); }

    protected NMSMagicEnderman(EntityType<? extends EnderMan> type, Level level, MagicEntityType magicType) {
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
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        double range = MagicBrains.meleeRange(magicType);
        if (range > 0 && distanceTo(target) >= range) return false;
        return super.doHurtTarget(target);
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return Component.literal(ColorFormat.format(MagicBrains.displayName(magicType)));
    }

    public static NMSMagicEnderman spawn(MagicEntityType magicType, Location location) {
        ServerLevel level = (ServerLevel) ((CraftWorld) location.getWorld()).getHandle();

        NMSMagicEnderman mob = new NMSMagicEnderman(EntityType.ENDERMAN, level, magicType);
        mob.setPos(location.getX(), location.getY(), location.getZ());

        mob.finalizeSpawn(level,
                level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.COMMAND, null, null);

        String wand = MagicBrains.wand(magicType);
        if (wand != null) {
            mob.setItemInHand(InteractionHand.MAIN_HAND,
                    CraftItemStack.asNMSCopy(MagicItemServices.get().createItem(MagicKeys.alkatraz(wand))));
        }

        level.addFreshEntityWithPassengers(mob);
        return mob;
    }
}