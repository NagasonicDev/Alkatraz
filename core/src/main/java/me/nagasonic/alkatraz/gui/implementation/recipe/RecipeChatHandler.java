package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class RecipeChatHandler implements Listener {

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    private static final Map<UUID, BiConsumer<Player, String>> callbacks = new ConcurrentHashMap<>();
    private static RecipeChatHandler instance;

    public static void prompt(Player player, String message, BiConsumer<Player, String> onResult) {
        if (instance == null) {
            instance = new RecipeChatHandler();
            Bukkit.getPluginManager().registerEvents(instance, Alkatraz.getInstance());
        }
        callbacks.put(player.getUniqueId(), onResult);
        player.sendMessage(ColorFormat.format("&7[Recipe Editor] " + message));
        player.sendMessage(lang().get("editor.chat_prompt"));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        BiConsumer<Player, String> callback = callbacks.remove(player.getUniqueId());
        if (callback == null) return;
        event.setCancelled(true);
        callback.accept(player, event.getMessage());
    }
}
