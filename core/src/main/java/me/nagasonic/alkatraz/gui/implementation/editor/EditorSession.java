package me.nagasonic.alkatraz.gui.implementation.editor;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EditorSession {

    private static final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();

    private final String defKey;
    private final File file;
    private YamlConfiguration config;
    private String pendingChatAction;

    public EditorSession(Player player, String defKey) {
        if (defKey == null || !defKey.matches("[A-Za-z0-9_.:-]+")) {
            throw new IllegalArgumentException("Invalid item definition key: " + defKey);
        }
        this.defKey = defKey;
        this.file = new File(Alkatraz.getInstance().getDataFolder(), "magic/items/" + defKey + ".yml");
        reload();
        sessions.put(player.getUniqueId(), this);
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            Alkatraz.logSevere("Failed to save editor changes for " + defKey + ": " + e.getMessage());
        }
    }

    public YamlConfiguration config() { return config; }
    public File file() { return file; }
    public String defKey() { return defKey; }

    public String pendingChatAction() { return pendingChatAction; }
    public void setPendingChatAction(String action) { this.pendingChatAction = action; }
    public void clearPendingChatAction() { this.pendingChatAction = null; }

    public static EditorSession get(UUID uuid) { return sessions.get(uuid); }
    public static void remove(UUID uuid) { sessions.remove(uuid); }
    public static void clearAll() { sessions.clear(); }
}