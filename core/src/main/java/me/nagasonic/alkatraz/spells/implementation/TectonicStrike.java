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

import java.util.List;

/**
 * Circle 6 EARTH burst: a focused slam at one point on the ground.
 *
 * <h2>The damage is a consequence, not the point</h2>
 * The telegraph writes <b>nothing</b>. The only world write in the entire cast is the single
 * {@link TerrainEditor#destroySphere} at the moment of impact, and the entity scan that deals
 * damage runs on that same tick, reading the epicentre the crater was just dug at. There is no
 * buff, no teleport, no summon - the player gets one crater and one shockwave and the ground is
 * permanently different afterwards.
 *
 * <h2>Damage contract (spec section 4)</h2>
 * <ul>
 *   <li>One {@code getPower} per damage instance: {@link SpellAttackContext#power(LivingEntity)}
 *       only, inside the impact tick. The two-arg cast-time form is never called, so the option
 *       multipliers and the affinity term are applied exactly once.</li>
 *   <li>Deduped per target per cast off the single shared {@link AttackProperties}.</li>
 *   <li>{@code power: 14.8} reaches {@link SpellDamageUtil#damageWithSpell} on every hit.</li>
 *   <li>This class extends {@link AttackSpell}.</li>
 *   <li>Exactly one {@link SpellAttackContext} per cast path, closed over by the runnable.</li>
 * </ul>
 *
 * <h2>Restoration</h2>
 * None, deliberately. The crater is paid for in real drops - {@code destroySphere} with
 * {@code dropItems = true} calls {@code breakNaturally()} - so putting the blocks back would
 * duplicate material that has already left the world as items.
 */
public class TectonicStrike extends AttackSpell {

    /**
     * Hot ember orange. Hoisted to a static so it is allocated once rather than per tick, and
     * deliberately NOT derived from {@code element}: the EARTH palette's browns are Fissure's and
     * Tremor's identity, and a slam needs to read as its own thing on screen.
     */
    private static final Color EMBER = Color.fromRGB(255, 122, 40);

    /** The impact flash. */
    private static final Particle.DustOptions SLAM_CORE = new Particle.DustOptions(EMBER, 0.9F);
    /** The closing warning ring. */
    private static final Particle.DustOptions TELEGRAPH_DUST = new Particle.DustOptions(EMBER, 0.5F);
    /** The cast circle. */
    private static final Particle.DustOptions CIRCLE_DUST = new Particle.DustOptions(EMBER, 0.4F);

    /** Block the cracks are drawn with during the telegraph. Visual only. */
    private static final Material CRACK_BLOCK = Material.STONE;

    /** The terrain read straight off the yml. Every one of these has an explicit default. */
    private int telegraphTicks = 15;
    private double craterRadius = 2.5;
    private int craterBlockCap = 120;
    private double blastRadius = 5.5;
    private double slamKnockback = 1.6;
    private double slamLift = 0.9;
    private double slamReach = 12.0;

    public TectonicStrike(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/tectonic_strike.yml");
        Alkatraz.getInstance().saveConfig("spells/tectonic_strike_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/tectonic_strike.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.telegraphTicks = Math.max(1, spellConfig.getInt("telegraph_ticks", 15));
        this.craterRadius = spellConfig.getDouble("crater_radius", 2.5);
        this.craterBlockCap = Math.max(1, spellConfig.getInt("crater_block_cap", 120));
        this.blastRadius = spellConfig.getDouble("blast_radius", 5.5);
        this.slamKnockback = spellConfig.getDouble("slam_knockback", 1.6);
        this.slamLift = spellConfig.getDouble("slam_lift", 0.9);
        this.slamReach = spellConfig.getDouble("slam_reach", 12.0);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        SpellAttackContext ctx = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);

        Location aimed = Utils.resolveTarget(caster, (int) Math.round(slamReach));
        if (aimed == null) {
            cancelCast(caster);
            return;
        }
        Location epicentre = Utils.findTopSolid(aimed.clone().add(0, 3, 0), 6);
        if (epicentre == null) epicentre = aimed;

        double radius = getModifiedStat(caster, "blast_radius", blastRadius);
        double crater = getModifiedStat(caster, "crater_radius", craterRadius);

        runTelegraph(caster, wand, ctx, epicentre, crater, radius, slamKnockback, slamLift);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;

        SpellAttackContext ctx = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);

        // A mob has no crosshair and no spell options, so the epicentre comes off its target and
        // the radii are the raw yml values rather than getModifiedStat. Everything after this -
        // telegraph length, block cap, velocity, particles, the hasHit guard - is identical.
        Location epicentre = Utils.findTopSolid(caster.getTarget().getLocation(), 8);
        if (epicentre == null) epicentre = caster.getLocation();

        runTelegraph(caster, wand, ctx, epicentre, craterRadius, blastRadius, slamKnockback, slamLift);
    }

    /**
     * Fifteen ticks of closing warning ring and rising cracks, then the slam. Nothing is written
     * to the world until the last possible cancel, so a countered or interrupted cast leaves no
     * partial terrain state to unwind.
     */
    private void runTelegraph(LivingEntity caster, ItemStack wand, SpellAttackContext ctx,
                              Location epicentre, double crater, double radius,
                              double knockback, double lift) {
        AttackProperties props = ctx.properties();
        World world = epicentre.getWorld();
        if (world == null) return;

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ctx.isSpent() || caster.isDead()
                        || (caster instanceof Player player && !player.isOnline())) {
                    cancel();
                    return;
                }
                if (ticks >= telegraphTicks) {
                    cancel();
                    slam(caster, wand, ctx, props, epicentre, crater, radius, knockback, lift);
                    return;
                }
                telegraph(caster, world, epicentre, ticks, telegraphTicks);
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * The warning phase: a ring that closes inward to nothing, four cracks growing along the
     * cardinals, and a rumble. <b>Particles and sound only.</b>
     */
    private void telegraph(LivingEntity caster, World world, Location epicentre, int ticks, int total) {
        double radius = 3.0 * (1.0 - (double) ticks / Math.max(1, total));
        for (Location point : ParticleUtils.circle(epicentre.clone().add(0, 0.05, 0), radius, 24,
                epicentre.getYaw(), 0)) {
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, TELEGRAPH_DUST);
        }

        double reach = ticks * 0.2;
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            double angle = quadrant * (Math.PI / 2.0);
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            for (double d = 0.1; d <= reach; d += 0.2) {
                Location crack = epicentre.clone().add(dx * d, 0.05, dz * d);
                ParticleCaster.spawn(caster, world, Utils.BLOCK_DUST, crack, 1,
                        CRACK_BLOCK.createBlockData());
            }
        }

        if (ticks % 5 == 0) {
            world.playSound(epicentre, Sound.BLOCK_STONE_BREAK, 0.4f,
                    0.6f + 0.4f * (ticks / (float) Math.max(1, total)));
        }
    }

    /**
     * Phase two, on a single tick: the crater, the shockwave and one hit on everything caught in
     * it. The {@code destroySphere} below is the ONLY block write in this spell.
     */
    private void slam(LivingEntity caster, ItemStack wand, SpellAttackContext ctx,
                      AttackProperties props, Location epicentre, double crater, double radius,
                      double knockback, double lift) {
        World world = epicentre.getWorld();
        if (world == null) return;
        ctx.tick();

        world.playSound(epicentre, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.5f);
        world.playSound(epicentre, Sound.BLOCK_STONE_BREAK, 1.2f, 0.6f);

        // Real drops, per-block Protection, hard maxBlocks cap. Never block.setType(Material.AIR):
        // that deletes blocks with zero drops, which is an item-loss bug, not a feature.
        TerrainEditor.destroySphere(caster, epicentre, crater, true, craterBlockCap);

        ParticleCaster.spawn(caster, world, Utils.EXPLOSION, epicentre.clone().add(0, 0.5, 0), 3);
        ParticleCaster.spawn(caster, world, Utils.LARGE_SMOKE, epicentre.clone().add(0, 0.5, 0),
                25, crater * 0.6, 0.3, crater * 0.6, 0.25);
        ParticleCaster.spawn(caster, world, Utils.BLOCK, epicentre.clone().add(0, 0.5, 0),
                40, crater * 0.5, 0.4, crater * 0.5, 0.4, Material.DIRT.createBlockData());
        for (Location point : ParticleUtils.circle(epicentre.clone().add(0, 0.05, 0), crater, 16,
                epicentre.getYaw(), 0)) {
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, SLAM_CORE);
        }

        for (LivingEntity target : Utils.getNearbyLivingEntities(epicentre, radius, radius, radius)) {
            if (target.equals(caster) || target.isDead()) continue;
            if (props.hasHit(target)) continue;
            props.hit(target);

            // A body standing exactly on the epicentre has a zero-length away vector, so this is
            // the null/zero-safe normalise: it leaves it at zero rather than producing NaN, and
            // the target still takes the upward component.
            Vector away = target.getLocation().toVector().subtract(epicentre.toVector()).setY(0);
            Utils.safeNormalizeInPlace(away);
            target.setVelocity(away.multiply(knockback).add(new Vector(0, lift, 0)));

            // The ONLY getPower call in this spell. props.hasHit above is what keeps
            // ctx.power(target) from being reached twice for one target in one tick.
            SpellDamageUtil.damageWithSpell(target, ctx.power(target), caster, wand, TectonicStrike.this);
        }
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
        ParticleCaster.spawn(caster, location.getWorld(), Utils.BLOCK_DUST, location, 20,
                CRACK_BLOCK.createBlockData());
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION, location, 1);
        ParticleCaster.spawn(null, location.getWorld(), Utils.LARGE_SMOKE, location, 30,
                0.6, 0.6, 0.6, 0.25);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.tectonicstrike.book_name"))
                .addCustomLoreLine(lang().get("spells.tectonicstrike.lore1"))
                .addCustomLoreLine(lang().get("spells.tectonicstrike.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
