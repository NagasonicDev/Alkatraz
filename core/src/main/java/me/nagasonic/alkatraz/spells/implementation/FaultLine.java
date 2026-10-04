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
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Circle 7 EARTH: the roster's only <b>double-charged</b> spell. It is a SUSTAIN field on the
 * SUSTAIN budget and a terrain writer on the TERRAIN budget at the same time, and its yml declares
 * all three keys - {@code power}, {@code hit_count} and {@code terrain_budget} - so the budget test
 * can charge the two halves independently. They never touch: the SUSTAIN budget is spent in health
 * damage (7.6 x 10 = 76.0, exactly {@code sustainBudget(7)}), the TERRAIN budget in blocks
 * (370 at defaults, 555 on the paid {@code rift} option, against 950).
 *
 * <h2>The cadence</h2>
 * The repeating task's period AND delay are both
 * {@link SpellAttackContext#DAMAGE_INTERVAL_TICKS}, so the applications land on ticks 20, 40,
 * 60 ... 200 and nowhere else. Nothing runs between them.
 *
 * <h2>Why not {@code AttackProperties.hasHit}</h2>
 * That set is permanent for the life of the cast, which is right for a BURST and wrong for a
 * field: a target standing in the trench for the full ten seconds would be damaged exactly once,
 * ever. The runnable therefore keeps its own {@code hitThisInterval} set and clears it at the end
 * of every interval, which is what makes the cadence a per-interval one. {@code props.hit} is
 * still recorded so counters and {@code SpellComponentHandler} see the target as struck.
 *
 * <h2>Terrain</h2>
 * Every destroy is {@link TerrainEditor#destroySphere} with real drops and an explicit clamp; every
 * place is {@link TerrainEditor#placeBlocks} with a {@code recordInto} for each location first; the
 * only restore is a single {@link TerrainEditor#restoreAll} of the raised lip. The trench itself is
 * <b>not</b> restored, on purpose - {@code breakNaturally} has already paid it out as items.
 */
public class FaultLine extends AttackSpell {

    /** The circle this spell's SUSTAIN budget is charged against. */
    private static final int CIRCLE = 7;

    /**
     * Hot magma red. Hoisted to a static so it is allocated once, and deliberately NOT derived
     * from {@code element}: TectonicStrike's ember orange and Fissure's saddle brown are this
     * element's other identities and a fault line has to read as its own thing.
     */
    private static final Color MAGMA = Color.fromRGB(214, 46, 30);

    /** The trench edge. */
    private static final Particle.DustOptions CRACK_DUST = new Particle.DustOptions(MAGMA, 1.0F);
    /** The hot core inside the trench. */
    private static final Particle.DustOptions CORE_DUST = new Particle.DustOptions(MAGMA, 0.6F);
    /** The cast circle. */
    private static final Particle.DustOptions CIRCLE_DUST = new Particle.DustOptions(MAGMA, 0.4F);

    /** Vertical half-height of the entity scan around each dig site. */
    private static final double DAMAGE_SCAN_HEIGHT = 3.0;

    /** Every ad-hoc read below carries the literal the yml ships, so a missing key is not a 0. */
    private int durationSeconds = 10;
    private double crackOriginReach = 4.0;
    private int crackLength = 40;
    private int crackStep = 4;
    private double crackRadius = 2.0;
    private int crackWidth = 2;
    private double crackLift = 1.35;
    private double crackDamageRadius = 3.5;
    private Material lipMaterial = Material.POLISHED_ANDESITE;
    private int terrainBudget = 950;

    public FaultLine(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/fault_line.yml");
        Alkatraz.getInstance().saveConfig("spells/fault_line_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/fault_line.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.durationSeconds = Math.max(1, spellConfig.getInt("duration", 10));
        this.crackOriginReach = spellConfig.getDouble("crack_origin_reach", 4.0);
        this.crackLength = Math.max(0, spellConfig.getInt("crack_length", 40));
        this.crackStep = Math.max(1, spellConfig.getInt("crack_step", 4));
        this.crackRadius = spellConfig.getDouble("crack_radius", 2.0);
        this.crackWidth = Math.max(0, spellConfig.getInt("crack_width", 2));
        this.crackLift = spellConfig.getDouble("crack_lift", 1.35);
        this.crackDamageRadius = spellConfig.getDouble("crack_damage_radius", 3.5);
        this.terrainBudget = Math.max(0, spellConfig.getInt("terrain_budget", 950));
        Material resolved = Material.matchMaterial(
                spellConfig.getString("lip_material", "POLISHED_ANDESITE"));
        this.lipMaterial = resolved != null ? resolved : Material.POLISHED_ANDESITE;
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        SpellAttackContext ctx = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);

        Vector dir = caster.getEyeLocation().getDirection().setY(0);
        Utils.safeNormalizeInPlace(dir);
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());

        Location ground = Utils.findTopSolid(
                caster.getLocation().clone().add(dir.clone().multiply(crackOriginReach)), 8);
        if (ground == null) ground = caster.getLocation();

        int travel = travelTicks(getModifiedStat(caster, "crack_length", crackLength));
        runFault(caster, wand, ctx, ground, dir, perp, travel);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;

        SpellAttackContext ctx = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);

        // No crosshair and no spell options on a mob: the fault starts at its target's feet and
        // runs along its line of sight, at the raw yml crack_length.
        Vector dir = caster.getTarget().getEyeLocation().getDirection().setY(0);
        Utils.safeNormalizeInPlace(dir);
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());

        Location ground = Utils.findTopSolid(caster.getTarget().getLocation(), 8);
        if (ground == null) ground = caster.getLocation();

        runFault(caster, wand, ctx, ground, dir, perp, travelTicks(crackLength));
    }

    /**
     * How many 20-tick damage windows the fault actually walks. Bounded by BOTH the physical
     * length of the crack ({@code crack_length / crack_step}) and the field's declared life
     * ({@code duration}, which at the 20-tick cadence is the yml's {@code hit_count}). At the
     * shipped defaults both are 10, and taking the smaller of the two means neither key can
     * silently disagree with the budget.
     */
    private int travelTicks(double length) {
        int physical = (int) Math.floor(length / crackStep);
        int declared = (int) Math.floor(durationSeconds * 20.0 / SpellAttackContext.DAMAGE_INTERVAL_TICKS);
        return Math.max(0, Math.min(physical, declared));
    }

    /**
     * The whole field, shared by both cast paths: one {@link SpellAttackContext}, one block
     * allowance, one lip restore, ten damage windows at the 20-tick cadence.
     */
    private void runFault(LivingEntity caster, ItemStack wand, SpellAttackContext ctx,
                          Location ground, Vector dir, Vector perp, int travel) {
        if (travel <= 0 || ground.getWorld() == null) return;

        // ONE shared allowance for the whole cast. Every destroy and every place decrements it,
        // so the SUM of both can never exceed terrain_budget.
        int[] blocksLeft = {terrainBudget};
        Map<Location, Material> lipOriginals = new LinkedHashMap<>();

        // NOT AttackProperties.hasHit - that set is permanent for the whole cast, which would let
        // a SUSTAIN field touch a target once EVER. This one is cleared at the end of every
        // 20-tick interval, which is what makes the cadence a per-interval one.
        Set<UUID> hitThisInterval = new HashSet<>();

        // The SUSTAIN budget is 76 at circle 7 and the number of windows is known before the
        // first one lands, so the per-application figure is clamped: the worst-case total lands
        // exactly on the budget and no option combination can exceed it. 7.6 x 10 = 76 at
        // defaults, 7.6 x 2.0 x 5 = 76 on 'seam', and 76 / 15 = 5.07 x 15 = 76 on 'rift'.
        double perWindowCap = SpellBudgets.sustainBudget(CIRCLE) / travel;

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (ctx.isSpent() || caster.isDead()
                        || (caster instanceof Player player && !player.isOnline())) {
                    cancel();
                    restoreLips(caster, lipOriginals);
                    return;
                }
                if (tick >= travel) {
                    cancel();
                    restoreLips(caster, lipOriginals);
                    return;
                }

                ctx.tick();
                cut(caster, wand, ctx, ground, dir, perp, tick, blocksLeft, lipOriginals,
                        hitThisInterval, perWindowCap);
                tick++;
            }
        }.runTaskTimer(Alkatraz.getInstance(),
                SpellAttackContext.DAMAGE_INTERVAL_TICKS, SpellAttackContext.DAMAGE_INTERVAL_TICKS);
    }

    /**
     * One damage window: dig the trench, heave the lip, present it, then touch whatever is
     * standing in it. The dig happens <b>before</b> anything is damaged - the 76 points of damage
     * are what happens because the ground opens.
     */
    private void cut(LivingEntity caster, ItemStack wand, SpellAttackContext ctx, Location ground,
                     Vector dir, Vector perp, int tick, int[] blocksLeft,
                     Map<Location, Material> lipOriginals, Set<UUID> hitThisInterval,
                     double perWindowCap) {
        Location head = ground.clone().add(dir.clone().multiply(tick * (double) crackStep));
        Location dig = Utils.findTopSolid(head.clone().add(0, 3, 0), 6);
        if (dig == null) return;     // ran off the edge of the world; the field simply stops advancing
        World world = dig.getWorld();
        if (world == null) return;

        // ---- DESTROY: real drops, per-block Protection, hard clamp ----
        if (blocksLeft[0] > 0) {
            int allowed = Math.min(blocksLeft[0], sphereCount(crackRadius));
            blocksLeft[0] -= TerrainEditor.destroySphere(caster, dig.clone().add(0, -1, 0),
                    crackRadius, true, allowed);
        }

        // ---- PLACE: the lip. Record EVERY original before writing any of it. ----
        List<Location> lipTargets = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            for (int w = 0; w < crackWidth; w++) {
                Location loc = dig.clone().add(perp.clone().multiply(side * (1 + w)));
                lipTargets.add(loc);
                TerrainEditor.recordInto(lipOriginals, loc, loc.getBlock().getType());
            }
        }
        while (blocksLeft[0] <= 0 && !lipTargets.isEmpty()) lipTargets.remove(lipTargets.size() - 1);
        blocksLeft[0] -= TerrainEditor.placeBlocks(caster, lipTargets, lipMaterial);

        // ---- PRESENTATION ----
        world.playSound(dig, Sound.BLOCK_STONE_BREAK, 0.9f, 0.4f);
        world.playSound(dig, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);
        for (Location lip : lipTargets) {
            ParticleCaster.spawn(caster, world, Utils.BLOCK, lip.clone().add(0.5, 0.5, 0.5),
                    6, 0.3, 0.3, 0.3, 0.2, lipMaterial.createBlockData());
        }
        ParticleCaster.spawn(caster, world, Utils.EXPLOSION, dig.clone().add(0, 0.3, 0), 1);
        for (double d = 0; d <= crackRadius; d += 0.25) {
            for (int side = -1; side <= 1; side += 2) {
                Location point = dig.clone().add(perp.clone().multiply(side * d)).add(0, 0.06, 0);
                ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, CRACK_DUST);
                ParticleCaster.spawn(caster, world, Utils.DUST, point.clone().add(0, -0.9, 0), 0, CORE_DUST);
            }
        }

        // ---- DAMAGE: at most once per target per 20-tick interval ----
        for (LivingEntity target : Utils.getNearbyLivingEntities(dig, crackDamageRadius,
                DAMAGE_SCAN_HEIGHT, crackDamageRadius)) {
            if (target.equals(caster) || target.isDead()) continue;
            if (!hitThisInterval.add(target.getUniqueId())) continue;
            ctx.properties().hit(target);
            target.setVelocity(new Vector(0, crackLift, 0));
            // Exactly ONE getPower per damage instance. The base is clamped to the SUSTAIN
            // budget's share of one window, then the option and affinity terms are applied once
            // by getPower itself - calling the two-arg overload here as well would square both.
            double base = Math.min(ctx.properties().getRemainingPower(), perWindowCap);
            SpellDamageUtil.damageWithSpell(target, getPower(caster, target, base),
                    caster, wand, FaultLine.this);
        }
        hitThisInterval.clear();
    }

    /**
     * The lip is restored the moment the field ends - countered, cancelled, caster dead, caster
     * logged out, or the fault running out of length. It exists only to frame the trench and has
     * no life of its own, so it goes immediately rather than on a timer.
     */
    private void restoreLips(LivingEntity caster, Map<Location, Material> lipOriginals) {
        if (lipOriginals.isEmpty()) return;
        TerrainEditor.restoreAll(caster, new TerrainEditor.RestoreTicket(lipOriginals));
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
        ParticleCaster.spawn(caster, location.getWorld(), Utils.BLOCK, location, 25,
                Material.STONE.createBlockData());
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION, location, 2);
        ParticleCaster.spawn(null, location.getWorld(), Utils.LARGE_SMOKE, location, 35,
                0.7, 0.3, 0.7, 0.2);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.faultline.book_name"))
                .addCustomLoreLine(lang().get("spells.faultline.lore1"))
                .addCustomLoreLine(lang().get("spells.faultline.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", CIRCLE))
                .build();
    }
}
