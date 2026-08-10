package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.events.PlayerSpellFailEvent;
import me.nagasonic.alkatraz.events.ResearchCompletedEvent;
import me.nagasonic.alkatraz.events.SpellDiscoveredEvent;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerDispatch;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Fires player-state-related engraving triggers ({@code on_respawn},
 * {@code on_level_up}, {@code on_spell_casted}, {@code on_low_health}, ...).
 */
public final class PlayerStateTriggerListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        TriggerDispatch.dispatch(player, "on_respawn");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLevelUp(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        if (event.getOldLevel() >= event.getNewLevel()) return;
        Map<String, Object> params = new HashMap<>();
        params.put("old_level", event.getOldLevel());
        params.put("new_level", event.getNewLevel());
        TriggerDispatch.dispatch(player, "on_level_up", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onXpGain(PlayerExpChangeEvent event) {
        if (event.getAmount() <= 0) return;
        Player player = event.getPlayer();
        Map<String, Object> params = new HashMap<>();
        params.put("amount", event.getAmount());
        TriggerDispatch.dispatch(player, "on_xp_gain", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Map<String, Object> params = new HashMap<>();
        params.put("food_level", event.getFoodLevel());
        params.put("change", event.getFoodLevel() - player.getFoodLevel());
        TriggerDispatch.dispatch(player, "on_food_change", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getAmount() <= 0) return;
        Map<String, Object> params = new HashMap<>();
        params.put("amount", event.getAmount());
        params.put("reason", event.getRegainReason() != null ? event.getRegainReason().name() : "UNKNOWN");
        TriggerDispatch.dispatch(player, "on_heal", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLowHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double health = player.getHealth();
        double max = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null
                ? player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()
                : 20.0;
        if (health > max * 0.2) return;
        Map<String, Object> params = new HashMap<>();
        params.put("health", health);
        params.put("health_percent", health / max);
        TriggerDispatch.dispatch(player, "on_low_health", params);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) return;
        TriggerDispatch.dispatch(player, "on_first_join");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        TriggerDispatch.dispatch(player, "on_quit");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpellCasted(me.nagasonic.alkatraz.events.PlayerCastEvent event) {
        Player player = event.getCaster();
        Spell spell = event.getSpell();
        ItemStack wand = event.getWand();

        Map<String, Object> params = new HashMap<>();
        if (spell != null) {
            params.put("spell_id", spell.getId());
            if (spell.getElement() != null) {
                params.put("spell_element", spell.getElement().name());
            }
        }
        TriggerDispatch.dispatchHeld(player, "on_spell_casted", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpellFail(PlayerSpellFailEvent event) {
        Player player = event.getPlayer();
        Spell spell = event.getSpell();

        Map<String, Object> params = new HashMap<>();
        if (spell != null) {
            params.put("spell_id", spell.getId());
        }
        params.put("reason", event.getReason());
        TriggerDispatch.dispatchHeld(player, "on_spell_fail", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpellDiscovered(SpellDiscoveredEvent event) {
        Player player = event.getPlayer();
        Spell spell = event.getSpell();

        Map<String, Object> params = new HashMap<>();
        if (spell != null) {
            params.put("spell_id", spell.getId());
            params.put("spell_type", spell.getType().toLowerCase());
            if (spell.getElement() != null) {
                params.put("spell_element", spell.getElement().name());
            }
        }
        TriggerDispatch.dispatch(player, "on_spell_discovered", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResearchComplete(ResearchCompletedEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> params = new HashMap<>();
        params.put("node_id", event.getNode().getId());
        TriggerDispatch.dispatch(player, "on_research_complete", params);
    }
}
