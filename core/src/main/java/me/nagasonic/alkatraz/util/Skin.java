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
import java.util.concurrent.ConcurrentHashMap;

public class Skin {
    private static final int CACHE_LIMIT = 256;
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final Map<String, Skin> CACHE = new ConcurrentHashMap<>();

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
        try {
            URL url = new URL(urlString);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            if (connection instanceof HttpURLConnection http) {
                http.setRequestMethod("GET");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            JsonObject textures = new JsonParser().parse(reader).getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();
            Skin skin = fromJson(textures);
            if (CACHE.size() > CACHE_LIMIT) CACHE.clear();
            CACHE.put(urlString, skin);
            return skin;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
