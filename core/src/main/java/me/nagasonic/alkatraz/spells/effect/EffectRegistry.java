package me.nagasonic.alkatraz.spells.effect;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.modifier.AppliedModifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Transient, in-memory, static. The record of which timed effect is on which
 * target, so a dispel can contest and reverse them.
 *
 * <p><b>Nothing here is persisted and nothing here schedules anything.</b> A
 * restart clears the registry wholesale; the only two effects that outlive a
 * restart are Stealth and Disguise, and both are persisted on the profile and
 * therefore re-stamp on join.
 *
 * <p><b>This class never calls the clock.</b> {@code record}, {@code remove},
 * {@code clear} and {@code pruneAll} all take their notion of "now" from a
 * parameter or use none at all. The only clock read is the default in
 * {@link #effectsOn(UUID)}, and the package-private
 * {@link #effectsOn(UUID, long)} overload lets a caller supply it. That is what
 * keeps {@code EffectRegistryTest} free of Bukkit: the test never has to stub a
 * clock, it just states the time.
 *
 * <p><b>Self-pruning.</b> {@link #effectsOn(UUID)} drops every entry whose
 * {@code expiresAtMillis} has passed before it returns, rewriting the list in
 * place. There is no expiry task and no scheduler, so a spell that stamps an
 * effect and forgets to un-stamp still cleans itself up. Concealment entries
 * carry {@code Long.MAX_VALUE} and are removed explicitly by their reversal.
 *
 * <p>Each target's list is a {@link CopyOnWriteArrayList} because the lists are
 * tiny (a handful of effects) and are read far more often than written: a
 * dispel reads every entry on a target in one pass, and iteration must not see
 * a concurrent mutation from a spell landing at the same moment.
 */
public final class EffectRegistry {

    private static final Map<UUID, List<ActiveEffect>> BY_TARGET = new ConcurrentHashMap<>();

    private EffectRegistry() {
    }

    /**
     * Stamps one effect onto a target.
     *
     * <p>Takes no timestamp and reads no clock: both are already on the record.
     * Recording the same effect twice produces two entries, and removing either
     * removes exactly that one.
     *
     * @param effect the entry to stamp; a null effect or a null target is ignored
     */
    public static void record(ActiveEffect effect) {
        if (effect == null || effect.targetId() == null) return;
        BY_TARGET.computeIfAbsent(effect.targetId(), key -> new CopyOnWriteArrayList<>()).add(effect);
    }

    /**
     * Every live effect on a target, with expired entries pruned first.
     *
     * @param targetId the entity carrying the effects
     * @return an immutable snapshot; empty if the target carries nothing
     */
    public static List<ActiveEffect> effectsOn(UUID targetId) {
        return effectsOn(targetId, System.currentTimeMillis());
    }

    /**
     * Package-private seam over {@link #effectsOn(UUID)} that takes the current
     * time instead of reading it, so the {@code expiresAtMillis == now} boundary
     * is assertable exactly rather than approximately.
     *
     * @param targetId  the entity carrying the effects
     * @param nowMillis the time to judge expiry against
     * @return an immutable snapshot; empty if the target carries nothing
     */
    static List<ActiveEffect> effectsOn(UUID targetId, long nowMillis) {
        if (targetId == null) return List.of();
        List<ActiveEffect> effects = BY_TARGET.get(targetId);
        if (effects == null) return List.of();
        List<ActiveEffect> expired = new ArrayList<>();
        effects.removeIf(effect -> {
            if (effect.isExpiredAt(nowMillis)) { expired.add(effect); return true; }
            return false;
        });
        if (Bukkit.getServer() != null && isPrimaryThreadSafe()) {
            for (ActiveEffect effect : expired) {
                if (effect.handle() instanceof AppliedModifier applied) {
                    LivingEntity entity = (LivingEntity) Bukkit.getEntity(targetId);
                    Spell source = effect.sourceSpellId() == null ? null : SpellRegistry.getSpell(effect.sourceSpellId());
                    if (entity != null) {
                        try { applied.remove(entity, source); } catch (Exception e) { Alkatraz.logDebug("Failed to revoke expired modifier: " + e.getMessage()); }
                    }
                }
            }
        }
        if (effects.isEmpty()) BY_TARGET.remove(targetId, effects);
        return List.copyOf(effects);
    }

    /**
     * Drops one entry, matching on record equality — which includes the handle,
     * so removing one of two identical-looking stamps leaves the other.
     *
     * @param effect the entry to drop; a null effect is ignored
     */
    public static void remove(ActiveEffect effect) {
        if (effect == null || effect.targetId() == null) return;
        List<ActiveEffect> effects = BY_TARGET.get(effect.targetId());
        if (effects == null) return;
        effects.remove(effect);
        if (effects.isEmpty()) BY_TARGET.remove(effect.targetId(), effects);
    }

    /**
     * Drops every entry on one target. Used when a player quits or dies, so a
     * relog starts from a clean slate.
     *
     * @param targetId the entity to clear; null is ignored
     */
    public static void clear(UUID targetId) {
        if (targetId == null) return;
        // Revoke applied modifier handles on clear so a quit/death/world change
        // does not leave stale stat/attribute changes on a surviving target.
        if (Bukkit.getServer() != null && isPrimaryThreadSafe()) {
            List<ActiveEffect> effects = BY_TARGET.get(targetId);
            if (effects != null) {
                LivingEntity entity = (LivingEntity) Bukkit.getEntity(targetId);
                Spell source = null;
                for (ActiveEffect effect : List.copyOf(effects)) {
                    if (!(effect.handle() instanceof AppliedModifier applied)) continue;
                    source = effect.sourceSpellId() == null ? null : SpellRegistry.getSpell(effect.sourceSpellId());
                    if (entity != null) {
                        try { applied.remove(entity, source); } catch (Exception e) { Alkatraz.logDebug("Failed to revoke modifier on clear: " + e.getMessage()); }
                    }
                }
            }
        }
        BY_TARGET.remove(targetId);
    }

    /**
     * Drops every entry for every target, including still-live ones. Used on
     * plugin disable/reload so stale effect references cannot outlive the reload.
     */
    public static void clearAll() {
        // Revoke any applied modifier handles before dropping the entries, so
        // reload/disable does not leave stale stat/attribute changes in place.
        if (Bukkit.getServer() != null && isPrimaryThreadSafe()) {
            for (java.util.Map.Entry<UUID, List<ActiveEffect>> entry : new java.util.ArrayList<>(BY_TARGET.entrySet())) {
                for (ActiveEffect effect : java.util.List.copyOf(entry.getValue())) {
                    if (!(effect.handle() instanceof AppliedModifier applied)) continue;
                    LivingEntity entity = (LivingEntity) Bukkit.getEntity(entry.getKey());
                    Spell source = effect.sourceSpellId() == null ? null : SpellRegistry.getSpell(effect.sourceSpellId());
                    if (entity != null) {
                        try { applied.remove(entity, source); } catch (Exception e) { Alkatraz.logDebug("Failed to revoke modifier on clearAll: " + e.getMessage()); }
                    }
                }
            }
        }
        BY_TARGET.clear();
    }

    /**
     * Sweeps every target's list against the current time and drops the map
     * entries that end up empty. Package-private: there is no production caller
     * yet, because {@link #effectsOn(UUID)} already self-prunes on every read.
     * It exists so a maintenance command or a test can sweep the whole registry
     * in one pass without reading every target id first.
     */
    public static void pruneAll() {
        long now = System.currentTimeMillis();
        boolean primary = Bukkit.getServer() != null && isPrimaryThreadSafe();
        List<ActiveEffect> expired = new ArrayList<>();
        for (Map.Entry<UUID, List<ActiveEffect>> entry : BY_TARGET.entrySet()) {
            List<ActiveEffect> effects = entry.getValue();
            for (ActiveEffect effect : List.copyOf(effects)) {
                if (effect.isExpiredAt(now)) expired.add(effect);
            }
            effects.removeIf(effect -> effect.isExpiredAt(now));
            if (effects.isEmpty()) BY_TARGET.remove(entry.getKey(), effects);
        }
        if (primary) {
            for (ActiveEffect effect : expired) {
                if (effect.handle() instanceof AppliedModifier applied) {
                    LivingEntity entity = effect.targetId() == null ? null : (LivingEntity) Bukkit.getEntity(effect.targetId());
                    Spell source = effect.sourceSpellId() == null ? null : SpellRegistry.getSpell(effect.sourceSpellId());
                    if (entity != null) {
                        try { applied.remove(entity, source); } catch (Exception e) { Alkatraz.logDebug("Failed to revoke expired modifier: " + e.getMessage()); }
                    }
                }
            }
        }
    }

    private static boolean isPrimaryThreadSafe() {
        try {
            return Bukkit.isPrimaryThread();
        } catch (RuntimeException e) {
            return true;
        }
    }
}