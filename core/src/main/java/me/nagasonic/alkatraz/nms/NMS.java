package me.nagasonic.alkatraz.nms;

import me.nagasonic.alkatraz.mobs.MagicEntityType;
import me.nagasonic.alkatraz.util.Skin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Optional;

public interface NMS extends Listener {
    void setInvisible(org.bukkit.entity.Entity e, boolean invis);
    void setTransparent(org.bukkit.entity.Entity e, Player target, boolean trans);
    void fakeArmor(HumanEntity e, Player target, org.bukkit.inventory.ItemStack helmet, org.bukkit.inventory.ItemStack chest, org.bukkit.inventory.ItemStack legs, org.bukkit.inventory.ItemStack boots);
    void fakeExp(Player player, float progress, int level, int totalExp);
    void changeSkin(Player player, List<Player> viewers, Skin skin);
    void changeSkinElse(Player player, List<Player> viewers, Skin skin);
    void registerMagicEntities();
    Optional<Entity> spawnMagicEntity(String key, Location location);

    default Optional<Entity> spawnMagicEntity(MagicEntityType type, Location location) {
        return spawnMagicEntity(type.getId(), location);
    }
    default void onEnable(){
        // default: do nothing
    }
}
