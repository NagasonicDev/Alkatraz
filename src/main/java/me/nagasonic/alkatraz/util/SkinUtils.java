package me.nagasonic.alkatraz.util;

import com.google.common.io.CharStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SkinUtils {
    public static JsonObject generateFromURL(String url, boolean slim) throws InterruptedException, ExecutionException {
        return EXECUTOR.<JsonObject>submit(() -> {
            DataOutputStream out = null;
            InputStreamReader reader = null;
            try {
                URL target = new URL("https://api.mineskin.org/generate/url");
                HttpURLConnection con = (HttpURLConnection)target.openConnection();
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("User-Agent", "Alkatraz/" + Alkatraz.getInstance().getDescription().getVersion());
                con.setRequestProperty("Cache-Control", "no-cache");
                con.setRequestProperty("Accept", "application/json");
                con.setRequestProperty("Content-Type", "application/json");
                con.setConnectTimeout(1000);
                con.setReadTimeout(30000);
                out = new DataOutputStream(con.getOutputStream());
                JsonObject req = new JsonObject();
                req.addProperty("url", url);
                req.addProperty("name", "");
                if (slim)
                    req.addProperty("variant", "slim");
                out.writeBytes(req.toString().replace("\\", ""));
                out.close();
                reader = new InputStreamReader(con.getInputStream());
                String str = CharStreams.toString(reader);
                JsonObject output = (new JsonParser()).parse(str).getAsJsonObject();
                JsonObject data = output.get("data").getAsJsonObject();
                con.disconnect();
                return data;
            } finally {
                if (out != null)
                    try {
                        out.close();
                    } catch (IOException iOException) {}
                if (reader != null)
                    try {
                        reader.close();
                    } catch (IOException iOException) {}
            }
        }).get();
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    public static PlayerProfile getOfflineProfile(String name) {
        return Bukkit.getServer().createPlayerProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()), name);
    }

    private static Map<UUID, String> names = new HashMap<>();
    public static PlayerProfile getEffectiveProfile(Player player) {
        String effectiveName = names.getOrDefault(player.getUniqueId(), player.getName());
        PlayerProfile profile = Bukkit.getServer().createPlayerProfile(effectiveName);
        profile.update();
        if (profile.getUniqueId() == null) {
            return getOfflineProfile(effectiveName);
        }
        return profile;
    }
}
