package me.nagasonic.alkatraz.hooks;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Compile-time hook into the WorldGuard v7 region flag API.
 *
 * <p>Every guard method returns {@code true} (allowed) when WorldGuard is absent,
 * so the plugin degrades gracefully without it. All flag checks are performed at
 * the location of the action, honouring WorldGuard's native bypass and the
 * {@link Flags} region state for the acting subject.</p>
 */
public final class WorldGuardHook extends PluginHook {

    private static final class Holder {
        private static final WorldGuardHook INSTANCE = new WorldGuardHook();
    }

    public static WorldGuardHook getInstance() {
        return Holder.INSTANCE;
    }

    private WorldGuardHook() {
        super("WorldGuard");
    }

    @Override
    public void ifPresent() {
        if (present) {
            Alkatraz.logInfo("WorldGuard hook registered successfully!");
        }
    }

    private static Player asPlayer(LivingEntity doer) {
        return (doer instanceof Player) ? (Player) doer : null;
    }

    private boolean allows(Location loc, Player subject, StateFlag flag) {
        if (!present) {
            return true;
        }
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        LocalPlayer wgPlayer = subject == null ? null : WorldGuardPlugin.inst().wrapPlayer(subject);
        return query.testState(BukkitAdapter.adapt(loc), wgPlayer, flag);
    }

    public boolean canBuild(LivingEntity doer, Location loc) {
        if (!present) {
            return true;
        }
        return allows(loc, asPlayer(doer), Flags.BUILD);
    }

    public boolean canDamage(LivingEntity victim, LivingEntity doer, Location loc) {
        if (!present) {
            return true;
        }
        StateFlag flag = (victim instanceof Player) ? Flags.PVP : Flags.MOB_DAMAGE;
        return allows(loc, asPlayer(doer), flag);
    }

    public boolean canExplode(LivingEntity doer, Location loc) {
        if (!present) {
            return true;
        }
        return allows(loc, asPlayer(doer), Flags.OTHER_EXPLOSION);
    }

    public boolean canTeleport(Player player, Location to) {
        if (!present) {
            return true;
        }
        return allows(to, player, Flags.ENTRY);
    }

    public boolean canSpawnMob(LivingEntity doer, Location loc) {
        if (!present) {
            return true;
        }
        return allows(loc, asPlayer(doer), Flags.MOB_SPAWNING);
    }

    public boolean canIgnite(LivingEntity doer, Location loc) {
        if (!present) {
            return true;
        }
        return allows(loc, asPlayer(doer), Flags.FIRE_SPREAD) && allows(loc, asPlayer(doer), Flags.LAVA_FIRE);
    }
}
