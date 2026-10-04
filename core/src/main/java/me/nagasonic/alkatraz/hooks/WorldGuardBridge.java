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
    private volatile Object platform;
    private volatile Method platformGetRegionContainer;
    private volatile Method containerCreateQuery;
    private volatile Method queryGetApplicableRegions;
    private volatile Method resultGetRegions;
    private volatile Method regionGetId;
    private volatile Method bukkitAdapterAdapt;

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
            Object worldGuard = getInstance.invoke(null);

            Method getPlatform = worldGuardClass.getMethod("getPlatform");
            Object resolvedPlatform = getPlatform.invoke(worldGuard);
            this.platform = resolvedPlatform;

            platformGetRegionContainer = resolvedPlatform.getClass().getMethod("getRegionContainer");
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

    public Set<String> regionsAt(Location location) {
        if (!present) {
            return Collections.emptySet();
        }
        Method getRegionContainer = platformGetRegionContainer;
        Method createQuery = containerCreateQuery;
        Method getApplicableRegions = queryGetApplicableRegions;
        Method getRegions = resultGetRegions;
        Method getId = regionGetId;
        Method adapt = bukkitAdapterAdapt;
        if (platform == null || getRegionContainer == null || createQuery == null || getApplicableRegions == null
                || getRegions == null || getId == null || adapt == null) {
            return Collections.emptySet();
        }
        try {
            Object container = getRegionContainer.invoke(platform);
            Object query = createQuery.invoke(container);
            Object weLocation = adapt.invoke(null, location);
            Object result = getApplicableRegions.invoke(query, weLocation);
            Set<?> regions = (Set<?>) getRegions.invoke(result);
            if (regions.isEmpty()) {
                return Collections.emptySet();
            }
            Set<String> ids = new HashSet<>(Math.max(2, regions.size() << 1));
            for (Object region : regions) {
                ids.add((String) getId.invoke(region));
            }
            return ids;
        } catch (ReflectiveOperationException e) {
            return Collections.emptySet();
        }
    }
}
