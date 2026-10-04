package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.vfx.ChannelRegistry;
import me.nagasonic.alkatraz.spells.vfx.WindUp;
import me.nagasonic.alkatraz.spells.util.TerrainEditor;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 8 EARTH: channel, and a <b>real plateau</b> is levered out of the ground - walkable stone,
 * six blocks high and twelve across, that you can stand on and drop off the edge of. It holds for
 * eight seconds, then collapses back into the crust and leaves <b>no scar at all</b>.
 *
 * <h2>Why this spell places and never destroys</h2>
 * {@link TerrainEditor#destroySphere} with {@code dropItems = true} calls {@code breakNaturally()},
 * so a destroyed block leaves the world as an item and restoring it later duplicates that material.
 * A spell that both destroys and restores is therefore a duplication bug waiting to happen. This
 * one leaves no scar, so it places and restores and never destroys: the plateau is stone from
 * nowhere that vanishes again from nowhere. The alternative - permanent terrain loss - is what
 * tectonic_strike and fault_line are for.
 *
 * <h2>Interruption</h2>
 * The channel is a {@link WindUp} registered with the shared {@link ChannelRegistry}, so moving
 * past {@code move_cancel_threshold}, taking any damage, quitting or plugin shutdown all interrupt
 * it - and the canceller this class registers puts every layer placed so far back through
 * {@link TerrainEditor#restoreAll}. Aborting mid-channel therefore leaves no scar either. Once the
 * plateau is standing it lives in {@link #STANDING}, which {@link #onPluginDisable} and
 * {@link #onQuit} settle, so it never outlives the server or its caster.
 *
 * <h2>Damage contract</h2>
 * This spell deals <b>no damage</b>. It is {@code role: 'TERRAIN'}, judged on blocks by
 * {@code terrain_budget}, and it declares no {@code hit_count}: a hit count on a spell that never
 * hits anything would be dead config. {@code power: 22.7} is present only so the single-key budget
 * assertion has a number to read, and {@code getPower} is never called.
 */
public class ContinentalShift extends AttackSpell implements Listener {

    /**
     * Desaturated moss green. Hoisted to a static so it is allocated once, and deliberately NOT
     * derived from {@code element}: TectonicStrike's ember orange, FaultLine's magma red and
     * Worldbreaker's violet-black are this element's other identities.
     */
    private static final Color MOSS = Color.fromRGB(88, 150, 108);

    /** The rising dust off a freshly placed block. */
    private static final Particle.DustOptions LIFT_DUST = new Particle.DustOptions(MOSS, 0.8F);
    /** The collapse curtain at the plateau's foot. */
    private static final Particle.DustOptions BASE_DUST = new Particle.DustOptions(MOSS, 0.5F);
    /** The cast circle. */
    private static final Particle.DustOptions CIRCLE_DUST = new Particle.DustOptions(MOSS, 0.4F);

    /** Ticks between the two crash lines of the collapse animation's sound. */
    private static final int COLLAPSE_SOUND_EVERY = 4;

    /**
     * Plateaus that are currently standing, keyed by the caster who raised them. This is what
     * {@link #onPluginDisable} and {@link #onQuit} settle: once the channel completes, the plateau
     * is no longer anybody's wind-up, so the shared {@link ChannelRegistry} cannot own it.
     */
    private static final Map<UUID, Plateau> STANDING = new ConcurrentHashMap<>();

    /** One standing plateau: the caster to re-gate the restore on, and every recorded original. */
    private record Plateau(Player caster, Map<Location, Material> originals) {
    }

    /**
     * Every ad-hoc read below carries the literal the yml ships, so a missing key is not a silent
     * 0. {@code move_cancel_threshold} is deliberately NOT read here -
     * {@link WindUp#moveCancelThreshold} reads it off the yml itself, which is what lets a
     * movement event interrupt the channel without this class knowing the number.
     */
    private double channelSeconds = 3.0;
    private double plateauReach = 20.0;
    private double plateauRadiusX = 6.0;
    private double plateauRadiusZ = 4.0;
    private int plateauHeight = 6;
    private Material plateauMaterial = Material.POLISHED_ANDESITE;
    private double plateauHoldSeconds = 8.0;
    private int collapseTicks = 12;
    private int terrainBudget = 1400;

    public ContinentalShift(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/continental_shift.yml");
        Alkatraz.getInstance().saveConfig("spells/continental_shift_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/continental_shift.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.channelSeconds = spellConfig.getDouble("channel_seconds", 3.0);
        this.plateauReach = spellConfig.getDouble("plateau_reach", 20.0);
        this.plateauRadiusX = spellConfig.getDouble("plateau_radius_x", 6.0);
        this.plateauRadiusZ = spellConfig.getDouble("plateau_radius_z", 4.0);
        this.plateauHeight = Math.max(1, spellConfig.getInt("plateau_height", 6));
        this.plateauHoldSeconds = spellConfig.getDouble("plateau_hold_seconds", 8.0);
        this.collapseTicks = Math.max(1, spellConfig.getInt("collapse_ticks", 12));
        this.terrainBudget = Math.max(0, spellConfig.getInt("terrain_budget", 1400));
        Material resolved = Material.matchMaterial(
                spellConfig.getString("plateau_material", "POLISHED_ANDESITE"));
        this.plateauMaterial = resolved != null ? resolved : Material.POLISHED_ANDESITE;
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        UUID uuid = caster.getUniqueId();
        if (ChannelRegistry.isChanneling(uuid)) return;      // one channel per player
        if (!Protection.blockEdit(caster, caster.getLocation())) {
            cancelCast(caster);
            return;
        }

        Location aimed = Utils.resolveTarget(caster, (int) Math.round(plateauReach));
        if (aimed == null) {
            cancelCast(caster);
            return;
        }
        Location found = Utils.findTopSolid(aimed.clone().add(0, 3, 0), 6);
        final Location centre = found != null ? found : caster.getLocation();
        if (centre.getWorld() == null) {
            cancelCast(caster);
            return;
        }

        // Captured once: the plateau keeps the orientation the caster had when the channel
        // started, even if they are gone by the time it collapses.
        final double yaw = Math.toRadians(caster.getLocation().getYaw());
        final double rx = getModifiedStat(caster, "plateau_radius_x", plateauRadiusX);
        final double rz = getModifiedStat(caster, "plateau_radius_z", plateauRadiusZ);
        final int height = Math.max(1, (int) Math.round(getModifiedStat(caster, "plateau_height", plateauHeight)));

        int channelTicks = WindUp.effectiveTicks(
                (int) Math.round(channelSeconds * 20.0), masteryOf(caster), getMaxMastery());
        WindUp windUp = WindUp.begin(this, caster, channelTicks, centre);

        // ONE shared allowance for the whole cast. Checked before every one of the six layer
        // writes, so the SUM across all of them can never exceed terrain_budget.
        final int[] blocksLeft = {terrainBudget};
        final Map<Location, Material> originals = new LinkedHashMap<>();

        final int[] taskId = new int[1];
        ChannelRegistry.register(uuid, windUp, () -> {
            if (taskId[0] != 0) Bukkit.getScheduler().cancelTask(taskId[0]);
            // A partial channel may already have placed layers. Put them all back - through
            // TerrainEditor, so Protection is re-checked per block.
            TerrainEditor.restoreAll(caster, new TerrainEditor.RestoreTicket(originals));
            originals.clear();
            STANDING.remove(uuid);
        });

        taskId[0] = new BukkitRunnable() {
            int ticks = 0;
            int nextLayer = 0;

            @Override
            public void run() {
                if (ticks >= channelTicks) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    complete(caster, centre, yaw, rx, rz, height, originals);
                    return;
                }
                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    TerrainEditor.restoreAll(caster, new TerrainEditor.RestoreTicket(originals));
                    originals.clear();
                    STANDING.remove(uuid);
                    return;
                }
                windUp.tick();

                // One layer every channelTicks / height ticks, so the plateau rises WITH the
                // channel instead of popping in at the end.
                int layerInterval = Math.max(1, channelTicks / height);
                if (ticks % layerInterval == 0 && nextLayer < height) {
                    raiseLayer(caster, centre, yaw, rx, rz, nextLayer, originals, blocksLeft);
                    nextLayer++;
                }
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L).getTaskId();
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Player-only spell.
    }

    private int masteryOf(Player caster) {
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        return profile == null ? 0 : profile.getSpellMastery(this);
    }

    /**
     * One layer of the plateau: the only world write in the whole spell. Every original is
     * recorded <b>before</b> any of the layer is written, so an abort halfway up still puts the
     * ground back exactly as it found it.
     */
    private void raiseLayer(Player caster, Location centre, double yaw, double rx, double rz,
                            int layer, Map<Location, Material> originals, int[] blocksLeft) {
        if (blocksLeft[0] <= 0) return;
        List<Location> targets = footprint(centre, yaw, rx, rz, layer);
        if (targets.isEmpty()) return;

        // RECORD EVERY ORIGINAL BEFORE WRITING ANY OF IT.
        for (Location loc : targets) {
            TerrainEditor.recordInto(originals, loc, loc.getBlock().getType());
        }
        // Truncate the request to what the shared allowance still permits, so the SUM across all
        // six layers can never exceed terrain_budget - the paid 'colossus' option asks for 1467
        // against a ceiling of 1400 and is cut at the ceiling rather than overshooting it.
        while (targets.size() > blocksLeft[0]) targets.remove(targets.size() - 1);
        if (targets.isEmpty()) return;

        blocksLeft[0] -= TerrainEditor.placeBlocks(caster, targets, plateauMaterial);

        for (Location loc : targets) {
            ParticleCaster.spawn(caster, loc.getWorld(), Utils.BLOCK, loc.clone().add(0.5, 0.5, 0.5),
                    3, 0.1, 0.15, 0.1, 0.05, plateauMaterial.createBlockData());
            ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc.clone().add(0.5, 1.05, 0.5),
                    0, LIFT_DUST);
        }
    }

    /**
     * The plateau stands, then collapses. The cosmetic curtain and the actual restore are two
     * separate tasks on purpose: the restore is scheduled independently of the animation, so the
     * two cannot desynchronise and leave blocks behind.
     */
    private void complete(Player caster, Location centre, double yaw, double rx, double rz,
                          int height, Map<Location, Material> originals) {
        UUID uuid = caster.getUniqueId();
        if (caster.isDead() || !caster.isOnline()) {
            TerrainEditor.restoreAll(caster, new TerrainEditor.RestoreTicket(originals));
            originals.clear();
            return;
        }
        if (originals.isEmpty()) return;      // every write was refused; nothing was left behind

        STANDING.put(uuid, new Plateau(caster, originals));

        World world = centre.getWorld();
        world.playSound(centre, Sound.BLOCK_ANVIL_USE, 1.0f, 0.5f);
        Location top = centre.clone().add(0, height, 0);
        for (Location point : ParticleUtils.circle(top, 2.0, 24, top.getYaw(), 0)) {
            ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point, 0, LIFT_DUST);
        }

        int hold = holdTicks();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > hold + collapseTicks) {
                    cancel();
                    return;
                }
                if (ticks == hold) {
                    world.playSound(centre, Sound.BLOCK_STONE_BREAK, 1.2f, 0.4f);
                }
                if (ticks > hold) {
                    collapseCurtain(caster, centre, yaw, rx, rz, height, ticks - hold);
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);

        // ONE call, ONE restore, per-block Protection re-check. Fires exactly once.
        new BukkitRunnable() {
            @Override
            public void run() {
                settle(uuid);
            }
        }.runTaskLater(Alkatraz.getInstance(), hold + collapseTicks);
    }

    /**
     * The collapse animation, sweeping down the plateau's face. <b>Cosmetic only</b> - it writes
     * no block; the real restore is the single task scheduled alongside it.
     */
    private void collapseCurtain(LivingEntity caster, Location centre, double yaw, double rx,
                                 double rz, int height, int step) {
        if (step > collapseTicks) return;
        int remaining = Math.max(1, height - (int) Math.floor(height * ((double) step / collapseTicks)));
        List<Location> ring = footprint(centre, yaw, rx, rz, remaining - 1);
        for (Location loc : ring) {
            ParticleCaster.spawn(caster, loc.getWorld(), Utils.BLOCK, loc.clone().add(0.5, 0.5, 0.5),
                    6, 0.3, 0.2, 0.3, 0.2, plateauMaterial.createBlockData());
            ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc.clone().add(0.5, 0.05, 0.5),
                    0, BASE_DUST);
        }
        if (step % COLLAPSE_SOUND_EVERY == 0) {
            centre.getWorld().playSound(centre, Sound.BLOCK_STONE_BREAK, 0.5f, 0.4f);
        }
    }

    /**
     * Puts a standing plateau back and forgets it. Idempotent: the map entry is removed first, so
     * the disable handler, the quit handler and the timer cannot restore the same blocks twice.
     */
    private void settle(UUID uuid) {
        Plateau standing = STANDING.remove(uuid);
        if (standing == null) return;
        TerrainEditor.restoreAll(standing.caster(), new TerrainEditor.RestoreTicket(standing.originals()));
        standing.originals().clear();
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent e) {
        settle(e.getPlayer().getUniqueId());
    }

    @EventHandler
    private void onPluginDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Alkatraz.getInstance()) return;
        for (UUID uuid : STANDING.keySet()) settle(uuid);
        STANDING.clear();
    }

    /**
     * Every column of one layer of the plateau footprint: a filled ellipse in the caster's yaw
     * frame, so for each {@code dz} the half-width is {@code floor(radius_x * sqrt(1 - (dz/radius_z)^2))}
     * and that row contributes {@code 2 * half + 1} columns. 6 x 4 is 73 columns, so the default
     * plateau is 73 x 6 = 438 blocks.
     *
     * <p>{@code new Location(world, double, int, double)} widens the layer's integer y, so every
     * entry lands on an exact block.
     */
    private List<Location> footprint(Location centre, double yaw, double rx, double rz, int layer) {
        World world = centre.getWorld();
        if (world == null) return new ArrayList<>();
        int zRadius = Math.max(1, (int) Math.round(rz));
        int xRadius = Math.max(1, (int) Math.round(rx));
        double fx = Math.cos(yaw);
        double fz = Math.sin(yaw);
        int y = centre.getBlockY() + 1 + layer;
        List<Location> out = new ArrayList<>();
        for (int dz = -zRadius; dz <= zRadius; dz++) {
            double inside = 1.0 - ((double) dz * dz) / ((double) zRadius * zRadius);
            if (inside < 0.0) inside = 0.0;
            int half = (int) Math.floor(xRadius * Math.sqrt(inside) + 1e-9);
            for (int dx = -half; dx <= half; dx++) {
                out.add(new Location(world,
                        centre.getBlockX() + fx * dx - fz * dz,
                        y,
                        centre.getBlockZ() + fz * dx + fx * dz));
            }
        }
        return out;
    }

    /** Ticks the plateau stands before it starts to come apart. */
    private int holdTicks() {
        return Math.max(1, (int) Math.round(plateauHoldSeconds * 20.0));
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location loc = caster.getEyeLocation();
            List<Location> points = ParticleUtils.magicCircle(loc, loc.getYaw(), loc.getPitch(),
                    loc.getDirection().normalize().multiply(1.5), 3, 0);
            for (Location point : points) {
                ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point, 0, CIRCLE_DUST);
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.BLOCK, location, 25,
                plateauMaterial.createBlockData());
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION, location, 2);
        ParticleCaster.spawn(null, location.getWorld(), Utils.LARGE_SMOKE, location, 35,
                0.6, 0.6, 0.6, 0.2);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.continentalshift.book_name"))
                .addCustomLoreLine(lang().get("spells.continentalshift.lore1"))
                .addCustomLoreLine(lang().get("spells.continentalshift.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}
