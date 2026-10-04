package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Circle 9 EARTH capstone: two faults tear the arena apart in parallel, perpendicular to wherever
 * the caster is looking, and the slab of ground trapped between them is levered upward with
 * everything standing on it. <b>The faults stay. The arena is permanently split.</b> That is the
 * point of a circle-9 terrain spell.
 *
 * <h2>One block allowance, not two</h2>
 * {@code int[] blocksLeft = {terrainBudget}} is allocated once and the <em>same array reference</em>
 * is threaded through the fault-advance loop and the slab-lift loop. Two counters would each be
 * able to spend 2000, giving 4000; the sum could then only be held under the ceiling by every call
 * site remembering to check the other one. One shared counter decrements from a single number
 * that starts at the ceiling, so {@code destroyed_so_far + placed_so_far <= 2000} holds by
 * construction - there is no arrangement of options, region permissions or chunk boundaries that
 * can exceed it. If the allowance runs out mid-dig the first fault to ask takes what is left and
 * the second gets nothing for that tick, which is the correct behaviour rather than a special case.
 *
 * <h2>What is restored and what is not</h2>
 * The slab is placed, recorded and restored by one {@link TerrainEditor#restoreAll}. The faults are
 * dug with {@code dropItems = true}, so {@code breakNaturally} has already paid them out as items
 * and putting them back would duplicate the material. A fault line that heals itself is not a fault
 * line.
 *
 * <h2>Damage contract</h2>
 * The slab crushes everything on it exactly once, on the single lift tick: one
 * {@link SpellAttackContext}, one {@link AttackProperties}, one {@code ctx.tick()}, one
 * {@code ctx.power(target)} per target. {@code power: 28.6} is the section 6 circle-9 ladder value
 * and {@code 28.6 <= burstBudget(9) = 90.0}; no {@code hit_count} is declared because there is
 * exactly one application per target.
 */
public class Worldbreaker extends AttackSpell {

    /** Violet-black basalt. Hoisted to a static so it is allocated once, and deliberately NOT
     * derived from {@code element}: this element's other three new spells are ember orange,
     * magma red and moss green. */
    private static final Color BASALT = Color.fromRGB(64, 42, 92);

    /** The growing fault traces and the trench rim. */
    private static final Particle.DustOptions FAULT_DUST = new Particle.DustOptions(BASALT, 1.0F);
    /** The rising slab. */
    private static final Particle.DustOptions LIFT_DUST = new Particle.DustOptions(BASALT, 0.7F);
    /** The cast circle. */
    private static final Particle.DustOptions CIRCLE_DUST = new Particle.DustOptions(BASALT, 0.4F);

    /** How often the telegraph's rumble repeats, in ticks. */
    private static final int TELEGRAPH_SOUND_EVERY = 5;

    /** Vertical half-height of the entity scan over the trapped slab. */
    private static final double CRUSH_SCAN_HEIGHT = 4.0;

    /** Material the raised slab is placed as. One fixed material rather than N per-column lookups:
     * {@code TerrainEditor.placeBlocks} takes one material for the whole list, so a per-column
     * material would need N calls - and one call per layer is both cheaper and easier to reason
     * about against the budget. */
    private static final Material SLAB_MATERIAL = Material.POLISHED_BLACKSTONE;

    /** Every ad-hoc read below carries the literal the yml ships, so a missing key is not a 0. */
    private int telegraphTicks = 20;
    private double riftSpacing = 14.0;
    private int riftLength = 30;
    private int riftStep = 2;
    private double riftRadius = 2.0;
    private int crushWidth = 10;
    private int crushLift = 3;
    private double crushLiftVelocity = 1.8;
    private double riftHoldSeconds = 6.0;
    private int terrainBudget = 2000;

    public Worldbreaker(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/worldbreaker.yml");
        Alkatraz.getInstance().saveConfig("spells/worldbreaker_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/worldbreaker.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.telegraphTicks = Math.max(1, spellConfig.getInt("telegraph_ticks", 20));
        this.riftSpacing = spellConfig.getDouble("rift_spacing", 14.0);
        this.riftLength = Math.max(0, spellConfig.getInt("rift_length", 30));
        this.riftStep = Math.max(1, spellConfig.getInt("rift_step", 2));
        this.riftRadius = spellConfig.getDouble("rift_radius", 2.0);
        this.crushWidth = Math.max(0, spellConfig.getInt("crush_width", 10));
        this.crushLift = Math.max(0, spellConfig.getInt("crush_lift", 3));
        this.crushLiftVelocity = spellConfig.getDouble("crush_lift_velocity", 1.8);
        this.riftHoldSeconds = spellConfig.getDouble("rift_hold_seconds", 6.0);
        this.terrainBudget = Math.max(0, spellConfig.getInt("terrain_budget", 2000));
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        SpellAttackContext ctx = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);
        AttackProperties props = ctx.properties();

        Vector dir = caster.getEyeLocation().getDirection().setY(0);
        Utils.safeNormalizeInPlace(dir);
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());

        Location found = Utils.findTopSolid(caster.getLocation(), 8);
        Location centre = found != null ? found : caster.getLocation();
        if (centre.getWorld() == null) return;

        double half = riftSpacing / 2.0;
        List<Location> rifts = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            Location anchor = centre.clone().add(perp.clone().multiply(side * half));
            Location ground = Utils.findTopSolid(anchor.clone().add(0, 3, 0), 6);
            rifts.add(ground != null ? ground : anchor);
        }

        int digWindow = digTicks(getModifiedStat(caster, "rift_length", riftLength));
        int lift = Math.max(0, (int) Math.round(getModifiedStat(caster, "crush_lift", crushLift)));
        runSplit(caster, wand, ctx, props, centre, dir, perp, rifts, digWindow, lift);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Player-only spell. A capstone that rewrites 1890 blocks of an arena should not be
        // something a scheduled mob repeats every CastSpellGoal cooldown.
    }

    /** Dig ticks each fault takes to run its full length. */
    private int digTicks(double length) {
        return (int) Math.floor(length / riftStep);
    }

    /**
     * All three phases - telegraph, dig, lift - in one task, closing over the single
     * {@link SpellAttackContext} and the single block allowance, so countering kills the whole cast.
     */
    private void runSplit(LivingEntity caster, ItemStack wand, SpellAttackContext ctx,
                           AttackProperties props, Location centre, Vector dir, Vector perp,
                           List<Location> rifts, int digWindow, int lift) {
        // ONE shared allowance for the WHOLE cast - both faults and the slab. This is why there is
        // no second counter: a single int[] that starts at the ceiling and is decremented by every
        // write makes the SUM a structural guarantee rather than something two code paths must
        // politely agree on.
        int[] blocksLeft = {terrainBudget};
        Map<Location, Material> slabOriginals = new LinkedHashMap<>();

        new BukkitRunnable() {
            int phase = 0;                 // 0 telegraph, 1 dig, 2 lift
            int ticks = 0;

            @Override
            public void run() {
                if (ctx.isSpent() || caster.isDead()
                        || (caster instanceof Player player && !player.isOnline())) {
                    cancel();
                    settleSlab(caster, slabOriginals);
                    return;
                }

                if (phase == 0) {
                    if (ticks >= telegraphTicks) {
                        phase = 1;
                        ticks = 0;
                        return;
                    }
                    telegraph(caster, centre, dir, rifts, ticks);
                    ticks++;
                    return;
                }

                if (phase == 1) {
                    if (ticks >= digWindow) {
                        phase = 2;
                        ticks = 0;
                        liftSlab(caster, wand, ctx, props, dir, perp, rifts, slabOriginals, blocksLeft, lift);
                        return;
                    }
                    dig(caster, dir, rifts, ticks, blocksLeft);
                    ticks++;
                    return;
                }

                ticks++;
                if (ticks >= holdTicks()) {
                    cancel();
                    settleSlab(caster, slabOriginals);
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * Phase 0: two fault traces growing out from the midpoint of each fault toward its ends.
     * <b>No block is written during the telegraph</b> - it is twenty ticks of lines and a rumble.
     */
    private void telegraph(LivingEntity caster, Location centre, Vector dir,
                           List<Location> rifts, int ticks) {
        double progress = (double) ticks / telegraphTicks;
        for (Location rift : rifts) {
            for (int end = -1; end <= 1; end += 2) {
                int reach = (int) Math.floor(riftLength / 2.0 * progress);
                Location from = rift.clone().add(dir.clone().multiply(end * reach));
                Location to = rift.clone().add(dir.clone().multiply(-end * reach));
                for (Location point : ParticleUtils.line(0.5, from, to)) {
                    ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point.clone().add(0, 0.06, 0),
                            0, FAULT_DUST);
                }
            }
            ParticleCaster.spawn(caster, rift.getWorld(), Utils.DUST, rift.clone().add(0, 0.06, 0),
                    0, FAULT_DUST);
        }
        if (ticks % TELEGRAPH_SOUND_EVERY == 0) {
            centre.getWorld().playSound(centre, Sound.BLOCK_STONE_BREAK, 1.0f,
                    0.4f + 0.3f * (float) progress);
        }
    }

    /**
     * Phase 1: both faults advance on the same tick and draw from the same {@code blocksLeft}.
     * Neither one is special-cased and neither gets its own allowance.
     */
    private void dig(LivingEntity caster, Vector dir, List<Location> rifts, int tick, int[] blocksLeft) {
        for (Location rift : rifts) {
            Location head = rift.clone().add(dir.clone().multiply(tick * (double) riftStep));
            Location ground = Utils.findTopSolid(head.clone().add(0, 3, 0), 6);
            if (ground == null) continue;
            World world = ground.getWorld();
            if (world == null) continue;

            if (blocksLeft[0] > 0) {
                int allowed = Math.min(blocksLeft[0], sphereCount(riftRadius));
                blocksLeft[0] -= TerrainEditor.destroySphere(
                        caster, ground.clone().add(0, -1, 0), riftRadius, true, allowed);
            }

            world.playSound(ground, Sound.BLOCK_STONE_BREAK, 0.8f, 0.5f);
            ParticleCaster.spawn(caster, world, Utils.BLOCK, ground.clone().add(0, 0.5, 0),
                    8, 0.3, 0.2, 0.3, 0.3, Material.GRAVEL.createBlockData());
            for (Location point : ParticleUtils.circle(ground.clone().add(0, 0.06, 0),
                    riftRadius, 12, ground.getYaw(), 0)) {
                ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, FAULT_DUST);
            }
        }
    }

    /**
     * Phase 2: the crush tick and the lift. The damage happens on the same tick the slab starts
     * moving, exactly once per target, off the one shared {@link AttackProperties}.
     */
    private void liftSlab(LivingEntity caster, ItemStack wand, SpellAttackContext ctx,
                          AttackProperties props, Vector dir, Vector perp, List<Location> rifts,
                          Map<Location, Material> slabOriginals, int[] blocksLeft, int lift) {
        Location slabCentre = midpoint(rifts.get(0), rifts.get(1));
        if (slabCentre.getWorld() == null) return;

        ctx.tick();
        for (LivingEntity target : Utils.getNearbyLivingEntities(slabCentre, crushWidth / 2.0,
                CRUSH_SCAN_HEIGHT, riftLength / 2.0)) {
            if (target.equals(caster) || target.isDead()) continue;
            if (props.hasHit(target)) continue;
            props.hit(target);
            target.setVelocity(new Vector(0, crushLiftVelocity, 0));
            // The ONLY getPower call in this spell.
            SpellDamageUtil.damageWithSpell(target, ctx.power(target), caster, wand, Worldbreaker.this);
        }

        for (int layer = 1; layer <= lift; layer++) {
            if (blocksLeft[0] <= 0) break;
            List<Location> targets = slabFootprint(slabCentre, dir, perp, layer);
            if (targets.isEmpty()) break;
            // RECORD EVERY ORIGINAL BEFORE WRITING ANY OF IT.
            for (Location loc : targets) {
                TerrainEditor.recordInto(slabOriginals, loc, loc.getBlock().getType());
            }
            while (targets.size() > blocksLeft[0]) targets.remove(targets.size() - 1);
            if (targets.isEmpty()) break;
            blocksLeft[0] -= TerrainEditor.placeBlocks(caster, targets, SLAB_MATERIAL);

            for (Location loc : targets) {
                ParticleCaster.spawn(caster, loc.getWorld(), Utils.BLOCK, loc.clone().add(0.5, 0.5, 0.5),
                        6, 0.2, 0.3, 0.2, 0.3, SLAB_MATERIAL.createBlockData());
                ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc.clone().add(0.5, 1.05, 0.5),
                        0, LIFT_DUST);
            }
        }

        slabCentre.getWorld().playSound(slabCentre, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.6f);
        new BukkitRunnable() {
            @Override
            public void run() {
                settleSlab(caster, slabOriginals);
            }
        }.runTaskLater(Alkatraz.getInstance(), holdTicks());
    }

    /**
     * The one restore. Re-checks Protection per block, so a region entered during the hold is not
     * rewritten - the exact defect {@link TerrainEditor} exists to close. Idempotent, because the
     * map is cleared after the restore and an empty ticket makes {@code restoreAll} a no-op.
     */
    private void settleSlab(LivingEntity caster, Map<Location, Material> slabOriginals) {
        if (slabOriginals.isEmpty()) return;
        TerrainEditor.restoreAll(caster, new TerrainEditor.RestoreTicket(slabOriginals));
        slabOriginals.clear();
    }

    /** One layer of the trapped slab: a rectangle centred between the two faults. */
    private List<Location> slabFootprint(Location slabCentre, Vector dir, Vector perp, int layer) {
        World world = slabCentre.getWorld();
        if (world == null) return new ArrayList<>();
        int halfLength = Math.max(1, riftLength / 2);
        int halfWidth = Math.max(0, crushWidth / 2);
        int y = slabCentre.getBlockY() + layer;
        List<Location> out = new ArrayList<>();
        for (int along = -halfLength; along <= halfLength; along++) {
            for (int across = -halfWidth; across <= halfWidth; across++) {
                Vector offset = dir.clone().multiply(along).add(perp.clone().multiply(across));
                out.add(new Location(world,
                        slabCentre.getBlockX() + offset.getX(),
                        y,
                        slabCentre.getBlockZ() + offset.getZ()));
            }
        }
        return out;
    }

    private static Location midpoint(Location a, Location b) {
        return new Location(a.getWorld(),
                (a.getX() + b.getX()) / 2.0,
                (a.getY() + b.getY()) / 2.0,
                (a.getZ() + b.getZ()) / 2.0);
    }

    /** Ticks the raised slab stands before it settles back. */
    private int holdTicks() {
        return Math.max(1, (int) Math.round(riftHoldSeconds * 20.0));
    }

    /**
     * Mirrors {@link TerrainEditor#destroySphere}'s candidate enumeration. Used only to pre-clamp
     * {@code maxBlocks}; it never writes a block itself.
     */
    private static int sphereCount(double radius) {
        if (radius <= 0) return 0;
        int r = (int) Math.ceil(radius);
        int count = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) count++;
                }
            }
        }
        return count;
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
        ParticleCaster.spawn(caster, location.getWorld(), Utils.BLOCK, location, 30,
                SLAB_MATERIAL.createBlockData());
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION, location, 3);
        ParticleCaster.spawn(null, location.getWorld(), Utils.LARGE_SMOKE, location, 45,
                0.8, 0.5, 0.8, 0.3);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.worldbreaker.book_name"))
                .addCustomLoreLine(lang().get("spells.worldbreaker.lore1"))
                .addCustomLoreLine(lang().get("spells.worldbreaker.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}
