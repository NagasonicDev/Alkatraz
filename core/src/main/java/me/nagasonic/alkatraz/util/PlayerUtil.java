package me.nagasonic.alkatraz.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.List;

import static org.bukkit.Bukkit.getOnlinePlayers;

public class PlayerUtil {
    public static Entity getTargetEntity(Player p, double range){
        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTraceEntities(eye, eye.getDirection(), range);
        if (r != null) { return r.getHitEntity(); }
        return null;
    }

    public static List<? extends Player> getSeenByPlayers(Player player) {
        // Do not hide the player from itself or do anything if the other player cannot see the player
        return getOnlinePlayers()
                .stream()
                .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
                .filter(other -> other.canSee(player))
                .toList();
    }
}
