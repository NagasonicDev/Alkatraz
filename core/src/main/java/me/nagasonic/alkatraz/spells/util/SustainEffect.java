package me.nagasonic.alkatraz.spells.util;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The toggle lifecycle shared by the five circle-9 sustains.
 *
 * <p>One active instance per caster, owned by the {@link Spell} that opened it. A toggle from
 * the owning spell closes it, and every close path runs the teardown exactly once - so a
 * spell never has to reason about which of several events fired. A toggle from a
 * <em>different</em> sustain is rejected without touching the running one, since all five
 * circle-9 sustains share the single slot.
 *
 * <p>Shortcut: upkeep is charged inside the same 1-tick task as the body callback, every
 * 20th tick, rather than as a second scheduled task. Cheap at five sustains; if a future
 * spell needs upkeep on a different clock, split it then.
 */
public final class SustainEffect {

    @FunctionalInterface
    public interface Body {
        /**
         * Runs every tick while the sustain is live. Return false to end it.
         *
         * @param elapsedTicks ticks since the sustain opened, starting at 0
         */
        boolean tick(Player caster, int elapsedTicks);
    }

    @FunctionalInterface
    public interface Teardown {
        /**
         * Runs exactly once, on any end path.
         *
         * <p>{@code caster} is null when the player quit and is already off the online list.
         * Clean up anything keyed by UUID regardless; null-guard only the work that needs a live
         * player (boss bars, particles, velocity). Skipping the teardown entirely on quit
         * leaks profile stat modifiers across a relog.
         */
        void run(Player caster);
    }

    /** Outcome of a {@link #toggle} call. */
    public enum Toggle {
        /** The sustain started. */
        OPENED,
        /** The caller owned the running sustain and this call ended it. */
        CLOSED,
        /** Nothing happened - a null caster, or another spell already owns the slot. */
        BLOCKED
    }

    private static final class Active {
        private final Spell owner;
        private final Teardown teardown;
        private final Body body;
        private final World world;
        private int taskId = -1;
        private int elapsedTicks;

        private Active(Spell owner, Teardown teardown, Body body, World world) {
            this.owner = owner;
            this.teardown = teardown;
            this.body = body;
            this.world = world;
        }
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static boolean installed;

    private SustainEffect() {
    }

    /** Registers the plugin-disable hook. Called once from Alkatraz#onEnable. */
    public static void install() {
        if (installed) return;
        installed = true;
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPluginDisable(PluginDisableEvent event) {
                if (event.getPlugin() != Alkatraz.getInstance()) return;
                shutdown();
            }
        }, Alkatraz.getInstance());
    }

    public static boolean isActive(UUID id) {
        return id != null && ACTIVE.containsKey(id);
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    public static Toggle toggle(Player p, double upkeepPerSecond, Spell owner, Body body, Teardown teardown) {
        if (p == null) return Toggle.BLOCKED;
        UUID id = p.getUniqueId();
        Active existing = ACTIVE.get(id);
        if (existing != null) {
            // All five sustains share the one slot. A foreign owner gets nothing - no stop, no
            // refund, no teardown - rather than silently cancelling the running one. Identity,
            // not getId(): a reloaded spell instance is a different owner.
            if (existing.owner != owner) return Toggle.BLOCKED;
            stop(id);
            return Toggle.CLOSED;
        }
        Active active = new Active(owner, teardown, body, p.getWorld());
        ACTIVE.put(id, active);
        active.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                Alkatraz.getInstance(), () -> tick(p, id, upkeepPerSecond), 0L, 1L);
        return Toggle.OPENED;
    }

    public static void stop(Player p) {
        if (p != null) stop(p.getUniqueId());
    }

    /** Idempotent. Safe before any toggle, safe twice, safe from any lifecycle hook. */
    public static void stop(UUID id) {
        if (id == null) return;
        Active active = ACTIVE.remove(id);
        if (active == null) return;
        if (active.taskId != -1) Bukkit.getScheduler().cancelTask(active.taskId);
        // Null when the player is already off the online list. The teardown still runs - see
        // its javadoc - so UUID-keyed state is never left behind across a relog.
        active.teardown.run(Bukkit.getPlayer(id));
    }

    /** Ends every live sustain. Used by plugin disable and reload. */
    public static void shutdown() {
        for (UUID id : Map.copyOf(ACTIVE).keySet()) {
            try {
                stop(id);
            } catch (Throwable ignored) {
                // One broken teardown must not strand the other players' effects; Bukkit cancels
                // the tasks either way, it does not undo a leaked stat modifier or boss bar.
            }
        }
        // The listener is dead once this plugin is disabled, so a re-enable in the same
        // classloader must be able to install it again.
        installed = false;
    }

    private static void tick(Player p, UUID id, double upkeepPerSecond) {
        Active active = ACTIVE.get(id);
        if (active == null) return;
        if (p == null || !p.isOnline()) {
            stop(id);
            return;
        }
        if (p.isDead() || p.getWorld() == null || p.getWorld() != active.world) {
            stop(id);
            return;
        }
        if (upkeepPerSecond > 0 && active.elapsedTicks % 20 == 0) {
            MagicProfile profile = ProfileManager.getProfile(id, MagicProfile.class);
            if (profile == null) {
                stop(id);
                return;
            }
            if (profile.getMana() < upkeepPerSecond) {
                Utils.sendActionBar(p, Alkatraz.getLangManager().get("spells.sustain.out_of_mana"));
                stop(id);
                return;
            }
            StatUtils.subMana(p, upkeepPerSecond);
        }
        boolean keepGoing = false;
        try {
            keepGoing = active.body.tick(p, active.elapsedTicks);
            active.elapsedTicks++;
        } catch (Throwable t) {
            Alkatraz.logSevere("Sustain effect failed for " + id + ": " + t.getMessage());
            keepGoing = false;
        }
        if (!keepGoing) stop(id);
    }
}