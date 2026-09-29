package me.nagasonic.alkatraz.mobs.goals;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.api.mobs.Goal;
import me.nagasonic.alkatraz.api.mobs.GoalFlag;
import me.nagasonic.alkatraz.api.mobs.MobBrainContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Follows whatever entity is recorded in a configurable persistent-NBT UUID key
 * (default {@code summoner_uuid}), tame-pet style: path toward it when beyond
 * follow-range, teleport to a nearby spot beside it when beyond teleport-range
 * or in another world, and never treat the followed entity as an attack target.
 *
 * <p>The goal is stateless per mob: every evaluation re-resolves the followed
 * entity from NBT, so a shared cached {@code GoalBrain} instance is safe to
 * apply to many mobs at once.
 */
public class FollowEntityGoal implements Goal {

    private static final double TELEPORT_OFFSET = 1.5;
    private static final double CROSS_WORLD_OFFSET_X = 1.0;
    private static final double CROSS_WORLD_OFFSET_Z = 1.0;
    private static final int UUID_CACHE_LIMIT = 4096;
    private static final Map<String, UUID> UUID_CACHE = new ConcurrentHashMap<>();

    private final FollowEntityConfig config;

    public FollowEntityGoal(FollowEntityConfig config) {
        this.config = config;
    }

    @Override
    public EnumSet<GoalFlag> flags() {
        return EnumSet.of(GoalFlag.MOVE);
    }

    @Override
    public boolean canStart(MobBrainContext ctx) {
        LivingEntity followed = resolveFollowed(ctx);
        if (followed == null) return false;
        if (config.clearTargetWhenFollowed() && ctx.getTarget() == followed) return true;
        return isFar(ctx, followed);
    }

    @Override
    public boolean shouldContinue(MobBrainContext ctx) {
        return canStart(ctx);
    }

    @Override
    public void stop(MobBrainContext ctx) {
        ctx.stopNavigating();
    }

    @Override
    public void tick(MobBrainContext ctx) {
        LivingEntity followed = resolveFollowed(ctx);
        if (followed == null) return;
        if (config.clearTargetWhenFollowed() && ctx.getTarget() == followed) {
            ctx.setTarget(null);
        }
        if (shouldTeleport(ctx, followed)) {
            ctx.self().teleport(teleportTarget(ctx, followed));
            ctx.stopNavigating();
            return;
        }
        if (isFar(ctx, followed)) {
            ctx.moveTo(followed.getLocation(), config.speed());
        }
    }

    private boolean isFar(MobBrainContext ctx, LivingEntity followed) {
        if (!sameWorld(ctx, followed)) return true;
        return ctx.self().getLocation().distanceSquared(followed.getLocation())
                > config.followRange() * config.followRange();
    }

    private boolean shouldTeleport(MobBrainContext ctx, LivingEntity followed) {
        if (!sameWorld(ctx, followed)) return true;
        return ctx.self().getLocation().distanceSquared(followed.getLocation())
                > config.teleportRange() * config.teleportRange();
    }

    private static boolean sameWorld(MobBrainContext ctx, LivingEntity followed) {
        return Objects.equals(ctx.self().getWorld(), followed.getWorld());
    }

    private static Location teleportTarget(MobBrainContext ctx, LivingEntity followed) {
        Location base = followed.getLocation();
        if (sameWorld(ctx, followed)) {
            Vector v = ctx.self().getLocation().toVector().subtract(base.toVector());
            if (v.lengthSquared() > 0) {
                v.normalize().multiply(TELEPORT_OFFSET);
                return base.clone().add(v.getX(), 0, v.getZ());
            }
        }
        return base.clone().add(CROSS_WORLD_OFFSET_X, 0, CROSS_WORLD_OFFSET_Z);
    }

    private LivingEntity resolveFollowed(MobBrainContext ctx) {
        String rawUuid = NBT.<String>getPersistentData(ctx.self(), nbt -> nbt.getString(config.followEntityUuidKey()));
        if (rawUuid == null || rawUuid.isBlank()) return null;
        UUID followed = parseUuid(rawUuid);
        if (followed == null) return null;
        Entity entity = Bukkit.getEntity(followed);
        if (entity instanceof LivingEntity living && !living.isDead()) return living;
        return null;
    }

    /**
     * Parses (and memoises) the followed UUID. The cache holds strings only, so
     * the goal stays stateless per mob and safe to share between mobs; entries
     * are bounded by the number of distinct followers and the cache is dropped
     * wholesale if it ever grows past a sane limit.
     */
    private static UUID parseUuid(String rawUuid) {
        UUID cached = UUID_CACHE.get(rawUuid);
        if (cached != null) return cached;
        try {
            UUID parsed = UUID.fromString(rawUuid);
            if (UUID_CACHE.size() >= UUID_CACHE_LIMIT) UUID_CACHE.clear();
            UUID_CACHE.put(rawUuid, parsed);
            return parsed;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Immutable per-goal configuration; safe to share across mobs. */
    public record FollowEntityConfig(
            String followEntityUuidKey,
            double followRange,
            double teleportRange,
            double speed,
            boolean clearTargetWhenFollowed) {}
}