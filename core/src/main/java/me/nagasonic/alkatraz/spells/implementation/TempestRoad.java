package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Circle 7 AIR UTILITY: lays a wind lane that carries players along it for a time and grants
 * slow falling when they leave. No damage. The lane is fixed in place when cast.
 */
public class TempestRoad extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.AIR);
    /** Forward push applied each tick while a player rides the lane. */
    private static final double CARRY_SPEED = 0.55;
    /** Upward nudge so riders stay aloft in the lane. */
    private static final double LIFT = 0.08;
    /** Slow falling granted on exit, in ticks. */
    private static final int EXIT_SLOW_FALL_TICKS = 60;

    private static final Map<UUID, BukkitTask> LANE_TASKS = new HashMap<>();

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public TempestRoad(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/tempest_road_options.yml");
        Alkatraz.getInstance().save("spells/tempest_road.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/tempest_road.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        int length = (int) Math.round(getModifiedStat(p, "road_length", option(p, "length", 36)));
        int durationSeconds = (int) Math.round(getModifiedStat(p, "road_duration", option(p, "duration", 30)));
        Vector forward = horizontalDirection(p);
        if (forward == null) return;

        UUID ownerId = p.getUniqueId();
        BukkitTask previous = LANE_TASKS.remove(ownerId);
        if (previous != null) previous.cancel();
        World world = p.getWorld();
        Location origin = p.getLocation().clone();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        final int totalTicks = Math.max(1, durationSeconds * 20);
        final double halfWidth = 3.0; // 6 wide
        final double laneLength = Math.max(1.0, length);
        final Set<UUID> inside = new HashSet<>();

        BukkitTask laneTask = new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (ticks++ >= totalTicks || world == null || !isWorldLoaded(world)) {
                    settle(inside, world);
                    cancel();
                    LANE_TASKS.remove(ownerId);
                    return;
                }
                Set<UUID> nowInside = new HashSet<>();
                collectInside(world, origin, forward, right, laneLength, halfWidth, nowInside);
                // grant slow falling to players who left the lane
                for (UUID id : new HashSet<>(inside)) {
                    if (!nowInside.contains(id)) {
                        Player rider = playerIfValid(world, id);
                        if (rider != null) grantExitSlowFall(rider);
                        inside.remove(id);
                    }
                }
                // carry players currently in the lane
                for (UUID id : nowInside) {
                    Player rider = playerIfValid(world, id);
                    if (rider == null) continue;
                    inside.add(id);
                    Vector vel = forward.clone().multiply(CARRY_SPEED);
                    vel.setY(Math.max(rider.getVelocity().getY(), LIFT));
                    rider.setVelocity(vel);
                    rider.setFallDistance(0f);
                }
                drawLane(world, p, origin, forward, right, laneLength, halfWidth, ticks);
            }
        }.runTaskTimer(Alkatraz.getInstance(), 1L, 1L);
        LANE_TASKS.put(ownerId, laneTask);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    private void settle(Set<UUID> inside, World world) {
        for (UUID id : inside) {
            Player rider = world == null ? null : playerIfValid(world, id);
            if (rider == null) {
                // try global lookup so exit slow fall can still apply if world ref is stale
                Entity e = Alkatraz.getInstance().getServer().getEntity(id);
                if (e instanceof Player p && p.isOnline() && !p.isDead()) {
                    grantExitSlowFall(p);
                }
            } else {
                grantExitSlowFall(rider);
            }
        }
        inside.clear();
    }

    private static void grantExitSlowFall(Player rider) {
        rider.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, EXIT_SLOW_FALL_TICKS, 0, true, false, true));
        rider.setFallDistance(0f);
    }

    private static Player playerIfValid(World world, UUID id) {
        Entity e = Alkatraz.getInstance().getServer().getEntity(id);
        if (!(e instanceof Player p)) return null;
        if (!p.isOnline() || p.isDead()) return null;
        if (world != null && p.getWorld() != world) return null;
        return p;
    }

    private static boolean isWorldLoaded(World world) {
        for (World w : Alkatraz.getInstance().getServer().getWorlds()) {
            if (w.equals(world)) return true;
        }
        return false;
    }

    private static void collectInside(World world, Location origin, Vector forward, Vector right,
                                      double length, double halfWidth, Set<UUID> out) {
        double reach = length + 2.0;
        for (Entity entity : world.getNearbyEntities(origin.clone().add(forward.clone().multiply(length / 2.0)),
                reach, 4.0, reach)) {
            if (!(entity instanceof Player player)) continue;
            if (!player.isOnline() || player.isDead() || player.getWorld() != world) continue;
            if (isInsideLane(player.getLocation(), origin, forward, right, length, halfWidth)) {
                out.add(player.getUniqueId());
            }
        }
    }

    /** True when {@code loc} lies within the lane prism above {@code origin}. */
    static boolean isInsideLane(Location loc, Location origin, Vector forward, Vector right,
                                double length, double halfWidth) {
        if (loc == null || origin == null || loc.getWorld() == null || origin.getWorld() == null) return false;
        if (!loc.getWorld().equals(origin.getWorld())) return false;
        Vector delta = loc.toVector().subtract(origin.toVector());
        double along = delta.dot(forward);
        double across = delta.dot(right);
        double up = delta.getY();
        return along >= -1.0 && along <= length + 1.0
                && Math.abs(across) <= halfWidth + 0.5
                && up >= -1.5 && up <= 4.0;
    }

    private static Vector horizontalDirection(Player p) {
        Vector dir = Utils.safeNormalize(p.getLocation().getDirection());
        dir.setY(0);
        if (dir.lengthSquared() < 1e-4) return null;
        dir.normalize();
        return dir;
    }

    private void drawLane(World world, Player caster, Location origin, Vector forward, Vector right,
                          double length, double halfWidth, int tick) {
        // light particle hints along the lane edges and center
        for (double d = 0; d <= length; d += 4.0) {
            Location center = origin.clone().add(forward.clone().multiply(d)).add(0, 0.5, 0);
            ParticleCaster.spawn(caster, world, Utils.DUST, center, 1, PALETTE.accentDust());
            Location left = center.clone().add(right.clone().multiply(-halfWidth));
            Location rightEdge = center.clone().add(right.clone().multiply(halfWidth));
            ParticleCaster.spawn(caster, world, Utils.DUST, left, 1, PALETTE.deepDust());
            ParticleCaster.spawn(caster, world, Utils.DUST, rightEdge, 1, PALETTE.deepDust());
        }
        if (tick % 10 == 0) {
            Location mid = origin.clone().add(forward.clone().multiply(length / 2.0)).add(0, 1.2, 0);
            SpellVfx.ring(PALETTE.core(), caster, mid, halfWidth, 20, tick * 6, 0.05);
        }
    }

    @Override
    public void cancelTasks() {
        for (BukkitTask task : LANE_TASKS.values()) {
            if (task != null) task.cancel();
        }
        LANE_TASKS.clear();
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 20) / 20.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.3, 0),
                scale * 0.8, 20, frame * 8, 0.0);
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.4 + phase, 0),
                scale * (1.0 + phase), 18, -frame * 6, 0.0);
        SpellVfx.ring(palette.deep(), caster, origin.clone().add(0, 0.2, 0),
                scale * (1.4 - phase * 0.4), 24, frame * 4, 0.1);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        double radius = 1.2 + progress * 2.0;
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.4, 0), radius, 24, tick * 8, 0.05);
        if (tick % 5 == 0) {
            SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 1.0 + progress, 0),
                    0.8 + progress, 16, -tick * 8, 0.0);
        }
    }

    /** Numeric option value, falling back when the option file is absent. */
    private double option(Player p, String id, double fallback) {
        if (getOption(id) == null) return fallback;
        Object value = getOption(id).getSelectedValue(p).getValue();
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.tempestroad.book_name"))
                .addCustomLoreLine(lang().get("spells.tempestroad.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }
}
