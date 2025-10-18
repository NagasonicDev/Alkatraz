package me.nagasonic.alkatraz.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Consumer;

public class UpdateChecker implements Listener {

    private static void getVersion(Consumer<String> consumer) {
        try {
            URL url = new URL("https://api.modrinth.com/v3/project/Skonic/version");
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            JsonArray jsonArray = new Gson().fromJson(reader, JsonArray.class);
            JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
            String tag_name = jsonObject.get("version_number").getAsString();
            consumer.accept(tag_name);
        } catch (IOException e) {
            if (Alkatraz.getPluginConfig().getBoolean("debug")) {
                Alkatraz.getInstance().getLogger().info("Checking for updates failed:" + e.getMessage());
            }else {
                Alkatraz.getInstance().getLogger().info("Checking for updates failed");
            }
        }
    }

    public static void checkUpdate(){
        getVersion(v -> {
            if (!Alkatraz.getInstance().getDescription().getVersion().equals(v)) {
                Bukkit.getScheduler().runTask(Alkatraz.getInstance(), () -> {
                    Alkatraz.logWarning("There is a new version of Alkatraz available: " + v);
                    Alkatraz.logWarning("Please don't forget to look at the change log, the update may have important changes that require additional steps to work.");
                    Alkatraz.logWarning("");
                });
                Bukkit.getPluginManager().registerEvents(new Listener() {
                    private final Collection<UUID> messagedOperators = new HashSet<>();

                    @EventHandler
                    public void onOperatorJoin(PlayerJoinEvent e){
                        if (!e.getPlayer().isOp() || messagedOperators.contains(e.getPlayer().getUniqueId())) return;
                        messagedOperators.add(e.getPlayer().getUniqueId());
                        e.getPlayer().sendMessage(
                                Utils.chat("&dA new version of Alkatraz is available: " + v),
                                Utils.chat("&dI recommend you take a look at the update notes"),
                                Utils.chat("&dto see the importance of this update."),
                                Utils.chat("&5"));
                    }
                }, Alkatraz.getInstance());
            }
        });
    }
}
