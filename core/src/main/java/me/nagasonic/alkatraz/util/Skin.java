package me.nagasonic.alkatraz.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.UUID;
import me.nagasonic.alkatraz.Alkatraz;
import java.util.concurrent.ConcurrentHashMap;

public class Skin {
    private static final int CACHE_LIMIT = 256;
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final long NEGATIVE_CACHE_MS = 60_000L;
    private static final Map<String, Skin> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();

    private String value;
    private String signature;
    private UUID uuid;

    public Skin(String value, String signature) {
        this.value = value == null ? "" : value;
        this.signature = signature == null ? "" : signature;
        this.uuid = UUID.nameUUIDFromBytes(this.value.getBytes());
    }

    public JsonObject toJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("value", value);
        jsonObject.addProperty("signature", signature);
        return jsonObject;
    }

    @Override
    public String toString(){
        return toJson().toString();
    }

    @Override
    public boolean equals(Object other){
        if (other instanceof Skin){
            Skin otherSkin = (Skin) other;
            return value.equals(otherSkin.value) && signature.equals(otherSkin.signature);
        }else {
            return false;
        }
    }

    public static Skin fromJson(JsonObject object){
        return new Skin(object.get("value").getAsString(), object.get("signature").getAsString());
    }

    public static Skin fromURL(String urlString){
        Skin cached = CACHE.get(urlString);
        if (cached != null) return cached;
        Long failedAt = FAILED.get(urlString);
        if (failedAt != null && (System.currentTimeMillis() - failedAt) < NEGATIVE_CACHE_MS) {
            throw new RuntimeException("cached Mojang fetch failure");
        }
        try {
            URL url = new URL(urlString);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            if (connection instanceof HttpURLConnection http) {
                http.setRequestMethod("GET");
            }
            Skin skin;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                JsonObject textures = new JsonParser().parse(reader).getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();
                skin = fromJson(textures);
            }
            if (CACHE.size() >= CACHE_LIMIT) {
                // Evict a single entry instead of dropping the whole cache.
                java.util.Iterator<String> it = CACHE.keySet().iterator();
                if (it.hasNext()) CACHE.remove(it.next());
            }
            FAILED.remove(urlString);
            CACHE.put(urlString, skin);
            return skin;
        } catch (Exception e) {
            FAILED.put(urlString, System.currentTimeMillis());
            throw new RuntimeException(e);
        }
    }

    /**
     * Fetches a skin off the main thread, invoking {@code onSuccess} on the main
     * thread when it becomes available (or is already cached) and {@code onFailure}
     * if the fetch fails. Callers must not touch players from the async path.
     */
    public static void fetchAsync(String urlString,
                                  java.util.function.Consumer<Skin> onSuccess,
                                  java.util.function.Consumer<Throwable> onFailure) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Alkatraz.getInstance(), () -> {
            try {
                Skin skin = fromURL(urlString);
                org.bukkit.Bukkit.getScheduler().runTask(Alkatraz.getInstance(), () -> onSuccess.accept(skin));
            } catch (Throwable t) {
                org.bukkit.Bukkit.getScheduler().runTask(Alkatraz.getInstance(), () -> onFailure.accept(t));
            }
        });
    }

    public String getTexture() {
        return this.value;
    }

    public String getSignature() {
        return this.signature;
    }

    public UUID getUUID() {
        return this.uuid;
    }
}
