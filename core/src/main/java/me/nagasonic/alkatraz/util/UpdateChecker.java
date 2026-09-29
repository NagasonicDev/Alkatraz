package me.nagasonic.alkatraz.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class UpdateChecker implements Listener {

    private static final String VERSION_URL = "https://api.modrinth.com/v3/project/Alkatraz/version";
    private static final AtomicBoolean CHECKED = new AtomicBoolean(false);

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    private static void getVersion(Consumer<String> consumer) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(VERSION_URL).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                JsonArray jsonArray = new Gson().fromJson(reader, JsonArray.class);
                JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
                String tag_name = jsonObject.get("version_number").getAsString();
                consumer.accept(tag_name);
            }
        } catch (IOException | RuntimeException e) {
            Alkatraz.logDebug("Checking for updates failed: " + e.getMessage());
            Alkatraz.logInfo("Checking for updates failed");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Entry point kept for {@code Alkatraz.onEnable()}. The network work is pushed
     * onto an async task so a slow or unreachable Modrinth can never stall server
     * startup.
     */
    public static void checkUpdate(){
        if (!CHECKED.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskAsynchronously(Alkatraz.getInstance(), () -> fetchAndAnnounce());
    }

    private static void fetchAndAnnounce() {
        getVersion(v -> {
            if (!Alkatraz.getInstance().getDescription().getVersion().equals(v)) {
                Bukkit.getScheduler().runTask(Alkatraz.getInstance(), () -> {
                    Alkatraz.logWarning("There is a new version of Alkatraz available: " + v);
                    Alkatraz.logWarning("Please don't forget to look at the change log, the update may have important changes that require additional steps to work.");
                    Alkatraz.logWarning("");
                    Bukkit.getPluginManager().registerEvents(new Listener() {
                        private final Collection<UUID> messagedOperators = new HashSet<>();

                        @EventHandler
                        public void onOperatorJoin(PlayerJoinEvent e){
                            if (!e.getPlayer().isOp() || messagedOperators.contains(e.getPlayer().getUniqueId())) return;
                            messagedOperators.add(e.getPlayer().getUniqueId());
                            e.getPlayer().sendMessage(
                                    lang().get("update.new_version", "version", v),
                                    lang().get("update.recommend_notes"),
                                    lang().get("update.see_importance"));
                        }
                    }, Alkatraz.getInstance());
                });
            }
        });
    }
}
