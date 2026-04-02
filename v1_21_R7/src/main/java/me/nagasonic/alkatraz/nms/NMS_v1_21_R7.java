package me.nagasonic.alkatraz.nms;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.util.Skin;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R7.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_21_R7.entity.CraftHorse;
import org.bukkit.craftbukkit.v1_21_R7.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Field;
import java.util.*;

public final class NMS_v1_21_R7 implements NMS {

    private static EntityDataAccessor<Byte> HORSE_DATA_ID_FLAGS;
    static {
        try {
            for (Field field : AbstractHorse.class.getDeclaredFields()) {
                if (!EntityDataAccessor.class.isAssignableFrom(field.getType())) continue;

                field.setAccessible(true);
                Object value = field.get(null);

                if (value instanceof EntityDataAccessor<?> accessor) {
                    // We want BYTE serializer (flags field)
                    if (accessor.serializer() == EntityDataSerializers.BYTE) {
                        HORSE_DATA_ID_FLAGS = (EntityDataAccessor<Byte>) accessor;
                        break;
                    }
                }
            }

            if (HORSE_DATA_ID_FLAGS == null) {
                throw new IllegalStateException("Could not find horse flags accessor");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setInvisible(org.bukkit.entity.Entity target, boolean invis) {
        if (!(target instanceof LivingEntity livingTarget)) return;

        if (invis) {
            livingTarget.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY,
                    Integer.MAX_VALUE, // very long duration
                    1,
                    false, // ambient
                    false, // particles
                    false  // icon
            ));
        } else {
            livingTarget.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    @Override
    public void setTransparent(org.bukkit.entity.Entity e, Player target, boolean trans) {
        if (!(e instanceof Player entityPlayer)) return;

        Scoreboard scoreboard = target.getScoreboard();
        Team team = scoreboard.getTeam(e.getUniqueId() + "_stealth");

        if (team == null) {
            team = scoreboard.registerNewTeam(e.getUniqueId() + "_stealth");
            team.setCanSeeFriendlyInvisibles(true);
        }

        if (trans) {
            team.addEntry(target.getName());
            team.addEntry(entityPlayer.getName());
        } else {
            if (team.hasEntry(target.getName())) team.removeEntry(target.getName());
            if (team.hasEntry(entityPlayer.getName())) team.removeEntry(entityPlayer.getName());
        }
    }

    @Override
    public void fakeArmor(HumanEntity e, Player target, org.bukkit.inventory.ItemStack helmet, org.bukkit.inventory.ItemStack chest, org.bukkit.inventory.ItemStack legs, org.bukkit.inventory.ItemStack boots) {
        Entity nmsEntity = ((CraftEntity) e).getHandle();
        ServerPlayer viewer = ((CraftPlayer) target).getHandle();

        List<Pair<EquipmentSlot, ItemStack>> equipmentList = new ArrayList<>();
        java.util.function.Function<org.bukkit.inventory.ItemStack, ItemStack> toNms =
                (item) -> item != null ? org.bukkit.craftbukkit.v1_21_R7.inventory.CraftItemStack.asNMSCopy(item) : ItemStack.EMPTY;
        equipmentList.add(new Pair<>(EquipmentSlot.HEAD, toNms.apply(helmet)));
        equipmentList.add(new Pair<>(EquipmentSlot.CHEST, toNms.apply(chest)));
        equipmentList.add(new Pair<>(EquipmentSlot.LEGS, toNms.apply(legs)));
        equipmentList.add(new Pair<>(EquipmentSlot.FEET, toNms.apply(boots)));

        ClientboundSetEquipmentPacket packet =
                new ClientboundSetEquipmentPacket(nmsEntity.getId(), equipmentList);

        viewer.connection.send(packet);
    }

    @Override
    public void fakeExp(Player player, float progress, int level, int totalExp) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();

        ClientboundSetExperiencePacket packet = new ClientboundSetExperiencePacket(progress, totalExp, level);
        nmsPlayer.connection.send(packet);
    }

    @Override
    public void changeSkin(Player player, List<Player> viewers, Skin skin) {
        changeSkinElse(player, viewers, skin);
        refresh(player);
    }

    @Override
    public void changeSkinElse(Player player, List<Player> viewers, Skin skin) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        GameProfile profile = nmsPlayer.getGameProfile();
        profile.properties().removeAll("textures");
        profile.properties().put("textures", new Property("textures", skin.getTexture(), skin.getSignature()));
        for (Player other : viewers){
            if (other != player){
                hideAndShow(other, player);
            }
        }
    }

    @Override
    public void fakeHorseSaddle(Horse horse, Player target, boolean saddle) {
        ServerPlayer nmsPlayer = ((CraftPlayer) target).getHandle();
        net.minecraft.world.entity.animal.equine.Horse nmsHorse = ((CraftHorse) horse).getHandle();

        SynchedEntityData data = nmsHorse.getEntityData();

        // Get current flags
        byte flags = data.get(HORSE_DATA_ID_FLAGS);

        // Remove saddle bit (0x04)
        byte modifiedFlags = (byte) (flags & ~0x04);

        // Create spoofed metadata entry
        SynchedEntityData.DataValue<Byte> dataValue =
                new SynchedEntityData.DataValue<>(
                        HORSE_DATA_ID_FLAGS.id(),
                        EntityDataSerializers.BYTE,
                        modifiedFlags
                );

        // Send packet
        ClientboundSetEntityDataPacket packet =
                new ClientboundSetEntityDataPacket(
                        nmsHorse.getId(),
                        List.of(dataValue)
                );

        nmsPlayer.connection.send(packet);
    }

    public void refresh(Player player) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        resendInfoPackets(player, player);
        CommonPlayerSpawnInfo info = nmsPlayer.createCommonSpawnInfo(nmsPlayer.level());
        nmsPlayer.connection.send(new ClientboundRespawnPacket(info, ClientboundRespawnPacket.KEEP_ALL_DATA));
        Location l = player.getLocation();
        Vec3 posVec = new Vec3(l.getX(), l.getY(), l.getZ());
        Vec3 feetVec = new Vec3(l.getX(), l.getY(), l.getZ());
        PositionMoveRotation posRot = new PositionMoveRotation(posVec, feetVec, l.getYaw(), l.getPitch());
        Set<Relative> relative = EnumSet.noneOf(Relative.class);
        int teleportId = nmsPlayer.level().getServer().getTickCount();
        ClientboundPlayerPositionPacket pos = new ClientboundPlayerPositionPacket(teleportId, posRot, relative);
        nmsPlayer.connection.send(pos);
        nmsPlayer.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getHeldItemSlot()));
        ((CraftPlayer) player).updateScaledHealth();
        player.updateInventory();
    }



    public void resendInfoPackets(Player toResend, Player toSendTo) {
        ServerPlayer nmsPlayer = ((CraftPlayer) toResend).getHandle();
        ServerPlayer nmsViewer = ((CraftPlayer) toSendTo).getHandle();
        ClientboundPlayerInfoRemovePacket removePacket = new ClientboundPlayerInfoRemovePacket(Collections.singletonList(nmsPlayer.getUUID()));
        ClientboundPlayerInfoUpdatePacket addPacket = new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, nmsPlayer);
        nmsViewer.connection.send(removePacket);
        nmsViewer.connection.send(addPacket);
    }

    @SuppressWarnings("deprecation")
    private void hideAndShow(Player player, Player other) {
        try {
            player.hidePlayer(Alkatraz.getInstance(), other);
        } catch (NoSuchMethodError ignored) {
            // Backwards compatibility
            player.hidePlayer(other);
        }

        try {
            player.showPlayer(Alkatraz.getInstance(), other);
        } catch (NoSuchMethodError ignored) {
            // Backwards compatibility
            player.showPlayer(other);
        }
    }

    @Override
    public void onEnable() {
        NMS.super.onEnable();
    }
}
