package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.ManaCostImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Air Blades - Immediately fires a fan of wind blades on cast.
 *
 * Behavior:
 * - Fires 1, 3, or 5 blades instantly depending on the chosen spell option.
 * - Each blade travels in a straight line, dealing damage to the first entity hit.
 * - Multiple blades are spread in an even horizontal fan around the player's aim direction.
 */
public class AirBlades extends AttackSpell implements Listener {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `AIR` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(234, 246, 255);

    private static final ElementPalette.Palette AIR = ElementPalette.of(Element.AIR);

    /** The blade's cross-section dust. Pale AIR core, so the leading edge is the brightest thing. */
    private static final Particle.DustOptions BLADE_DUST = AIR.coreDust();
    /** The thin trailing wisp behind a blade, from the palette rather than a literal RGB. */
    private static final Particle.DustOptions TRAIL_DUST = AIR.accentDust();

    /**
     * FAN GEOMETRY - the air_blades signature shape.
     *
     * <p>The volley is drawn as a {@link SpellVfx#cone} sector opening away from the caster:
     * a bright {@code core} rim on the leading edge the blades actually leave along, and a
     * {@code deep} interior fill so the rim stays legible as a boundary. The sector grows
     * outward over {@link #FAN_TICKS} ticks and then disappears, which is the whole draw.
     */
    private static final double FAN_TICK_PERIOD = 0.2;
    private static final int FAN_TICKS = 8;
    private static final double FAN_RADIUS = 3.2;
    private static final double FAN_START_SCALE = 0.35;
    private static final int FAN_ARC_STEPS = 12;
    private static final int FAN_RADIAL_STEPS = 4;
    /** Arc width in degrees when only one blade fires; it still reads as a narrow cone. */
    private static final double FAN_SOLO_ARC = 14.0;
    /** Arc width in degrees added on top of the configured spread, so the fan slightly overshoots. */
    private static final double FAN_ARC_PADDING = 8.0;

    // Config values
    private double bladeSpeed;
    private double bladeRange;
    private double bladeSpread;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public AirBlades(String type) {
        super(type);
    }

    // ============================================
    // Configuration
    // ============================================

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/air_blades_options.yml");
        Alkatraz.getInstance().save("spells/air_blades.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/air_blades.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();

        this.bladeSpeed    = spellConfig.getDouble("blade_speed");
        this.bladeRange    = spellConfig.getDouble("blade_range");
        this.bladeSpread   = spellConfig.getDouble("blade_spread");

        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    // ============================================
    // Cast logic — fires immediately
    // ============================================

    @Override
    public void castAction(Player caster, ItemStack wand) {
        int bladeCount = (int) getModifiedStat(caster, "blade_count",
                 (int) getOption("blade_count").getSelectedValue(caster).getValue());

        double size = getModifiedStat(caster, "blade_size", 0.6);
        // RAW base power. getPower is applied once per blade impact, below - the
        // old code scaled here AND again at impact, which squared the option
        // multipliers and the affinity term.
        AttackProperties props = new AttackProperties(
                caster,
                caster.getEyeLocation(),
                getBasePower() * getWandPower(wand, caster),
                AttackType.MAGIC
        );
        fireBlades(caster, wand, bladeCount, size, props);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        int bladeCount = 2;
        double wandp = getWandPowerOrDefault(wand);
        // RAW base power - see the player branch.
        AttackProperties props = new AttackProperties(
                caster,
                caster.getEyeLocation(),
                getBasePower() * wandp,
                AttackType.MAGIC
        );
        fireBlades(caster, wand, bladeCount, 0.6, props);
    }

    // ============================================
    // Blade firing
    // ============================================

    /**
     * Fires {@code count} blades spread evenly in a horizontal fan.
     * With 1 blade: no spread (fires straight).
     * With 3 or 5 blades: evenly distributed across {@code bladeSpread} degrees.
     */
    private void fireBlades(LivingEntity caster, ItemStack wand, int count, double size, AttackProperties props) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.9f, 1.6f);

        Vector aimDir;
        if (caster instanceof Mob m && m.getTarget() != null) {
            aimDir = Utils.safeNormalize(m.getTarget().getLocation().toVector().subtract(m.getLocation().toVector()));
        }else aimDir = caster.getEyeLocation().getDirection().normalize();

        // Build the list of yaw offsets (in radians) for this volley
        List<Double> offsets = new ArrayList<>();
        if (count == 1) {
            offsets.add(0.0);
        } else {
            double halfSpread = Math.toRadians(bladeSpread) / 2.0;
            double step = Math.toRadians(bladeSpread) / (count - 1);
            for (int i = 0; i < count; i++) {
                offsets.add(-halfSpread + step * i);
            }
        }

        for (double yawOffset : offsets) {
            // Rotate aimDir around the Y axis by yawOffset
            Vector bladeDir = rotateAroundY(aimDir.clone(), yawOffset);

            launchBlade(caster, wand, props, bladeDir, size);
        }

        spawnBladeFan(caster, aimDir, offsets.size());
    }

    /**
     * Draws the AIR signature shape: a cone sector opening along {@code aimDir}, showing the
     * angular sector the volley is about to sweep.
     *
     * <p>This is what separates air_blades from the rest of the element by shape rather than
     * by colour: AIR's palette is deliberately the palest in the game, so a pale puff alone
     * would be indistinguishable from swift's trail or air_burst's blast. A widening sector
     * with a bright rim is a fan, and only one AIR spell draws a fan.
     *
     * @param caster  the entity casting, for per-player particle density
     * @param aimDir  the direction the volley is centred on; never null
     * @param blades  how many blades are in the volley; one still gets a narrow cone
     */
    private void spawnBladeFan(LivingEntity caster, Vector aimDir, int blades) {
        double arc = (blades <= 1) ? FAN_SOLO_ARC : bladeSpread + FAN_ARC_PADDING;
        double centre = Math.toDegrees(Math.atan2(aimDir.getZ(), aimDir.getX()));
        Location apex = caster.getEyeLocation().clone().add(0.0, -0.3, 0.0);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= FAN_TICKS || caster.isDead()) {
                    cancel();
                    return;
                }
                double progress = (elapsed + 1) / (double) FAN_TICKS;
                double radius = FAN_RADIUS * (FAN_START_SCALE + (1.0 - FAN_START_SCALE) * progress);
                SpellVfx.cone(CAST_COLOR, caster, apex, radius,
                        arc, centre - (arc / 2.0), FAN_ARC_STEPS, FAN_RADIAL_STEPS);
                elapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, (long) (FAN_TICK_PERIOD * 20.0));
    }

    /**
     * Launches a single blade projectile along {@code direction}.
     */
    private void launchBlade(LivingEntity caster, ItemStack wand, AttackProperties props, Vector direction, double size) {
        new BukkitRunnable() {
            // Travel in small steps; each step is one tick
            final Location position = caster.getEyeLocation().clone();
            final Vector step       = direction.clone().multiply(bladeSpeed);
            double distanceTravelled = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered()) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                // Advance position
                position.add(step);
                distanceTravelled += bladeSpeed;

                // Stop if max range exceeded
                if (distanceTravelled > bladeRange) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                // Stop if the blade enters liquid or a solid block
                Block b = position.getBlock();
                if (b.isLiquid() || (!b.isPassable() && b.getType().isOccluding())) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                // ---- Visual: blade cross-section (two perpendicular lines of particles) ----
                spawnBladeParticles(position, direction, size);

                // ---- Register as an offensive spell component for the counter/barrier system ----
                SpellParticleComponent comp = new SpellParticleComponent(
                        AirBlades.this,
                        props,
                        caster,
                        wand,
                        SpellComponentType.OFFENSE,
                        position.clone(),
                        0.5,  // collision radius
                        1     // lifetime (ticks)
                );
                SpellComponentHandler.register(comp);

                // ---- Damage nearby entities ----
                for (Entity entity : position.getWorld().getNearbyEntities(position, 0.6, 0.6, 0.6)) {
                    if (!(entity instanceof LivingEntity target)) continue;
                    if (entity.equals(caster)) continue;
                    // One AttackProperties for the WHOLE volley, so a counter kills
                    // every blade - and so a target caught by two blades of the same
                    // fan is only damaged once. Without this guard the shared props
                    // made the fan hit a single target up to five times per cast.
                    if (props.hasHit(target)) continue;
                    props.hit(target);

                    double damage = getPower(caster, target, props.getRemainingPower());
                    SpellDamageUtil.damageWithSpell(target, damage, caster, wand, AirBlades.this);

                    // Hit sound & particles
                    ParticleCaster.spawn(caster, position.getWorld(), Utils.SWEEP_ATTACK, position, 3, 0.2, 0.2, 0.2, 0);
                    position.getWorld().playSound(position, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);

                    // One blade, one entity — stop after first hit
                    spawnDisperse(position);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    // ============================================
    // Particles
    // ============================================

    /**
     * Draws a blade silhouette: a short line of particles perpendicular to travel direction.
     */
    private void spawnBladeParticles(Location center, Vector direction, double size) {
        // Perpendicular vector in the horizontal plane
        Vector perp = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

        double span = size * 2;
        ParticleCaster.spawn(null, center.getWorld(),
                Utils.DUST, center, 16,
                perp.getX() * span, 0, perp.getZ() * span,
                0, BLADE_DUST
        );

        // A thin trailing wisp in the accent shade so the blade has a wake rather than
        // being a flat perpendicular line. Small dust, so it stays a hint, not a second blade.
        ParticleCaster.spawn(null, center.getWorld(),
                Utils.DUST, center, 6,
                -direction.getX() * size, 0, -direction.getZ() * size,
                0, TRAIL_DUST
        );
    }

    /**
     * Dispersal burst when a blade stops.
     */
    private void spawnDisperse(Location loc) {
        ParticleCaster.spawn(null, loc.getWorld(), Utils.CLOUD, loc, 8, 0.3, 0.3, 0.3, 0.05);
        ParticleCaster.spawn(null, loc.getWorld(), Utils.SWEEP_ATTACK, loc, 2, 0.2, 0.2, 0.2, 0);
    }

    // ============================================
    // Barrier & counter callbacks
    // ============================================

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.CLOUD, location, 20, 0.5, 0.5, 0.5, 0.1);
        ParticleCaster.spawn(caster, location.getWorld(), Utils.SWEEP_ATTACK, location, 4);
        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.6f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.CLOUD, location, 40, 1, 1, 1, 0.15);
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION, location, 1);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
    }

    // ============================================
    // Circle animation
    // ============================================

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    // ============================================
    // Spell book
    // ============================================

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.airblades.book_name"))
                .addCustomLoreLine(lang().get("spells.airblades.lore1"))
                .addCustomLoreLine(lang().get("spells.airblades.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }

    // ============================================
    // Utility
    // ============================================

    /**
     * Rotates a vector around the Y axis by the given angle (radians).
     */
    private static Vector rotateAroundY(Vector v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = v.getX() * cos + v.getZ() * sin;
        double z = -v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }
}
