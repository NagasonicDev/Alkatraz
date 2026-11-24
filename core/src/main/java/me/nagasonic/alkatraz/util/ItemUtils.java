package me.nagasonic.alkatraz.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;

public class ItemUtils {

    /** Cached-only variant: never blocks on HTTP. Returns null on a cache miss and prefetches async. */
    public static ItemStack headFromUuidCached(String id) {
        String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false";
        Skin skin = Skin.getCached(url);
        if (skin == null) {
            Skin.fetchAsync(url, ignored -> { }, ignored -> { });
            return null;
        }
        return headFromBase64(skin.getTexture());
    }

    private static PlayerProfile getProfile(String url) {
        notNull(url, "url");
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID()); // Get a new player profile
        PlayerTextures textures = profile.getTextures();
        URL urlObject;
        try {
            urlObject = new URL(url);
        } catch (MalformedURLException exception) {
            throw new RuntimeException("Invalid URL", exception);
        }
        textures.setSkin(urlObject); // Set the skin of the player profile to the URL
        profile.setTextures(textures); // Set the textures back to the profile
        return profile;
    }

    public static ItemStack headWithUrl(ItemStack item, String url) {
        PlayerProfile profile = getProfile(url);
//        Skonic.info(profile.getTextures().getSkin().toString()); // ERROR : SPAM
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwnerProfile(profile); // Set the owning player of the head to the player profile
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack headFromBase64(String base64) {
        ItemStack item = getPlayerSkullItem();
        try {
            return headWithBase64(item, base64);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static URL getUrlFromBase64(String base64) throws MalformedURLException {
        String decoded = new String(Base64.getDecoder().decode(base64));
        JsonObject jsonObject = JsonParser.parseString(decoded).getAsJsonObject();
        // Extract the "textures" JSONObject
        JsonObject textures = jsonObject.get("textures").getAsJsonObject();

        // Extract the "SKIN" JSONObject and then the "url"
        String url = textures.getAsJsonObject("SKIN").get("url").getAsString();
        return new URL(url);
    }

    public static ItemStack headWithBase64(ItemStack item, String base64) throws MalformedURLException {
        notNull(item, "item");
        notNull(base64, "base64");

        return headWithUrl(item, getUrlFromBase64(base64).toString());
    }

    public static ItemStack getPlayerSkullItem() {
        Material mat = MaterialCompat.resolve("PLAYER_HEAD");
        if (mat != null) {
            return new ItemStack(mat);
        }
        return new ItemStack(Material.STONE);
    }

    private static void notNull(Object o, String name) {
        if (o == null) {
            throw new NullPointerException(name + " should not be null!");
        }
    }
}
