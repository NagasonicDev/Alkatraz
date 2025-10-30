package me.nagasonic.alkatraz.util;

import me.nagasonic.alkatraz.playerdata.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;

public class PlayerUtil {
    public static Entity getTargetEntity(Player p, double range){
        RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), range);
        if (r != null) { return r.getHitEntity(); }
        return null;
    }
}
