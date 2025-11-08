package me.nagasonic.alkatraz.nms;

import com.mojang.datafixers.util.Pair;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import org.bukkit.craftbukkit.v1_20_R4.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R4.entity.CraftPlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import net.minecraft.world.item.ItemStack;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class NMS_v1_20_R4 implements NMS {

    @Override
    public void setInvisible(org.bukkit.entity.Entity target, Player viewer, boolean invis) {
        Entity nmsEntity = ((CraftEntity) target).getHandle();
        ServerPlayer nmsViewer = ((CraftPlayer) viewer).getHandle();

        // Access the entity's data watcher
        SynchedEntityData data = nmsEntity.getEntityData();

        // Get current flags (index 0 = shared entity flags byte)
        byte flags = data.get(new EntityDataAccessor<>(0, EntityDataSerializers.BYTE));

        // Modify invisibility bit
        if (invis) {
            flags |= 0x20; // Set invisibility flag
        } else {
            flags &= ~0x20; // Clear invisibility flag
        }

        // Create metadata update
        List<SynchedEntityData.DataValue<?>> values = List.of(
                SynchedEntityData.DataValue.create(new EntityDataAccessor<>(0, EntityDataSerializers.BYTE), flags)
        );

        // Create and send the packet
        ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(nmsEntity.getId(), values);
        nmsViewer.connection.send(packet);
        if (invis){
            injectInvisIntercept(viewer, target);
        }else{
            removeInvisIntercept(viewer);
        }
    }


    public static void injectInvisIntercept(Player viewer, org.bukkit.entity.Entity target) {
        Channel channel = getChannel(viewer);

        channel.pipeline().addBefore("packet_handler", "invisibility_interceptor_" + viewer.getName(),
                new ChannelDuplexHandler() {
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        if (msg instanceof ClientboundSetEntityDataPacket packet) {
                            if (packet.id() == target.getEntityId()) {
                                // clone and modify metadata
                                List<SynchedEntityData.DataValue<?>> dataValues = packet.packedItems();
                                for (SynchedEntityData.DataValue<?> value : dataValues) {
                                    if (value.id() == 0 && value.value() instanceof Byte b) {
                                        byte flags = (byte) (b | 0x20); // set invisible bit
                                        SynchedEntityData.DataValue<Byte> newVal =
                                                SynchedEntityData.DataValue.create(new EntityDataAccessor<>(0, EntityDataSerializers.BYTE), flags);
                                        dataValues.set(dataValues.indexOf(value), newVal);
                                        break;
                                    }
                                }
                                packet = new ClientboundSetEntityDataPacket(packet.id(), dataValues);
                                super.write(ctx, packet, promise);
                                return;
                            }
                        }
                        super.write(ctx, msg, promise);
                    }
                });
    }

    public static void removeInvisIntercept(Player viewer) {
        Channel channel = getChannel(viewer);
        if (channel.pipeline().get("invisibility_interceptor_" + viewer.getName()) != null) {
            channel.pipeline().remove("invisibility_interceptor_" + viewer.getName());
        }
    }

    public static Channel getChannel(Player player) {
        try {
            ServerGamePacketListenerImpl listener = ((CraftPlayer) player).getHandle().connection;

            // Access the protected field "connection"
            Field connectionField = ServerGamePacketListenerImpl.class.getSuperclass().getDeclaredField("connection");
            connectionField.setAccessible(true);
            Object networkManager = connectionField.get(listener);

            // The field type has a public "channel" field
            Field channelField = networkManager.getClass().getDeclaredField("channel");
            channelField.setAccessible(true);
            return (Channel) channelField.get(networkManager);

        } catch (Exception e) {
            throw new RuntimeException("Failed to get player channel", e);
        }
    }

    @Override
    public void setTransparent(org.bukkit.entity.Entity e, Player target, boolean trans) {
        
    }

    @Override
    public void fakeArmor(HumanEntity e, Player target, org.bukkit.inventory.ItemStack helmet, org.bukkit.inventory.ItemStack chest, org.bukkit.inventory.ItemStack legs, org.bukkit.inventory.ItemStack boots) {
        net.minecraft.world.entity.Entity nmsEntity = ((CraftEntity) e).getHandle();
        ServerPlayer viewer = ((CraftPlayer) target).getHandle();

        List<Pair<EquipmentSlot, ItemStack>> equipmentList = new ArrayList<>();
        java.util.function.Function<org.bukkit.inventory.ItemStack, ItemStack> toNms =
                (item) -> item != null ? org.bukkit.craftbukkit.v1_20_R4.inventory.CraftItemStack.asNMSCopy(item) : ItemStack.EMPTY;
        equipmentList.add(new Pair<>(EquipmentSlot.HEAD, toNms.apply(helmet)));
        equipmentList.add(new Pair<>(EquipmentSlot.CHEST, toNms.apply(chest)));
        equipmentList.add(new Pair<>(EquipmentSlot.LEGS, toNms.apply(legs)));
        equipmentList.add(new Pair<>(EquipmentSlot.FEET, toNms.apply(boots)));

        ClientboundSetEquipmentPacket packet =
                new ClientboundSetEquipmentPacket(nmsEntity.getId(), equipmentList);

        viewer.connection.send(packet);
    }

    @Override
    public void onEnable() {
        NMS.super.onEnable();
    }
}
