package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
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

        double wandPower = NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        double power = getPower(caster, getBasePower()) * wandPower;
        double radius = getModifiedStat(caster, "sphere_size", sphereRadius);
        double range = getModifiedStat(caster, "sphere_range", sphereRange);

        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                power,
                AttackType.MAGIC
        );

        Location targetLoc = caster.getTargetBlock(null, (int) Math.ceil(range))
                .getLocation().add(0.5, 1, 0.5);

        shootSphere(caster, wand, props, targetLoc, radius, range);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;

        double wandp = wand == null ? 1 : NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        double power = getPower(caster, getBasePower()) * wandp;

        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                power,
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
        List<Location> path = ParticleUtils.line(1, caster.getEyeLocation(), targetLoc);
        int totalSteps = path.size();

        // Pre-compute fibonacci sphere points for even distribution
        int spherePoints = (int) (radius * 32);
        List<Location> baseSphere = ParticleUtils.fibonacciSphere(new Location(null, 0, 0, 0), radius, spherePoints);

        Set<UUID> damaged = new HashSet<>();

        new BukkitRunnable() {
            int step = 0;
            float rotation = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered()) {
                    splash(caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(step)), 0.6);
                    cancel();
                    return;
                }

                if (step >= totalSteps) {
                    splash(targetLoc, 1.0);
                    placeWater(targetLoc);
                    cancel();
                    return;
                }

                Location center = path.get(step);
                rotation += 0.15f;

                // Rotate sphere points for a swirling visual
                for (Location point : baseSphere) {
                    double x = point.getX();
                    double z = point.getZ();
                    double cos = Math.cos(rotation);
                    double sin = Math.sin(rotation);
                    double rx = x * cos - z * sin;
                    double rz = x * sin + z * cos;

                    Location worldLoc = center.clone().add(rx, point.getY(), rz);

                    // Blue glow ring on the outer edge
                    double dist = Math.sqrt(rx * rx + point.getY() * point.getY() + rz * rz);
                    if (dist > radius * 0.85) {
                        worldLoc.getWorld().spawnParticle(Utils.DUST, worldLoc, 0,
                                new Particle.DustOptions(Color.fromRGB(60, 180, 255), 0.5F));
                    }

                    // Core water particles - fewer toward the center
                    worldLoc.getWorld().spawnParticle(Particle.WATER_DROP, worldLoc, 1, 0, 0, 0, 0);
                    if (step % 2 == 0 && dist > radius * 0.4) {
                        worldLoc.getWorld().spawnParticle(Particle.WATER_SPLASH, worldLoc, 1,
                                0.05, 0.05, 0.05, 0);
                    }

                    SpellParticleComponent comp = new SpellParticleComponent(
                            WaterSphere.this,
                            props,
                            caster,
                            wand,
                            SpellComponentType.OFFENSE,
                            worldLoc,
                            0.5,
                            1
                    );
                    SpellComponentHandler.register(comp);
                }

                // Water ambient sound
                if (step % 3 == 0) {
                    center.getWorld().playSound(center, Sound.BLOCK_WATER_AMBIENT, 0.3f, 1.2f);
                }

                // Damage and push entities in range
                for (Entity entity : center.getNearbyEntities(radius + 0.5, radius + 0.5, radius + 0.5)) {
                    if (props.isCancelled() || props.isCountered()) {
                        cancel();
                        return;
                    }
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;
                    if (damaged.contains(entity.getUniqueId())) continue;
                    damaged.add(entity.getUniqueId());

                    le.damage(props.getRemainingPower(), caster);

                    Vector push = le.getLocation().toVector()
                            .subtract(center.toVector())
                            .normalize();
                    push.setY(Math.max(0.2, push.getY()));
                    le.setVelocity(push.multiply(0.8));

                    le.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOW, 30, 0, false, true
                    ));
                }

                // Hydrate nearby farmland
                for (Block b : Utils.blocksInRadius(center, (int) Math.ceil(radius))) {
                    if (b.getType() == Material.FARMLAND) {
                        if (b.getBlockData() instanceof Farmland farm) {
                            farm.setMoisture(farm.getMaximumMoisture());
                            b.setBlockData(farm);
                        }
                    }
                }

                step++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void splash(Location center, double sizeScale) {
        World world = center.getWorld();
        double r = sphereRadius * sizeScale;

        world.spawnParticle(Particle.WATER_SPLASH, center, (int) (80 * sizeScale), r, r, r, 0.5);
        world.spawnParticle(Particle.WATER_DROP, center, (int) (120 * sizeScale), r, r, r, 1);
        world.spawnParticle(Particle.BUBBLE_POP, center, (int) (40 * sizeScale), r, r, r, 0.3);
        world.playSound(center, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.5f, 0.8f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
    }

    private void placeWater(Location center) {
        Block centerBlock = center.getBlock();
        if (centerBlock.getType() == Material.AIR) {
            centerBlock.setType(Material.WATER);
            if (centerBlock.getBlockData() instanceof Levelled water) {
                water.setLevel(0);
                centerBlock.setBlockData(water);
            }
            Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
                if (centerBlock.getType() == Material.WATER) {
                    centerBlock.setType(Material.AIR);
                }
            }, 100L);
        }
        // Also hydrate all farmland within the sphere
        for (Block b : Utils.blocksInRadius(center, (int) Math.ceil(sphereRadius))) {
            if (b.getType() == Material.FARMLAND && b.getBlockData() instanceof Farmland farm) {
                farm.setMoisture(farm.getMaximumMoisture());
                b.setBlockData(farm);
            }
        }
    }

    @Override
    public boolean canMobCast(Mob mob) {
        return mob.getTarget() != null;
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = caster.getEyeLocation();
            float yaw = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();
            Vector forward = playerLoc.getDirection().normalize().multiply(1.5);
            List<Location> points = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 2, 0);
            for (Location loc : points) {
                loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                        new Particle.DustOptions(Color.fromRGB(60, 180, 255), 0.4F));
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&9Water Sutra &oVol. I")
                .addCustomLoreLine("&8The first part of a trilogy, revealing")
                .addCustomLoreLine("&8the basics of water magic.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }
}
