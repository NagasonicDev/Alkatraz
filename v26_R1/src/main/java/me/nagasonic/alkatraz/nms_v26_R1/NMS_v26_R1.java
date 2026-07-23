package me.nagasonic.alkatraz.nms_v26_R1;
import me.nagasonic.alkatraz.nms.NMS;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.commands.CastCommand;
import me.nagasonic.alkatraz.gui.grimoire.GrimoireLecternState;
import me.nagasonic.alkatraz.nms_v26_R1.entity.MagicEntitySpawner;
import me.nagasonic.alkatraz.util.Skin;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public final class NMS_v26_R1 implements NMS {

    @Override
    public void setInvisible(org.bukkit.entity.Entity target, boolean invis) {
        if (!(target instanceof LivingEntity livingTarget)) return;

        if (invis) {
            livingTarget.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY,
                    Integer.MAX_VALUE,
                    1,
                    false,
                    false,
                    false
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
                (item) -> item != null ? org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item) : ItemStack.EMPTY;
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
    public void registerMagicEntities() {
    }

    @Override
    public Optional<org.bukkit.entity.Entity> spawnMagicEntity(String key, Location location) {
        return MagicEntitySpawner.INSTANCE.spawnMagicEntity(key, location);
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
            player.hidePlayer(other);
        }

        try {
            player.showPlayer(Alkatraz.getInstance(), other);
        } catch (NoSuchMethodError ignored) {
            player.showPlayer(other);
        }
    }
    @Override
    public boolean openGrimoireLectern(Player player, org.bukkit.inventory.ItemStack writtenBook, String title,
                                       int startPage, int totalPages, java.util.function.Consumer<Integer> onPageChange) {
        try {
            ServerPlayer sp = ((CraftPlayer) player).getHandle();

            org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) writtenBook.getItemMeta();
            java.util.List<String> rawPages = (meta != null && meta.hasPages()) ? meta.getPages() : java.util.Collections.emptyList();

            List<Filterable<Component>> bookPages = new ArrayList<>();
            for (String rawPage : rawPages) {
                try {
                    JsonElement json = JsonParser.parseString(rawPage);
                    Component component = ComponentSerialization.CODEC
                            .parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                            .result()
                            .orElse(Component.literal(rawPage));
                    bookPages.add(Filterable.passThrough(component));
                } catch (Exception ex) {
                    bookPages.add(Filterable.passThrough(Component.literal(rawPage)));
                }
            }

            WrittenBookContent bookContent = new WrittenBookContent(
                    Filterable.passThrough(title),
                    meta != null && meta.getAuthor() != null ? meta.getAuthor() : "Alkatraz",
                    0,
                    bookPages,
                    true
            );

            ItemStack nmsBook = new ItemStack(Items.WRITTEN_BOOK);
            nmsBook.set(DataComponents.WRITTEN_BOOK_CONTENT, bookContent);

            SimpleContainer lecternContainer = new SimpleContainer(1);
            lecternContainer.setItem(0, nmsBook);

            SimpleContainerData pageData = new SimpleContainerData(1);
            pageData.set(0, startPage);

            int containerId = sp.containerMenu.containerId;
            if (containerId == 0) {
                containerId = 1;
            } else {
                containerId = (containerId + 1) % 100;
                if (containerId == 0) containerId = 1;
            }
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
            sp.initMenu(menu);
            sp.connection.send(new ClientboundOpenScreenPacket(containerId, MenuType.LECTERN, Component.literal(title)));
            menu.sendAllDataToRemote();

            return true;
        } catch (Exception e) {
            Alkatraz.logWarning("Failed to open fake lectern: " + e.getMessage());
            return false;
        }
    }

    private final java.util.Map<java.util.UUID, BlockPos> fakeLecternPositions = new java.util.HashMap<>();

    @Override
    public void spawnGrimoireLectern(Player player) {
        try {
            ServerPlayer sp = ((CraftPlayer) player).getHandle();
            Direction playerFacing = Direction.fromYRot(player.getLocation().getYaw());
            BlockPos pos = sp.blockPosition().relative(playerFacing, 2);

            Direction lecternFacing = playerFacing.getOpposite();
            BlockState lecternState = Blocks.LECTERN.defaultBlockState()
                    .setValue(LecternBlock.FACING, lecternFacing)
                    .setValue(LecternBlock.HAS_BOOK, true);

            sp.connection.send(new ClientboundBlockUpdatePacket(pos, lecternState));

            ItemStack nmsBook = new ItemStack(Items.WRITTEN_BOOK);
            WrittenBookContent bookContent = new WrittenBookContent(
                    Filterable.passThrough("Grimoire"),
                    "Alkatraz",
                    0,
                    List.of(
                            Filterable.passThrough(Component.literal("\u00A7lPage 1\n\u00A7rThis is a test grimoire page.")),
                            Filterable.passThrough(Component.literal("\u00A7lPage 2\n\u00A7rSecond test page."))
                    ),
                    true
            );
            nmsBook.set(DataComponents.WRITTEN_BOOK_CONTENT, bookContent);

            LecternBlockEntity blockEntity = new LecternBlockEntity(pos, lecternState);
            blockEntity.setBook(nmsBook);
            blockEntity.setPage(0);

            sp.connection.send(blockEntity.getUpdatePacket());
            fakeLecternPositions.put(player.getUniqueId(), pos);

            player.sendMessage("\u00A7aSpawned fake lectern in front of you!");
        } catch (Exception e) {
            Alkatraz.logWarning("Failed to spawn fake lectern: " + e.getMessage());
            player.sendMessage("\u00A7cFailed to spawn fake lectern: " + e.getMessage());
        }
    }

    @Override
    public void removeGrimoireLectern(Player player) {
        try {
            ServerPlayer sp = ((CraftPlayer) player).getHandle();
            BlockPos pos = fakeLecternPositions.remove(player.getUniqueId());
            if (pos != null) {
                sp.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState()));
                player.sendMessage("\u00A7cRemoved fake lectern.");
            } else {
                player.sendMessage("\u00A7cNo fake lectern to remove.");
            }
        } catch (Exception e) {
            Alkatraz.logWarning("Failed to remove fake lectern: " + e.getMessage());
        }
    }


    @Override
    public void onEnable() {
        NMS.super.onEnable();
    }
}
