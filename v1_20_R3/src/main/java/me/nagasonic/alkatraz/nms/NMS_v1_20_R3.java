package me.nagasonic.alkatraz.nms;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import net.minecraft.world.item.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

public final class NMS_v1_20_R3 implements NMS {

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
        net.minecraft.world.entity.Entity nmsEntity = ((CraftEntity) e).getHandle();
        ServerPlayer viewer = ((CraftPlayer) target).getHandle();

        List<Pair<EquipmentSlot, ItemStack>> equipmentList = new ArrayList<>();
        java.util.function.Function<org.bukkit.inventory.ItemStack, ItemStack> toNms =
                (item) -> item != null ? org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack.asNMSCopy(item) : ItemStack.EMPTY;
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
    public void onEnable() {
        NMS.super.onEnable();
    }
}
