package me.nagasonic.alkatraz.items.magic.adapter;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MagicDamageListener implements Listener {

    private static final Map<UUID, Double> EXPLOSION_BONUS = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMagicExplosionDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        DamageCause cause = event.getCause();
        if (cause != DamageCause.ENTITY_EXPLOSION && cause != DamageCause.BLOCK_EXPLOSION) return;
        double bonus = EXPLOSION_BONUS.getOrDefault(player.getUniqueId(), 0.0);
        if (bonus > 0) {
            event.setDamage(event.getDamage() + bonus);
        }
    }

    public static void registerExplosionBonus(UUID playerId, double bonus) {
        EXPLOSION_BONUS.put(playerId, bonus);
    }

    public static void clearExplosionBonus(UUID playerId) {
        EXPLOSION_BONUS.remove(playerId);
    }

    static double explosionBonus(UUID playerId) {
        return EXPLOSION_BONUS.getOrDefault(playerId, 0.0);
    }
}
