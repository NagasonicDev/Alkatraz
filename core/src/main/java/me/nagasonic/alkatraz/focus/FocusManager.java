package me.nagasonic.alkatraz.focus;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.FocusConfig;
import me.nagasonic.alkatraz.events.PlayerFocusChangeEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.WandUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the Focus resource: exponential stationary regeneration, a
 * velocity-scaled movement penalty (non-recursive, instantly restored when the
 * player stops), and damage-based focus loss. Every focus change funnels
 * through {@link #setFocus(Player, MagicProfile, double)} so the mid-cast
 * cancel latch and {@link PlayerFocusChangeEvent} always fire.
 */
public final class FocusManager implements Listener {

    private static final long REGEN_INTERVAL_TICKS = 20L;
    private static final long STATIONARY_THRESHOLD_MS = 500L;
    private static final double STATIONARY_SPEED_EPSILON = 1e-6;

    private final Map<UUID, Long> lastMoveTime = new HashMap<>();
    private final Map<UUID, Double> movementPenalty = new HashMap<>();
    private final FocusBossBar bossBar = new FocusBossBar();

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(Alkatraz.getInstance(), REGEN_INTERVAL_TICKS, REGEN_INTERVAL_TICKS);
    }

    public void shutdown() {
        bossBar.hideAll();
        lastMoveTime.clear();
        movementPenalty.clear();
    }

    // ============================
    // Regeneration tick (every second)
    // ============================

    private void tick() {
        if (!FocusConfig.isEnabled()) return;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
            if (profile == null) continue;
            UUID uuid = player.getUniqueId();

            Long lastMove = lastMoveTime.get(uuid);
            if (lastMove != null && now - lastMove > STATIONARY_THRESHOLD_MS) {
                movementPenalty.put(uuid, 0.0);
            }

            double effectiveMax = effectiveMax(profile, uuid);
            if (!profile.isCasting() && profile.getFocus() < effectiveMax) {
                double t = lastMove == null ? 0 : Math.min((now - lastMove) / 1000.0, 60.0);
                double rate = Math.min(
                        FocusConfig.getRegenBase() * Math.pow(FocusConfig.getRegenGrowthFactor(), t),
                        FocusConfig.getRegenMax());
                if (player.getHealth() < FocusConfig.getLowHealthThreshold()) {
                    rate *= FocusConfig.getLowHealthMultiplier();
                }
                setFocus(player, profile, Math.min(profile.getFocus() + rate, effectiveMax));
            }
            refreshBar(player, profile);
        }
    }

    private double effectiveMax(MagicProfile profile, UUID uuid) {
        return Math.max(0.0, profile.getMaxFocus() - movementPenalty.getOrDefault(uuid, 0.0));
    }

    // ============================
    // Single choke point for focus changes
    // ============================

    /**
     * Sets the player's focus to {@code newValue} (clamped by the profile),
     * fires {@link PlayerFocusChangeEvent}, refreshes the bossbar, and cancels
     * any in-progress cast whose required focus is no longer met.
     */
    public void setFocus(Player player, MagicProfile profile, double newValue) {
        double oldFocus = profile.getFocus();
        profile.setFocus(newValue);
        double newFocus = profile.getFocus();
        if (Double.compare(oldFocus, newFocus) != 0) {
            Spell castingSpell = profile.getCastingSpell();
            if (newFocus < oldFocus && profile.isCasting() && castingSpell != null
                    && newFocus < castingSpell.getRequiredFocus()) {
                castingSpell.cancelCastByFocus(player);
            }
            Bukkit.getPluginManager().callEvent(new PlayerFocusChangeEvent(player, oldFocus, newFocus));
        }
        refreshBar(player, profile);
    }

    private void refreshBar(Player player, MagicProfile profile) {
        if (!bossBar.isVisible(player)) return;
        bossBar.update(player, profile.getFocus(), effectiveMax(profile, player.getUniqueId()));
    }

    // ============================
    // Movement penalty (non-recursive, instant restore)
    // ============================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!FocusConfig.isEnabled()) return;
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || sameBlock(event.getFrom(), to)) return;

        UUID uuid = player.getUniqueId();
        MagicProfile profile = ProfileManager.getProfile(uuid, MagicProfile.class);
        if (profile == null) return;

        lastMoveTime.put(uuid, System.currentTimeMillis());

        org.bukkit.util.Vector velocity = player.getVelocity();
        double speedBps = Math.hypot(velocity.getX(), velocity.getZ()) / 0.05;
        double penaltyRatio = Math.min(
                speedBps * FocusConfig.getMovementPenaltyPerSpeed(),
                FocusConfig.getMovementMaxPenaltyRatio());
        double penalty = speedBps <= STATIONARY_SPEED_EPSILON
                ? 0.0
                : profile.getMaxFocus() * penaltyRatio;
        movementPenalty.put(uuid, penalty);

        double effectiveMax = effectiveMax(profile, uuid);
        if (profile.getFocus() > effectiveMax) {
            setFocus(player, profile, effectiveMax);
        }
    }

    // ============================
    // Damage-based focus loss
    // ============================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!FocusConfig.isEnabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        double damage = event.getFinalDamage();
        if (damage <= 0) return;
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        if (profile == null) return;
        double loss = profile.getMaxFocus() * FocusConfig.getDamageFactor() * (damage / player.getMaxHealth());
        if (loss <= 0) return;
        setFocus(player, profile, Math.max(0.0, profile.getFocus() - loss));
    }

    // ============================
    // Bossbar show / hide
    // ============================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (isCastingTool(player.getInventory().getItem(event.getNewSlot()))) {
            bossBar.show(player);
            MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
            if (profile != null) refreshBar(player, profile);
        } else {
            bossBar.hide(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (isCastingTool(player.getInventory().getItem(player.getInventory().getHeldItemSlot()))) {
            bossBar.show(player);
            MagicProfile profile = ProfileManager.getProfile(uuid, MagicProfile.class);
            if (profile != null) refreshBar(player, profile);
        }
        lastMoveTime.putIfAbsent(uuid, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        bossBar.hide(event.getPlayer());
        lastMoveTime.remove(uuid);
        movementPenalty.remove(uuid);
    }

    private static boolean isCastingTool(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() == 0) return false;
        return WandUtils.isWand(item) || WandUtils.isGrimoire(item);
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld() == b.getWorld()
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}