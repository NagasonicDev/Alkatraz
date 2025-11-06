package me.nagasonic.alkatraz.nms;

import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public interface NMS extends Listener {
    void setInvisible(org.bukkit.entity.Entity e, Player target, boolean invis);
    void setTransparent(org.bukkit.entity.Entity e, Player target, boolean trans);
    void fakeArmor(HumanEntity e, Player target, org.bukkit.inventory.ItemStack helmet, org.bukkit.inventory.ItemStack chest, org.bukkit.inventory.ItemStack legs, org.bukkit.inventory.ItemStack boots);
    default void onEnable(){
        // default: do nothing
    }
}
