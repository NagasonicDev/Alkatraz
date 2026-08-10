package me.nagasonic.alkatraz.hooks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Reflection bridge to WorldGuard's region API (v7). Lets engraving triggers
 * detect region entry/exit without a compile-time dependency on WorldGuard.
 */
public final class WorldGuardBridge {

    private static final String WORLDGUARD = "WorldGuard";
    private static WorldGuardBridge instance;

    private final boolean present;
    private Object worldGuard;
    private Object platform;
    private Method platformGetRegionContainer;
    private Method containerCreateQuery;
    private Method queryGetApplicableRegions;
    private Method resultGetRegions;
    private Method regionGetId;
    private Method bukkitAdapterAdapt;

    private WorldGuardBridge() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(WORLDGUARD);
        if (plugin == null) {
            present = false;
            return;
        }
        present = resolve(plugin);
    }

    public static WorldGuardBridge getInstance() {
        if (instance == null) {
            instance = new WorldGuardBridge();
        }
        return instance;
    }

    public boolean isPresent() {
        return present;
    }

    private boolean resolve(Plugin plugin) {
        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Method getInstance = worldGuardClass.getMethod("getInstance");
            worldGuard = getInstance.invoke(null);

            Method getPlatform = worldGuardClass.getMethod("getPlatform");
            platform = getPlatform.invoke(worldGuard);

            platformGetRegionContainer = platform.getClass().getMethod("getRegionContainer");
            containerCreateQuery = platformGetRegionContainer.getReturnType().getMethod("createQuery");
            queryGetApplicableRegions = containerCreateQuery.getReturnType().getMethod("getApplicableRegions", Class.forName("com.sk89q.worldedit.util.Location"));
            resultGetRegions = queryGetApplicableRegions.getReturnType().getMethod("getRegions");
            regionGetId = resultGetRegions.getReturnType().getMethod("getId");

            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            bukkitAdapterAdapt = bukkitAdapterClass.getMethod("adapt", Location.class);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /**
     * Returns the ids of all WorldGuard regions containing the given location,
     * or an empty set if WorldGuard is not present.
     */
    public Set<String> regionsAt(Location location) {
        if (!present) {
            return Collections.emptySet();
        }
        try {
            Object container = platformGetRegionContainer.invoke(platform);
            Object query = containerCreateQuery.invoke(container);
            Object weLocation = bukkitAdapterAdapt.invoke(null, location);
            Object result = queryGetApplicableRegions.invoke(query, weLocation);
            Object regions = resultGetRegions.invoke(result);
            Set<String> ids = new HashSet<>();
            for (Object region : (Set<?>) regions) {
                ids.add((String) regionGetId.invoke(region));
            }
            return ids;
        } catch (ReflectiveOperationException e) {
            return Collections.emptySet();
        }
    }
}
