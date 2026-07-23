package me.nagasonic.alkatraz.nms_v1_21_R1;
import me.nagasonic.alkatraz.nms.NMS;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.util.Skin;
import me.nagasonic.alkatraz.commands.CastCommand;
import me.nagasonic.alkatraz.gui.grimoire.GrimoireLecternState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.RelativeMovement;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_21_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack;
import org.bukkit.entity.Horse;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import net.minecraft.world.item.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import me.nagasonic.alkatraz.nms_v1_21_R1.entity.MagicEntitySpawner;
import java.util.*;
import java.util.function.Consumer;

public final class NMS_v1_21_R1 implements NMS {

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
                (item) -> item != null ? org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack.asNMSCopy(item) : ItemStack.EMPTY;
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
        profile.getProperties().removeAll("textures");
        profile.getProperties().put("textures", new Property("textures", skin.getTexture(), skin.getSignature()));
        for (Player other : viewers){
            if (other != player){
                hideAndShow(other, player);
            }
        }
    }

    @Override
    public void registerMagicEntities() {

    }

    @Override
    public Optional<org.bukkit.entity.Entity> spawnMagicEntity(String key, Location location) {
        return MagicEntitySpawner.INSTANCE.spawnMagicEntity(key, location);
    }

    public void refresh(Player player){
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        resendInfoPackets(player, player);
        CommonPlayerSpawnInfo info = nmsPlayer.createCommonSpawnInfo(nmsPlayer.serverLevel());
        nmsPlayer.connection.send(new ClientboundRespawnPacket(info, (byte) 0));
        Location l = player.getLocation();
        Set<RelativeMovement> relative = EnumSet.noneOf(RelativeMovement.class);
        int teleportId = nmsPlayer.level().getServer().getTickCount();
        ClientboundPlayerPositionPacket pos = new ClientboundPlayerPositionPacket(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(), relative, teleportId);
        nmsPlayer.connection.send(pos);
        nmsPlayer.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().getHeldItemSlot()));
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
    public boolean openGrimoireLectern(Player player, org.bukkit.inventory.ItemStack writtenBook, String title,
                                       int startPage, int totalPages, java.util.function.Consumer<Integer> onPageChange) {
        try {
            ServerPlayer sp = ((CraftPlayer) player).getHandle();

            SimpleContainer lecternContainer = new SimpleContainer(1);
            lecternContainer.setItem(0, CraftItemStack.asNMSCopy(writtenBook));

            SimpleContainerData pageData = new SimpleContainerData(1);
            pageData.set(0, startPage);

            int containerId = sp.containerMenu.containerId;
            final int pages = totalPages;
            final Player bkPlayer = player;

            LecternMenu menu = new LecternMenu(containerId, lecternContainer, pageData, sp.getInventory()) {
                private int trackedPage = startPage;

                @Override
                public boolean clickMenuButton(net.minecraft.world.entity.player.Player nmsPlayer, int buttonId) {
                    if (buttonId == 0) {
                        CastCommand.castFromGrimoire(bkPlayer);
                        bkPlayer.closeInventory();
                        return true;
                    }
                    if (buttonId == 3) {
                        CastCommand.castFromGrimoire(bkPlayer);
                        bkPlayer.closeInventory();
                        return true;
                    }
                    boolean result = super.clickMenuButton(nmsPlayer, buttonId);
                    if (result) {
                        if (buttonId == 1) trackedPage = Math.max(0, trackedPage - 1);
                        else if (buttonId == 2) trackedPage = Math.min(pages - 1, trackedPage + 1);
                        else if (buttonId >= 100) trackedPage = Math.min(pages - 1, buttonId - 100);
                        onPageChange.accept(trackedPage);
                    }
                    return result;
                }
            };
            menu.checkReachable = false;

            sp.containerMenu = menu;
            sp.connection.send(new ClientboundOpenScreenPacket(containerId, MenuType.LECTERN, Component.literal(title)));
            menu.sendAllDataToRemote();

            return true;
        } catch (Exception e) {
            Alkatraz.logWarning("Failed to open fake lectern: " + e.getMessage());
            return false;
        }
    }



    @Override
    public void onEnable() {
        NMS.super.onEnable();
    }
}
