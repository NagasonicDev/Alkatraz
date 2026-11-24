package me.nagasonic.alkatraz.spells.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-caster summon cap shared by retinues, knights and any other summon. The only
 * place that despawns owned entities, so a summon cannot be orphaned: every summon
 * is tagged, tracked per owner, and removed on owner quit/death/world change and on
 * plugin disable.
 */
public final class SummonLimits implements Listener {

    public static final SummonLimits INSTANCE = new SummonLimits();

    /** Scoreboard tag stamped on every managed summon, so shutdown can sweep leftovers. */
    public static final String TAG = "alkatraz_summon";

    private static final Map<UUID, List<UUID>> OWNED = new ConcurrentHashMap<>();

    private SummonLimits() {
    }

    /**
     * Registers {@code summon} to {@code owner} if the owner is under {@code cap}.
     * Returns false when the cap is already reached (the caller must then not spawn).
     */
    public static boolean register(LivingEntity owner, LivingEntity summon, int cap) {
        if (owner == null || summon == null) return false;
        List<UUID> list = OWNED.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>());
        prune(list);
        if (list.size() >= Math.max(0, cap)) return false;
        summon.addScoreboardTag(TAG);
        list.add(summon.getUniqueId());
        return true;
    }

    /** Live summons currently owned by {@code owner}. */
    public static int count(LivingEntity owner) {
        if (owner == null) return 0;
        List<UUID> list = OWNED.get(owner.getUniqueId());
        if (list == null) return 0;
        prune(list);
        return list.size();
    }

    /** Removes every summon owned by {@code owner}. Safe to call when it owns none. */
    public static void despawn(LivingEntity owner) {
        if (owner == null) return;
        List<UUID> list = OWNED.remove(owner.getUniqueId());
        if (list == null) return;
        for (UUID id : list) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
    }

    /** Despawns everything, including any tagged leftover whose owner data was lost. */
    public static void shutdown() {
        for (UUID owner : new ArrayList<>(OWNED.keySet())) {
            List<UUID> list = OWNED.remove(owner);
            if (list == null) continue;
            for (UUID id : list) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) entity.remove();
            }
        }
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(TAG)) entity.remove();
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        despawn(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        despawn(event.getEntity());
    }

    /** Mob owners (e.g. WraithboundRetinue) have no PlayerDeathEvent. */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        despawn(event.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        despawn(event.getPlayer());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        for (UUID owner : new ArrayList<>(OWNED.keySet())) {
            List<UUID> list = OWNED.get(owner);
            if (list == null) continue;
            for (UUID id : list) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.getWorld().equals(event.getWorld())) entity.remove();
            }
            prune(list);
        }
    }

    private static void prune(List<UUID> list) {
        list.removeIf(id -> {
            Entity entity = Bukkit.getEntity(id);
            return entity == null || entity.isDead() || !entity.isValid();
        });
    }
}
