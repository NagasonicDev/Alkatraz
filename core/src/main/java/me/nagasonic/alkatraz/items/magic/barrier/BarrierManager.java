package me.nagasonic.alkatraz.items.magic.barrier;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.condition.implementation.CooldownTracker;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BarrierManager {

    private static final Map<UUID, Map<String, BarrierSession>> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Double>> PERSISTED_HP = new ConcurrentHashMap<>();
    private static boolean scheduled = false;

    private BarrierManager() {}

    public static void initialize() {
        if (scheduled) return;
        scheduled = true;
        Alkatraz.getInstance().getServer().getScheduler().runTaskTimer(Alkatraz.getInstance(), () -> {
            for (Map<String, BarrierSession> byItem : new ArrayList<>(SESSIONS.values())) {
                for (BarrierSession session : new ArrayList<>(byItem.values())) {
                    if (session != null && !session.isDisposed()) {
                        session.tick();
                    }
                }
            }
        }, 1L, 1L);
    }

    public static boolean start(Player caster, MagicItemInstance sourceItem, EquipmentSlot slot,
                                BarrierConfig config, String itemKey) {
        long now = System.currentTimeMillis();
        if (CooldownTracker.isOnCooldown(caster.getUniqueId(), itemKey, now)) {
            playDenySound(caster, config);
            return false;
        }
        UUID uuid = caster.getUniqueId();
        Map<String, BarrierSession> byItem = SESSIONS.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        BarrierSession existing = byItem.get(itemKey);
        if (existing != null && !existing.isDisposed()) {
            if (config.refreshOnResummon()) {
                existing.refresh();
            }
            return true;
        }
        long active = byItem.values().stream().filter(s -> s != null && !s.isDisposed()).count();
        if (config.maxSimultaneous() > 0 && active >= config.maxSimultaneous()) {
            return true;
        }
        double startHp = config.persistHpAcrossSummons()
                ? persistedHpOf(uuid, itemKey, config.hitpoints())
                : config.hitpoints();
        BarrierSession session = new BarrierSession(caster, sourceItem, slot, config, itemKey, null, startHp);
        session.start();
        byItem.put(itemKey, session);
        if (config.cooldown().on() == BarrierConfig.CooldownConfig.CooldownMoment.SUMMON) {
            markShatterCooldown(uuid, itemKey, now, config.cooldown().seconds());
        }
        return true;
    }

    public static void disposeFor(Player player, BarrierConfig.ResetOn moment) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        Map<String, BarrierSession> byItem = SESSIONS.get(uuid);
        if (byItem == null) return;
        for (BarrierSession session : new ArrayList<>(byItem.values())) {
            if (session == null) continue;
            session.dispose(false);
            if (moment != null && session.getConfig().resetOn().contains(moment)) {
                forgetPersistedHp(uuid, session.getItemKey());
            }
        }
    }

    public static void disposeAll() {
        for (Map<String, BarrierSession> byItem : new ArrayList<>(SESSIONS.values())) {
            for (BarrierSession session : new ArrayList<>(byItem.values())) {
                if (session != null) {
                    session.dispose(false);
                }
            }
        }
        PERSISTED_HP.clear();
    }

    static void onDisposed(BarrierSession session, boolean shatter) {
        if (session == null) return;
        if (!shatter) {
            if (session.getConfig().persistHpAcrossSummons()) {
                persistHp(session);
            }
            if (session.getConfig().cooldown().on() == BarrierConfig.CooldownConfig.CooldownMoment.DISPOSE) {
                markShatterCooldown(session.actorId(), session.getItemKey(),
                        System.currentTimeMillis(), session.getConfig().cooldown().seconds());
            }
        }
        removeSession(session);
    }

    static void onShattered(BarrierSession session) {
        if (session == null || session.isDisposed()) return;
        if (session.getConfig().cooldown().on() == BarrierConfig.CooldownConfig.CooldownMoment.SHATTER) {
            markShatterCooldown(session.actorId(), session.getItemKey(),
                    System.currentTimeMillis(), session.getConfig().cooldown().seconds());
        }
        if (session.getConfig().resetOn().contains(BarrierConfig.ResetOn.SHATTER)) {
            forgetPersistedHp(session.actorId(), session.getItemKey());
        } else if (session.getConfig().persistHpAcrossSummons()) {
            persistHp(session);
        }
        session.dispose(true);
    }

    public static void markShatterCooldown(UUID actor, String itemKey, long nowMillis, double seconds) {
        CooldownTracker.markCooldown(actor, itemKey, (long) (nowMillis + seconds * 1000));
    }

    public static void resetPersistedIfResetOn(Player caster, String itemKey,
                                               BarrierConfig.ResetOn moment, BarrierConfig config) {
        if (config != null && caster != null && config.resetOn().contains(moment)) {
            forgetPersistedHp(caster.getUniqueId(), itemKey);
        }
    }

    private static void removeSession(BarrierSession session) {
        Map<String, BarrierSession> byItem = SESSIONS.get(session.actorId());
        if (byItem != null) {
            byItem.remove(session.getItemKey());
            if (byItem.isEmpty()) {
                SESSIONS.remove(session.actorId(), byItem);
            }
        }
    }

    private static void persistHp(BarrierSession session) {
        PERSISTED_HP.computeIfAbsent(session.actorId(), k -> new ConcurrentHashMap<>())
                .put(session.getItemKey(), session.hitpoints());
    }

    private static void forgetPersistedHp(UUID actor, String itemKey) {
        Map<String, Double> byItem = PERSISTED_HP.get(actor);
        if (byItem != null) {
            byItem.remove(itemKey);
            if (byItem.isEmpty()) {
                PERSISTED_HP.remove(actor, byItem);
            }
        }
    }

    private static double persistedHpOf(UUID actor, String itemKey, double fallback) {
        Map<String, Double> byItem = PERSISTED_HP.get(actor);
        if (byItem != null) {
            Double value = byItem.get(itemKey);
            if (value != null) return value;
        }
        return fallback;
    }

    private static void playDenySound(Player caster, BarrierConfig config) {
        String denySound = config.cooldown().denySound();
        if (denySound == null || denySound.isBlank()) return;
        try {
            caster.playSound(caster.getLocation(), Sound.valueOf(denySound.trim().toUpperCase()), 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
