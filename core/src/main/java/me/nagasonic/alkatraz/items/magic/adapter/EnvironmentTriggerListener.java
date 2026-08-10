package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.items.magic.trigger.TriggerDispatch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Fires environment-related engraving triggers ({@code on_lightning},
 * {@code on_fire_damage}, {@code on_rain}, ...).
 */
public final class EnvironmentTriggerListener implements Listener {

    /**
     * Maps a {@link EntityDamageEvent.DamageCause} to the environment trigger
     * key that should fire, or {@code null} if the cause is not an
     * environment-driven trigger.
     */
    public static String triggerForCause(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case LIGHTNING -> "on_lightning";
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> "on_explosion_damage";
            case FIRE, FIRE_TICK, LAVA -> "on_fire_damage";
            case DROWNING -> "on_drown";
            case FREEZE -> "on_freeze";
            case FALL -> "on_fall_damage";
            case VOID -> "on_void";
            case STARVATION -> "on_starve";
            case SUFFOCATION -> "on_suffocate";
            default -> null;
        };
    }

    /**
     * Fires the environment trigger matching the damage cause when a player
     * takes that kind of damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnvironmentDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        String trigger = triggerForCause(event.getCause());
        if (trigger == null) return;

        Map<String, Object> params = new HashMap<>();
        params.put("damage", event.getFinalDamage());
        params.put("cause", event.getCause().name());
        TriggerDispatch.dispatch(player, trigger, params);
    }

    /**
     * Fires {@code alkatraz:on_rain} when rain starts and
     * {@code alkatraz:on_thunder} when a thunderstorm starts.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!event.toWeatherState()) return;
        for (Player player : event.getWorld().getPlayers()) {
            TriggerDispatch.dispatch(player, "on_rain");
            if (event.getWorld().isThundering()) {
                TriggerDispatch.dispatch(player, "on_thunder");
            }
        }
    }
}
