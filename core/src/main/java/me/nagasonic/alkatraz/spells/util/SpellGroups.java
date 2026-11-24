package me.nagasonic.alkatraz.spells.util;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Channeled group movement (foldstep, wayfarers_call, empyrean_passage). Tracks
 * which members a leader is currently carrying so the spells can gate movement,
 * interrupt the ride when a member takes damage, and release everyone on quit,
 * death, world change or plugin disable.
 *
 * <p>Membership only - the movement itself belongs to the spell.
 */
public final class SpellGroups implements Listener {

    public static final SpellGroups INSTANCE = new SpellGroups();

    /** One ride; identity is the token used to tell two casters of the same member apart. */
    private record Ride(UUID leader) {
    }

    private static final Map<UUID, Ride> CARRIED = new ConcurrentHashMap<>();

    private SpellGroups() {
    }

    /** Marks {@code member} as carried by {@code leader} and returns this ride's token. */
    public static Object carry(LivingEntity leader, LivingEntity member) {
        if (leader == null || member == null) return null;
        UUID leaderId = leader.getUniqueId();
        if (leaderId.equals(member.getUniqueId())) return null;
        Ride ride = new Ride(leaderId);
        CARRIED.put(member.getUniqueId(), ride);
        return ride;
    }

    /** Releases everyone carried by {@code leader}. */
    public static void release(LivingEntity leader) {
        if (leader == null) return;
        UUID leaderId = leader.getUniqueId();
        CARRIED.entrySet().removeIf(entry -> entry.getValue().leader().equals(leaderId));
    }

    /** Releases a single member from any ride. */
    public static void releaseMember(LivingEntity member) {
        if (member == null) return;
        CARRIED.remove(member.getUniqueId());
    }

    /** Releases a single member only if the token still belongs to that ride. */
    public static void releaseMember(LivingEntity member, Object token) {
        if (member == null || token == null) return;
        CARRIED.computeIfPresent(member.getUniqueId(), (id, ride) -> ride == token ? null : ride);
    }

    public static boolean isCarried(LivingEntity member) {
        return member != null && CARRIED.containsKey(member.getUniqueId());
    }

    public static boolean isCarriedBy(LivingEntity member, Object token) {
        return member != null && token != null && CARRIED.get(member.getUniqueId()) == token;
    }

    /** The leader carrying {@code member}, or null. */
    public static UUID leaderOf(LivingEntity member) {
        Ride ride = member == null ? null : CARRIED.get(member.getUniqueId());
        return ride == null ? null : ride.leader();
    }

    public static void shutdown() {
        CARRIED.clear();
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity victim) releaseMember(victim);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        releaseMember(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        releaseMember(event.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        releaseMember(event.getPlayer());
    }
}
