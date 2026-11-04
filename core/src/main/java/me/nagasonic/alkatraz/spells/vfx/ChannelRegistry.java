package me.nagasonic.alkatraz.spells.vfx;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import me.nagasonic.alkatraz.Alkatraz;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One shared registry of in-flight wind-ups, replacing the copy-pasted
 * {@code private static final Map<UUID, ChannelData> channels} plus
 * {@code onMove}/{@code onDamage}/{@code onQuit} handler triples that were
 * duplicated across {@code Warp}, {@code SkeletalKnights} and every circle 6+
 * spell written since.
 *
 * <h2>Interruption</h2>
 * A wind-up is interrupted by the established triple: the caster moving beyond
 * {@code move_cancel_threshold} blocks (default
 * {@value WindUp#DEFAULT_MOVE_CANCEL_THRESHOLD}, read from the spell's yml when it
 * declares one), the caster taking damage, or the caster quitting. Plugin
 * shutdown interrupts everything too.
 *
 * <h2>Mana is NOT refunded</h2>
 * <b>The rule to remember:</b> {@code Spell.cast()} deducts mana at
 * {@code Spell.java:187}, <i>before</i> the cast-time delay, so an interrupted
 * wind-up has already paid. Interrupting does not refund mana, does not grant
 * cooldown, and does not grant mastery. This is exactly what every hand-rolled
 * {@code abortChannel} already did - the contract makes it uniform rather than
 * introducing a new rule. Do not add a refund here. The one genuine refund path
 * is {@code Spell.cancelCast(Player)}, called from inside {@code castAction}
 * when a cast cannot proceed at all, which is a different moment: before the
 * payload, not during the wind-up.
 *
 * <h2>Task ownership</h2>
 * The registry never owns the wind-up's repeating task - the spell does. Callers
 * hand in a {@link Runnable} that cancels their own task, which is what
 * {@link #shutdown()} invokes for every channel. Keeping that behind a
 * {@code Runnable} rather than a raw task id means the bookkeeping is testable
 * with no live scheduler.
 *
 * <p>Registered once from {@code Alkatraz.onEnable} via
 * {@link #INSTANCE}; {@link #shutdown()} is called from {@code onDisable}.
 */
public final class ChannelRegistry implements Listener {

    private static final String INTERRUPTED_LANG_KEY = "spells.cast.interrupted";

    /** The single shared instance, registered as a Bukkit listener in {@code onEnable}. */
    public static final ChannelRegistry INSTANCE = new ChannelRegistry();

    private static final Map<UUID, WindUp> CHANNELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Runnable> CANCELLERS = new ConcurrentHashMap<>();

    private ChannelRegistry() {
    }

    // ================================================================
    // Public API
    // ================================================================

    /** Registers a wind-up with no task canceller - the owner must stop its own task. */
    public static void register(UUID caster, WindUp windUp) {
        register(caster, windUp, null);
    }

    /**
     * Registers an in-flight wind-up for a caster, replacing any wind-up they
     * already had. One caster holds at most one wind-up at a time.
     *
     * @param caster    the channelling entity's id
     * @param windUp    the wind-up in flight
     * @param canceller cancels the caller's own repeating task, or null if the
     *                  caller does not own one
     */
    public static void register(UUID caster, WindUp windUp, Runnable canceller) {
        if (caster == null || windUp == null) return;
        // Replacing a channel on the same caster must stop the previous wind-up task.
        Runnable previousCanceller = CANCELLERS.get(caster);
        if (previousCanceller != null && previousCanceller != canceller) {
            try { previousCanceller.run(); } catch (RuntimeException ex) {
                Alkatraz.logWarning("Channel canceller failed: " + ex.getMessage());
            }
        }
        CHANNELS.put(caster, windUp);
        if (canceller != null) {
            CANCELLERS.put(caster, canceller);
        } else {
            CANCELLERS.remove(caster);
        }
    }

    /**
     * Forgets a caster's wind-up <b>without</b> running its canceller. Use this when
     * the wind-up finished on its own and the caller has already stopped its task;
     * use {@link #cancelAll} when the wind-up is being interrupted and the task
     * still needs stopping.
     */
    public static void unregister(UUID caster) {
        if (caster == null) return;
        CHANNELS.remove(caster);
        CANCELLERS.remove(caster);
    }

    /** Whether this caster currently has a wind-up in flight. */
    public static boolean isChanneling(UUID caster) {
        return caster != null && CHANNELS.containsKey(caster);
    }

    /** How many wind-ups are in flight across the server. */
    public static int activeChannels() {
        return CHANNELS.size();
    }

    /** The wind-up a caster currently holds, or null. Does not remove it. */
    public static WindUp windUp(UUID caster) {
        return caster == null ? null : CHANNELS.get(caster);
    }

    /**
     * Interrupts every in-flight wind-up, telling each caster why and cancelling
     * each caller's task. Called from {@code onDisable} via {@link #shutdown()},
     * and safe to call when nothing is registered.
     */
    public static void cancelAll(String langKey) {
        for (UUID caster : new java.util.ArrayList<>(CHANNELS.keySet())) {
            WindUp windUp = CHANNELS.remove(caster);
            Runnable canceller = CANCELLERS.remove(caster);
            if (canceller != null) {
                try {
                    canceller.run();
                } catch (RuntimeException ex) {
                    Alkatraz.logWarning("Channel canceller failed: " + ex.getMessage());
                    // A broken canceller must not stop the rest of the shutdown.
                }
            }
            if (windUp != null) {
                windUp.cancel(langKey);
            }
        }
        CANCELLERS.clear();
    }

    /**
     * Cancels every task and empties the registry. Called from
     * {@code Alkatraz.onDisable}; idempotent, and does not message anybody.
     */
    public static void shutdown() {
        cancelAll(INTERRUPTED_LANG_KEY);
    }

    // ================================================================
    // Interruption - the established triple
    // ================================================================

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        UUID caster = e.getPlayer().getUniqueId();
        WindUp windUp = CHANNELS.get(caster);
        if (windUp == null) return;
        Location to = e.getTo();
        if (to == null) return;
        Location from = e.getFrom();
        if (!from.getWorld().equals(to.getWorld())
                || from.distance(to) > WindUp.moveCancelThreshold(windUp.spell())) {
            interrupt(caster, windUp);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        WindUp windUp = CHANNELS.get(victim.getUniqueId());
        if (windUp == null) return;
        interrupt(victim.getUniqueId(), windUp);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID caster = e.getPlayer().getUniqueId();
        WindUp windUp = CHANNELS.remove(caster);
        Runnable canceller = CANCELLERS.remove(caster);
        if (canceller != null) {
            try {
                canceller.run();
            } catch (RuntimeException ex) {
                Alkatraz.logWarning("Channel canceller failed: " + ex.getMessage());
                // Nothing to salvage on a quit.
            }
        }
        if (windUp != null) {
            windUp.cancel(INTERRUPTED_LANG_KEY);
        }
    }

    private static void interrupt(UUID caster, WindUp windUp) {
        Runnable canceller = CANCELLERS.remove(caster);
        CHANNELS.remove(caster);
        if (canceller != null) {
            try {
                canceller.run();
            } catch (RuntimeException ex) {
                Alkatraz.logWarning("Channel canceller failed: " + ex.getMessage());
            }
        }
        windUp.cancel(INTERRUPTED_LANG_KEY);
    }
}