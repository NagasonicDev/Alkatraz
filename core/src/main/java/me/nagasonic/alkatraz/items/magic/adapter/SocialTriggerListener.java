package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerDispatch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.TradeSelectEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Fires social engraving triggers ({@code on_chat}, {@code on_command},
 * {@code on_villager_trade}).
 */
public final class SocialTriggerListener implements Listener {

    /**
     * Fires {@code alkatraz:on_chat} when a player sends a chat message.
     * {@link AsyncPlayerChatEvent} runs on an async thread, so the trigger is
     * dispatched on the main thread.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> params = new HashMap<>();
        params.put("message", event.getMessage());
        org.bukkit.Bukkit.getScheduler().runTask(Alkatraz.getInstance(), () ->
                TriggerDispatch.dispatch(player, "on_chat", params));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        Map<String, Object> params = new HashMap<>();
        params.put("command", message.split("\\s+")[0].toLowerCase());
        params.put("args", message.contains(" ") ? message.substring(message.indexOf(' ') + 1) : "");
        TriggerDispatch.dispatch(player, "on_command", params);
    }

    /**
     * Fires {@code alkatraz:on_villager_trade} when a player selects a trade in
     * a villager trading GUI.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerTrade(TradeSelectEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        TriggerDispatch.dispatch(player, "on_villager_trade");
    }
}
