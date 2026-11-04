package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.spells.vfx.FakeWaterVfx;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.effect.EffectApplicator;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class WaterSphere extends AttackSpell {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `WATER` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(47, 127, 212);

    /**
     * The single WATER palette. Every colour in this spell comes from here: the projectile head is
     * the leading edge and takes {@code core}, and the palette's own tail shading darkens behind
     * it. Nothing in this file may name an RGB value - five near-identical hand-picked blues is
     * exactly how the WATER element ended up looking like five different elements.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);

    /** Head-cluster density: one particle per this fraction of a block of radius. */
    private static final int CLUSTER_POINTS_PER_BLOCK = 32;
    /** Tail particles trailing behind the travelling head. */
    private static final int TAIL_POINTS = 10;
    /** Tail length as a multiple of the head cluster's radius. */
    private static final double TAIL_LENGTH_PER_RADIUS = 2.5;
    /** Cast-circle scale; this spell has always drawn a circle-1 sized glyph. */
    private static final double CIRCLE_SIZE = 2.0;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private double sphereRadius;
    private double sphereRange;

    public WaterSphere(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        splash(location, 0.8);
    }

    @Override
    public void onCountered(Location location) {
        splash(location, 1.0);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/water_sphere.yml");
        Alkatraz.getInstance().saveConfig("spells/water_sphere_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/water_sphere.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();
        this.sphereRadius = spellConfig.getDouble("sphere_radius", 3.0);
        this.sphereRange = spellConfig.getDouble("sphere_range", 25.0);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double radius = getModifiedStat(caster, "sphere_size", sphereRadius);
        double range = getModifiedStat(caster, "sphere_range", sphereRange);

        // RAW base power. getPower is applied once per impact, below.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster),
                AttackType.MAGIC
        );

        Location targetLoc = caster.getTargetBlock(null, (int) Math.ceil(range))
                .getLocation().add(0.5, 1, 0.5);

        shootSphere(caster, wand, props, targetLoc, radius, range);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;

        double wandp = getWandPowerOrDefault(wand);

        // RAW base power. getPower is applied once per impact, below.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * wandp,
                AttackType.MAGIC
        );

        Location targetLoc;
        if (caster.getTarget() != null) {
            targetLoc = caster.getTarget().getLocation();
        } else {
            targetLoc = caster.getEyeLocation()
                    .add(caster.getEyeLocation().getDirection().multiply(sphereRange));
        }

        shootSphere(caster, wand, props, targetLoc, sphereRadius, sphereRange);
    }

    private void shootSphere(LivingEntity caster, ItemStack wand, AttackProperties props,
                              Location targetLoc, double radius, double range) {
        if (caster == null || caster.getWorld() == null || targetLoc == null || targetLoc.getWorld() == null) return;
        List<Location> path = ParticleUtils.line(1, caster.getEyeLocation(), targetLoc);
        int totalSteps = path.size();

        // Head-cluster density scales with the radius the stat mods asked for.
        int clusterPoints = Math.max(6, (int) (radius * CLUSTER_POINTS_PER_BLOCK));

        // One travel direction for the whole flight. The path is a straight line, so a per-step
        // direction would only add noise to the tail.
        Vector travel = targetLoc.toVector().subtract(caster.getEyeLocation().toVector());
        if (travel.lengthSquared() < 1e-9) {
            travel = new Vector(0.0, 0.0, 1.0);
        } else {
            travel.normalize();
        }
        final Vector travelDir = travel;

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (caster.isDead() || !caster.isValid() || caster.getWorld() == null) {
                    cancel();
                    return;
                }
                if (props.isCancelled() || props.isCountered()) {
                    Location splashLoc = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(step));
                    if (splashLoc.getWorld() != null) splash(splashLoc, 0.6);
                    cancel();
                    return;
                }

                if (step >= totalSteps) {
                    if (targetLoc.getWorld() != null) {
                        splash(targetLoc, 1.0);
                        placeWater(caster, targetLoc);
                    }
                    cancel();
                    return;
                }

                Location center = path.get(step);
                if (center == null || center.getWorld() == null) { step++; return; }

                // A short client-side water flash gives the projectile a visible body even
                // when the viewer has particles disabled. It never edits the real world.
                if (step % 2 == 0) FakeWaterVfx.show(center, 3L);

                // Signature shape: a travelling cluster with a tail. The head is `core` (the
                // leading edge - what the eye tracks from the caster to the impact) and the tail
                // darkens toward `deep` behind it, so the orb reads as leaving, not as a blob.
                SpellVfx.projectile(CAST_COLOR, caster, center, travelDir,
                        radius, clusterPoints, TAIL_POINTS, radius * TAIL_LENGTH_PER_RADIUS);

                // Water spray around the head, so the orb reads as liquid rather than as dust.
                if (step % 2 == 0) {
                    ParticleCaster.spawn(caster, center.getWorld(), Utils.SPLASH, center, 3,
                            radius * 0.6, radius * 0.6, radius * 0.6, 0.05);
                }

                SpellParticleComponent comp = new SpellParticleComponent(
                        WaterSphere.this,
                        props,
                        caster,
                        wand,
                        SpellComponentType.OFFENSE,
                        center,
                        radius,
                        1
                );
                SpellComponentHandler.register(comp);


                // Water ambient sound
                if (step % 3 == 0) {
                    center.getWorld().playSound(center, Sound.BLOCK_WATER_AMBIENT, 0.3f, 1.2f);
                }

                // Damage and push entities in range
                for (Entity entity : center.getWorld().getNearbyEntities(center, radius + 0.5, radius + 0.5, radius + 0.5)) {
                    if (props.isCancelled() || props.isCountered()) {
                        cancel();
                        return;
                    }
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;
                    // Protection gate before damage.
                    if (!Protection.damage(le, caster, le.getLocation())) continue;
                    // One getPower per damage instance, deduped for the whole cast.
                    if (props.hasHit(le)) continue;
                    props.hit(le);

                    SpellDamageUtil.damageWithSpell(le, getPower(caster, le, props.getRemainingPower()),
                            caster, wand, WaterSphere.this);

                    Vector push = Utils.safeNormalize(le.getLocation().toVector()
                            .subtract(center.toVector()));
                    push.setY(Math.max(0.2, push.getY()));
                    le.setVelocity(push.multiply(0.8));

                    EffectApplicator.applyPotion(caster, le, new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOW, 30, 0, false, true
                    ), WaterSphere.this);
                }

                // Hydrate nearby farmland
                if (step % 5 == 0) {
                    for (Block b : Utils.blocksInRadius(center, (int) Math.ceil(radius))) {
                        if (b.getType() == Material.FARMLAND) {
                            if (b.getBlockData() instanceof Farmland farm) {
                                farm.setMoisture(farm.getMaximumMoisture());
                                if (Protection.blockEdit(caster, b.getLocation())) {
                                    b.setBlockData(farm);
                                }
                            }
                        }
                    }
                }

                step++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void splash(Location center, double sizeScale) {
        if (center == null) return;
        World world = center.getWorld();
        if (world == null) return;
        double r = sphereRadius * sizeScale;

        ParticleCaster.spawn(null, world, Utils.SPLASH, center, (int) (80 * sizeScale), r, r, r, 0.5);
        ParticleCaster.spawn(null, world, Utils.RAIN, center, (int) (120 * sizeScale), r, r, r, 1);
        ParticleCaster.spawn(null, world, Utils.BUBBLE_POP, center, (int) (40 * sizeScale), r, r, r, 0.3);
        world.playSound(center, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.5f, 0.8f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
    }

    private void placeWater(LivingEntity caster, Location center) {
        if (center == null || center.getWorld() == null) return;
        Block centerBlock = center.getBlock();
        if (centerBlock.getType() == Material.AIR) {
            if (Protection.blockEdit(caster, center)) {
                centerBlock.setType(Material.WATER);
                if (centerBlock.getBlockData() instanceof Levelled water) {
                    water.setLevel(0);
                    centerBlock.setBlockData(water);
                }
                Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
                    if (centerBlock.getType() == Material.WATER) {
                        if (Protection.blockEdit(caster, centerBlock.getLocation())) {
                            centerBlock.setType(Material.AIR);
                        }
                    }
                }, 100L);
            }
        }
        // Also hydrate all farmland within the sphere
        for (Block b : Utils.blocksInRadius(center, (int) Math.ceil(sphereRadius))) {
            if (b.getType() == Material.FARMLAND && b.getBlockData() instanceof Farmland farm) {
                farm.setMoisture(farm.getMaximumMoisture());
                if (Protection.blockEdit(caster, b.getLocation())) {
                    b.setBlockData(farm);
                }
            }
        }
    }

    @Override
    public boolean canMobCast(Mob mob) {
        return mob.getTarget() != null;
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        // Size 2 is what this spell has always drawn; CastCircle owns the layout and the colour.
        return CastCircle.start(this, caster, e, CIRCLE_SIZE, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.watersphere.book_name"))
                .addCustomLoreLine(lang().get("spells.watersphere.lore1"))
                .addCustomLoreLine(lang().get("spells.watersphere.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }
}
