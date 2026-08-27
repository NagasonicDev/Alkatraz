package me.nagasonic.alkatraz.hooks;

import me.nagasonic.alkatraz.api.dom.Permission;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Thin facade over {@link WorldGuardHook} that spells and engraving effects
 * call before performing world-affecting actions. Every method returns true
 * when the action is allowed (WorldGuard absent, bypass granted, or region
 * allows it) and false when the region denies the action.
 */
public final class Protection {

    private static WorldGuardHook hook;

    private Protection() {}

    private static WorldGuardHook hook() {
        if (hook == null) {
            hook = WorldGuardHook.getInstance();
        }
        return hook;
    }

    /** Pure decision: Alkatraz bypass overrides the region result. */
    public static boolean decide(boolean hasBypass, boolean regionAllows) {
        return hasBypass || regionAllows;
    }

    private static boolean hasBypass(LivingEntity doer) {
        return doer instanceof Player player && Permission.hasPermission(player, Permission.REGION_BYPASS);
    }

    /** May the doer place/break blocks at the location? */
    public static boolean blockEdit(LivingEntity doer, Location loc) {
        return decide(hasBypass(doer), hook().canBuild(doer, loc));
    }

    /** May the doer damage the victim at the location? */
    public static boolean damage(LivingEntity victim, LivingEntity doer, Location loc) {
        return decide(hasBypass(doer), hook().canDamage(victim, doer, loc));
    }

    /** May the doer create an explosion at the location? */
    public static boolean explosion(LivingEntity doer, Location loc) {
        return decide(hasBypass(doer), hook().canExplode(doer, loc));
    }

    /** May the player teleport to the location? */
    public static boolean teleport(Player player, Location loc) {
        return decide(hasBypass(player), hook().canTeleport(player, loc));
    }

    /** May the doer spawn a mob at the location? */
    public static boolean spawnMob(LivingEntity doer, Location loc) {
        return decide(hasBypass(doer), hook().canSpawnMob(doer, loc));
    }

    /** May the doer place/ignite fire at the location? */
    public static boolean ignite(LivingEntity doer, Location loc) {
        return decide(hasBypass(doer), hook().canIgnite(doer, loc));
    }
}
