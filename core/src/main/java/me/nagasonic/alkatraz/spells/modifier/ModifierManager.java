package me.nagasonic.alkatraz.spells.modifier;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.effect.ActiveEffect;
import me.nagasonic.alkatraz.spells.modifier.AppliedModifier;
import org.bukkit.Bukkit;
import me.nagasonic.alkatraz.spells.effect.EffectKind;
import me.nagasonic.alkatraz.spells.effect.EffectRegistry;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory manager for {@link ModifierInstance}s.
 *
 * <p>Provides the grant / revoke / list entry points promised by Wave 1 plus
 * expiry pruning and idempotent timeout handling. It deliberately avoids NMS:
 * side effects on a {@link LivingEntity} happen only through the optional
 * {@link AppliedModifier} handle stored on each instance, and stat side effects
 * go through the existing {@link me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile}
 * (via that handle) instead of a new path.
 */
public final class ModifierManager {

    private static final Map<String, List<ModifierInstance>> BY_BUCKET = new ConcurrentHashMap<>();

    private ModifierManager() {
    }

    // ---- bucket handling ----

    private static String bucketKey(ModifierInstance instance) {
        return instance.scope().name() + ":" + instance.scopeKey();
    }

    private static List<ModifierInstance> bucket(ModifierInstance instance, boolean create) {
        String key = bucketKey(instance);
        List<ModifierInstance> existing = BY_BUCKET.get(key);
        if (existing != null) return existing;
        if (!create) return null;
        return BY_BUCKET.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
    }

    private static void pruneBucket(List<ModifierInstance> list, long nowMillis) {
        if (list == null) return;
        for (ModifierInstance instance : new ArrayList<>(list)) {
            if (!instance.isExpiredAt(nowMillis)) continue;
            AppliedModifier applied = instance.getAppliedModifier();
            if (applied != null && instance.scope() == ModifierInstance.Scope.PLAYER
                    && Bukkit.getServer() != null) {
                try {
                    UUID uid = UUID.fromString(instance.scopeKey());
                    LivingEntity entity = (LivingEntity) Bukkit.getEntity(uid);
                    Spell source = instance.source() == null ? null : SpellRegistry.getSpell(instance.source());
                    if (entity != null) {
                        applied.remove(entity, source);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        list.removeIf(instance -> instance.isExpiredAt(nowMillis));
        // Trim empty buckets lazily in pruneAll().
    }

    // ---- core data-level grant ----

    /**
     * Grants a modifier in its own (possibly scope-keyed) bucket, applying
     * idempotent timeout handling:
     * <ul>
     *   <li>expired entries are removed first;</li>
     *   <li>{@link ModifierInstance.StackPolicy#IGNORE} keeps the existing live entry;</li>
     *   <li>{@link ModifierInstance.StackPolicy#REPLACE}/{@code REFRESH}/{@code STRONGEST}
     *       remove the previous entry with the same id;</li>
     *   <li>{@link ModifierInstance.StackPolicy#STACK} mints a fresh id so entries do not collide.</li>
     * </ul>
     */
    public static ModifierInstance grant(ModifierInstance instance) {
        if (instance == null) return null;
        long now = System.currentTimeMillis();
        ModifierInstance candidate = instance;
        List<ModifierInstance> list = bucket(candidate, true);
        pruneBucket(list, now);

        ModifierInstance existing = findById(list, candidate.id());
        if (existing != null) {
            switch (candidate.stackPolicy()) {
                case IGNORE -> {
                    return existing;
                }
                case REPLACE, REFRESH, STRONGEST -> {
                    list.remove(existing);
                }
                case STACK -> {
                    candidate = candidate.withId(UUID.randomUUID());
                }
            }
        }
        list.add(candidate);
        return candidate;
    }

    public static ModifierInstance grant(UUID playerId, ModifierInstance instance) {
        if (instance == null) return null;
        return grant(instance.withScope(ModifierInstance.Scope.PLAYER,
                playerId == null ? instance.scopeKey() : playerId.toString()));
    }

    public static ModifierInstance grant(String scopeKey, ModifierInstance.Scope scope, ModifierInstance instance) {
        if (instance == null) return null;
        return grant(instance.withScope(scope == null ? ModifierInstance.Scope.GROUP : scope,
                scopeKey == null ? instance.scopeKey() : scopeKey));
    }

    /**
     * Grants (and, when a handle is present, applies) a modifier to an entity.
     * Side effects use the existing {@link AppliedModifier} path; expiry is
     * recorded through {@link EffectRegistry} when an expiry time is present.
     */
    public static ModifierInstance grant(LivingEntity target, ModifierInstance instance, Spell source) {
        return grant(target, instance, source, 0);
    }

    public static ModifierInstance grant(LivingEntity target, ModifierInstance instance, Spell source, int durationTicks) {
        if (instance == null) return null;
        // Track whether an entry with this id already existed, so an IGNORE policy can
        // avoid re-applying while a genuinely new grant still applies.
        boolean existedBefore = false;
        ModifierInstance granted;
        if (target instanceof Player player) {
            if (instance.id() != null) {
                existedBefore = findById(player.getUniqueId(), instance.id()) != null;
            }
            granted = grant(player.getUniqueId(), instance);
        } else {
            if (instance.id() != null) {
                for (ModifierInstance m : list(instance.scope(), instance.scopeKey())) {
                    if (instance.id().equals(m.id())) { existedBefore = true; break; }
                }
            }
            granted = grant(instance);
        }

        // Idempotent: an IGNORE that returned the existing entry must not re-apply.
        // REPLACE/REFRESH/STRONGEST always apply the new value; STACK always applies.
        boolean appliedAgain = (target != null && granted != null && granted.appliedModifier() != null)
                && (instance.stackPolicy() != ModifierInstance.StackPolicy.IGNORE
                    || !existedBefore);
        if (target != null && granted != null && granted.appliedModifier() != null && appliedAgain) {
            granted.appliedModifier().apply(target, source);
        }

        if (target != null && granted != null && granted.expiresAtMillis() != Long.MAX_VALUE) {
            long now = System.currentTimeMillis();
            long expires = durationTicks > 0
                    ? now + (long) durationTicks * ActiveEffect.TICKS_TO_MILLIS
                    : granted.expiresAtMillis();
            EffectRegistry.record(new ActiveEffect(
                    target.getUniqueId(),
                    granted.type() == ModifierInstance.Type.ATTRIBUTE ? EffectKind.ATTRIBUTE_MODIFIER : EffectKind.STAT_MODIFIER,
                    granted.source(),
                    null,
                    0,
                    now,
                    expires,
                    granted.getAppliedModifier()));
        }
        return granted;
    }

    // ---- revoke ----

    public static boolean revoke(UUID playerId, UUID instanceId) {
        return revoke(ModifierInstance.Scope.PLAYER,
                playerId == null ? "" : playerId.toString(), instanceId);
    }

    public static boolean revoke(ModifierInstance.Scope scope, String scopeKey, UUID instanceId) {
        if (instanceId == null) return false;
        String key = (scope == null ? ModifierInstance.Scope.PLAYER : scope).name() + ":" + (scopeKey == null ? "" : scopeKey);
        List<ModifierInstance> list = BY_BUCKET.get(key);
        if (list == null) return false;
        int before = list.size();
        list.removeIf(m -> instanceId.equals(m.id()));
        boolean removed = list.size() < before;
        if (list.isEmpty()) BY_BUCKET.remove(key, list);
        return removed;
    }

    public static boolean revoke(ModifierInstance instance) {
        if (instance == null) return false;
        return revoke(instance.scope(), instance.scopeKey(), instance.id());
    }

    public static boolean revoke(LivingEntity target, ModifierInstance instance, Spell source) {
        if (instance == null) return false;
        boolean removed = revoke(instance);
        if (removed && target != null && instance.appliedModifier() != null) {
            instance.appliedModifier().remove(target, source);
        }
        return removed;
    }

    public static int revokeBySource(UUID playerId, String source) {
        if (source == null) return 0;
        String key = ModifierInstance.Scope.PLAYER.name() + ":" + playerId;
        List<ModifierInstance> list = BY_BUCKET.get(key);
        if (list == null) return 0;
        int before = list.size();
        list.removeIf(m -> source.equals(m.source()));
        int count = before - list.size();
        if (list.isEmpty()) BY_BUCKET.remove(key, list);
        return count;
    }

    // ---- list ----

    public static List<ModifierInstance> list(UUID playerId) {
        return list(ModifierInstance.Scope.PLAYER, playerId == null ? "" : playerId.toString());
    }

    public static List<ModifierInstance> list(ModifierInstance.Scope scope, String scopeKey) {
        String key = (scope == null ? ModifierInstance.Scope.PLAYER : scope).name() + ":" + (scopeKey == null ? "" : scopeKey);
        List<ModifierInstance> list = BY_BUCKET.get(key);
        if (list == null) return List.of();
        long now = System.currentTimeMillis();
        pruneBucket(list, now);
        return List.copyOf(list);
    }

    public static List<ModifierInstance> listAll() {
        long now = System.currentTimeMillis();
        List<ModifierInstance> out = new ArrayList<>();
        for (List<ModifierInstance> list : BY_BUCKET.values()) {
            pruneBucket(list, now);
            out.addAll(list);
        }
        return List.copyOf(out);
    }

    public static List<ModifierInstance> list(LivingEntity target) {
        if (target instanceof Player player) return list(player.getUniqueId());
        return List.of();
    }

    // ---- expiry / maintenance ----

    public static void pruneExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, List<ModifierInstance>> entry : new ArrayList<>(BY_BUCKET.entrySet())) {
            pruneBucket(entry.getValue(), now);
            if (entry.getValue().isEmpty()) BY_BUCKET.remove(entry.getKey(), entry.getValue());
        }
    }

    public static void pruneAll() {
        pruneExpired();
    }

    public static void clear() {
        // Revoke applied handles before dropping the buckets.
        if (Bukkit.getServer() != null && isPrimaryThreadSafe()) {
            for (java.util.Map.Entry<String, List<ModifierInstance>> entry : new java.util.ArrayList<>(BY_BUCKET.entrySet())) {
                for (ModifierInstance instance : java.util.List.copyOf(entry.getValue())) {
                    AppliedModifier applied = instance.getAppliedModifier();
                    if (applied == null) continue;
                    LivingEntity entity = null;
                    if (instance.scope() == ModifierInstance.Scope.PLAYER) {
                        try { entity = (LivingEntity) Bukkit.getEntity(UUID.fromString(instance.scopeKey())); } catch (Exception ignored) { }
                    }
                    Spell source = instance.source() == null ? null : SpellRegistry.getSpell(instance.source());
                    if (entity != null) {
                        try { applied.remove(entity, source); } catch (Exception e) { Alkatraz.logDebug("Failed to revoke modifier on clear: " + e.getMessage()); }
                    }
                }
            }
        }
        BY_BUCKET.clear();
    }

    public static void clearAll() { clear(); }

    public static void onPlayerQuit(UUID playerId) { clearPlayer(playerId); }
    public static void onPlayerQuit(Player player) { clearPlayer(player); }
    public static void onQuit(UUID playerId) { clearPlayer(playerId); }
    public static void onQuit(Player player) { clearPlayer(player); }
    public static void clearPlayer(LivingEntity entity) {
        clearPlayer(entity == null ? null : entity.getUniqueId(), entity);
    }
    public static void clearPlayer(UUID playerId) { clearPlayer(playerId, null); }
    public static void clearPlayer(UUID playerId, LivingEntity entity) {
        if (playerId == null) return;
        String bucketKey = ModifierInstance.Scope.PLAYER.name() + ":" + playerId;
        if (Bukkit.getServer() != null && isPrimaryThreadSafe()) {
            List<ModifierInstance> instances = BY_BUCKET.get(bucketKey);
            if (instances != null) {
                LivingEntity target = entity;
                if (target == null) {
                    try { target = (LivingEntity) Bukkit.getEntity(playerId); } catch (Exception ignored) { }
                }
                // Fall back to nothing rather than dropping the handle silently: if we
                // cannot resolve the entity, leave applied data untouched (no bucket drop
                // masquerading as a revoke).
                if (target != null) {
                    for (ModifierInstance instance : List.copyOf(instances)) {
                        AppliedModifier applied = instance.getAppliedModifier();
                        if (applied == null) continue;
                        Spell source = instance.source() == null ? null : SpellRegistry.getSpell(instance.source());
                        try { applied.remove(target, source); } catch (Exception e) {
                            Alkatraz.logDebug("Failed to revoke modifier on clearPlayer: " + e.getMessage());
                        }
                    }
                }
            }
        }
        BY_BUCKET.remove(bucketKey);
    }
    public static void clearBucket(String bucketKey) {
        if (bucketKey == null) return;
        BY_BUCKET.remove(bucketKey);
    }

    private static boolean isPrimaryThreadSafe() {
        try {
            return Bukkit.isPrimaryThread();
        } catch (RuntimeException e) {
            return true;
        }
    }

    // ---- query helpers ----

    public static ModifierInstance findById(UUID playerId, UUID instanceId) {
        for (ModifierInstance m : list(playerId)) {
            if (instanceId != null && instanceId.equals(m.id())) return m;
        }
        return null;
    }

    public static boolean has(UUID playerId, UUID instanceId) {
        return findById(playerId, instanceId) != null;
    }

    public static int count(UUID playerId) {
        return list(playerId).size();
    }

    public static ModifierInstance strongest(UUID playerId, String statOrAttributeId) {
        ModifierInstance best = null;
        for (ModifierInstance m : list(playerId)) {
            if (!statOrAttributeId.equals(m.statOrAttributeId())) continue;
            if (best == null || m.amount() > best.amount()) best = m;
        }
        return best;
    }

    private static ModifierInstance findById(List<ModifierInstance> list, UUID id) {
        if (id == null) return null;
        for (ModifierInstance m : list) {
            if (id.equals(m.id())) return m;
        }
        return null;
    }
}