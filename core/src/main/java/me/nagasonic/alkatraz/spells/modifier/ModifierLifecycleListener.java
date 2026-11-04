package me.nagasonic.alkatraz.spells.modifier;

import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.effect.ActiveEffect;
import me.nagasonic.alkatraz.spells.effect.EffectRegistry;
import me.nagasonic.alkatraz.spells.implementation.Buff;
import me.nagasonic.alkatraz.spells.implementation.Debuff;
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
        revokePooledHandles(player, id);
        // Drop any lingering modifier buckets for this player, revoking with the live entity.
        ModifierManager.clearPlayer(player);
    }

    private void revokePooledHandles(Player player, UUID id) {
        for (ActiveEffect effect : EffectRegistry.effectsOn(id)) {
            if (!"buff".equals(effect.sourceSpellId()) && !"debuff".equals(effect.sourceSpellId())) continue;
            if (effect.handle() instanceof AppliedModifier applied) {
                Spell source = effect.sourceSpellId() == null ? null : SpellRegistry.getSpell(effect.sourceSpellId());
                try { applied.remove(player, source); } catch (Exception e) {
                    me.nagasonic.alkatraz.Alkatraz.logDebug("Failed to revoke pooled modifier on cleanup: " + e.getMessage());
                }
            }
        }
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
        Player player = event.getPlayer();
        // World change must not keep pooled spell modifiers alive until expiry.
        clearPooledModifiers(player);
        UUID id = player.getUniqueId();
        // Revoke each buff/debuff handle before dropping its registry entry, so
        // the real stat/attribute change does not survive the world change.
        for (ActiveEffect effect : EffectRegistry.effectsOn(id)) {
            if (!"buff".equals(effect.sourceSpellId()) && !"debuff".equals(effect.sourceSpellId())) continue;
            if (effect.handle() instanceof AppliedModifier applied) {
                Spell source = effect.sourceSpellId() == null ? null : SpellRegistry.getSpell(effect.sourceSpellId());
                try { applied.remove(player, source); } catch (Exception e) {
                    me.nagasonic.alkatraz.Alkatraz.logDebug("Failed to revoke pooled modifier on world change: " + e.getMessage());
                }
            }
        }
        EffectRegistry.effectsOn(id).stream()
                .filter(e -> "buff".equals(e.sourceSpellId()) || "debuff".equals(e.sourceSpellId()))
                .forEach(EffectRegistry::remove);
    }
}