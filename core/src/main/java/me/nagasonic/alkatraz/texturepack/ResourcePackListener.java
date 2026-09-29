package me.nagasonic.alkatraz.texturepack;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public class ResourcePackListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!TexturePackManager.isResourcePackEnabled()) return;

        String url = TexturePackManager.getResourcePackUrl();
        if (url == null || url.isEmpty()) return;

        Player player = event.getPlayer();
        String hash = TexturePackManager.getResourcePackHash();
        String prompt = TexturePackManager.getResourcePackPrompt();
        boolean required = Alkatraz.isResourcePackForced();

        try {
            byte[] hashBytes = hexStringToByteArray(hash);
            player.setResourcePack(
                    url,
                    hashBytes,
                    prompt != null ? prompt : "Alkatraz Texture Pack",
                    required
            );
        } catch (Exception e) {
            Alkatraz.logWarning("Failed to send resource pack to " + player.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        Player player = event.getPlayer();

        switch (status) {
            case ACCEPTED:
                Alkatraz.getInstance().getLogger().info(player.getName() + " accepted the resource pack");
                break;
            case DECLINED:
                Alkatraz.getInstance().getLogger().info(player.getName() + " declined the resource pack");
                break;
            case FAILED_DOWNLOAD:
                Alkatraz.getInstance().getLogger().warning(player.getName() + " failed to download the resource pack");
                break;
        }
    }

    private static final java.util.regex.Pattern NON_HEX = java.util.regex.Pattern.compile("[^0-9a-fA-F]");

    private static volatile String cachedHash;
    private static volatile byte[] cachedBytes;

    private static byte[] hexStringToByteArray(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        if (hex.equals(cachedHash) && cachedBytes != null) return cachedBytes.clone();
        String cleaned = NON_HEX.matcher(hex).replaceAll("");
        int len = cleaned.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i + 1 < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(cleaned.charAt(i), 16) << 4)
                    + Character.digit(cleaned.charAt(i + 1), 16));
        }
        cachedHash = hex;
        cachedBytes = data;
        return data;
    }
}
