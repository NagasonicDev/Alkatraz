package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;

/**
 * Re-syncs equipment stats when a Paper server fires
 * {@code PlayerEquipmentChangeEvent} (Paper 1.19.3+). Registered reflectively
 * because the plugin compiles against spigot-api 1.19, where the event does not
 * exist. On non-Paper servers the periodic reconcile task covers changes.
 */
public final class EquipmentChangeListener {

    private static final String EVENT_CLASS = "org.bukkit.event.player.PlayerEquipmentChangeEvent";

    private EquipmentChangeListener() {}

    public static void tryRegister(Alkatraz plugin) {
        try {
            Class<? extends Event> eventClass = Class.forName(EVENT_CLASS).asSubclass(Event.class);
            Method getPlayer = eventClass.getMethod("getPlayer");
            PluginManager pm = Bukkit.getPluginManager();
            pm.registerEvent(eventClass, new Listener() {}, EventPriority.MONITOR,
                    (listener, event) -> {
                        if (!eventClass.isInstance(event)) return;
                        try {
                            Player player = (Player) getPlayer.invoke(event);
                            if (player != null) {
                                EquipmentStatService.getInstance().syncEquipmentStats(player);
                            }
                        } catch (ReflectiveOperationException e) {
                            plugin.getLogger().warning("Failed to reflect PlayerEquipmentChangeEvent: " + e.getMessage());
                        }
                    }, plugin);
            plugin.getLogger().info("Registered PlayerEquipmentChangeEvent listener for equipment stat re-sync.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("PlayerEquipmentChangeEvent not available; relying on periodic reconcile task.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register PlayerEquipmentChangeEvent listener: " + e.getMessage());
        }
    }
}
