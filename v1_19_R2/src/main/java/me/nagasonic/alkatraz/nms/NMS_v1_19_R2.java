package me.nagasonic.alkatraz.nms;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftPlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class NMS_v1_19_R2 implements NMS {

    @Override
    public void setInvisible(org.bukkit.entity.Entity e, Player target, boolean invis) {
        Entity nmsEntity = ((CraftEntity) e).getHandle();
        ServerPlayer serverTarget = ((CraftPlayer) target).getHandle();
        boolean wasInvisible = nmsEntity.isInvisible();
        nmsEntity.setInvisible(invis);
        SynchedEntityData data = nmsEntity.getEntityData();
        ClientboundSetEntityDataPacket packet =
                new ClientboundSetEntityDataPacket(nmsEntity.getId(), data.packDirty());
        serverTarget.connection.send(packet);
        nmsEntity.setInvisible(wasInvisible);
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
                (item) -> item != null ? org.bukkit.craftbukkit.v1_19_R2.inventory.CraftItemStack.asNMSCopy(item) : ItemStack.EMPTY;
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
