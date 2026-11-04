package me.nagasonic.alkatraz.spells.modifier;

import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.effect.EffectRegistry;
import me.nagasonic.alkatraz.spells.implementation.Buff;
import me.nagasonic.alkatraz.spells.implementation.Debuff;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class ModifierLifecycleListener implements Listener {

    private void cleanup(Player player) {
        if (player == null) return;
        clearPooledModifiers(player);
        UUID id = player.getUniqueId();
        EffectRegistry.effectsOn(id).stream()
                .filter(e -> "buff".equals(e.sourceSpellId()) || "debuff".equals(e.sourceSpellId()))
                .forEach(EffectRegistry::remove);
    }

    private void clearPooledModifiers(Player player) {
        Spell spell = SpellRegistry.getAllSpells().get(Buff.class);
        if (spell instanceof Buff) {
            ((Buff) spell).clearActiveModifiersFor(player);
        }
        spell = SpellRegistry.getAllSpells().get(Debuff.class);
        if (spell instanceof Debuff) {
            ((Debuff) spell).clearActiveModifiersFor(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            cleanup((Player) event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        // World change must not keep pooled spell modifiers alive until expiry.
        clearPooledModifiers(event.getPlayer());
        UUID id = event.getPlayer().getUniqueId();
        // Drop the effect-registry entries tied to pooled modifiers only, rather
        // than clearing every effect. Wipe anything still carrying the buff/debuff
        // source so dispel does not resurrect stale state.
        EffectRegistry.effectsOn(id).stream()
                .filter(e -> "buff".equals(e.sourceSpellId()) || "debuff".equals(e.sourceSpellId()))
                .forEach(EffectRegistry::remove);
    }
}
