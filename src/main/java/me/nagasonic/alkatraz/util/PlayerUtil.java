package me.nagasonic.alkatraz.util;

import me.nagasonic.alkatraz.playerdata.PlayerData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class PlayerUtil {
    private static Map<String, PlayerData> playerData = new HashMap<>();

    public static PlayerData getPlayerData(Player p) {
        if (!playerData.containsKey(p.getUniqueId().toString())){
            PlayerData data = new PlayerData();
            playerData.put(p.getUniqueId().toString(), data);
            return data;
        }
        return playerData.get(p.getUniqueId().toString());
    }

    public static void setPlayerData(Player p, PlayerData data) {
        playerData.put(p.getUniqueId().toString(), data);
    }
}
