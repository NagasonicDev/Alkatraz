package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class Tornado extends Spell {
    private double radius;
    private double duration;
    private double damagePerTick;
    private double pullStrength;
    private double speed;

    public Tornado(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/tornado_options.yml");
        Alkatraz.getInstance().save("spells/tornado.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/tornado.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.radius = spellConfig.getDouble("radius");
        this.duration = spellConfig.getDouble("duration");
        this.damagePerTick = spellConfig.getDouble("damage_per_tick");
        this.pullStrength = spellConfig.getDouble("pull_strength");
        this.speed = spellConfig.getDouble("speed", 0.4);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double activeRadius = getModifiedStat(caster, "radius", radius);
        double activeDamage = getModifiedStat(caster, "damage_per_tick", damagePerTick);
        double activePull = getModifiedStat(caster, "pull_strength", pullStrength);

        Location targetLoc = caster.getTargetBlock(null, 30).getLocation().add(0.5, 0, 0.5);
        World world = caster.getWorld();

        world.playSound(targetLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        world.playSound(targetLoc, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.6f);

        new BukkitRunnable() {
            int ticks = 0;
            Location tornadoLoc = targetLoc.clone().add(0, 0.5, 0);
            float baseYaw = caster.getEyeLocation().getYaw();
            double driftAngle = 0;
            double driftOffset = 0;

            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    world.playSound(tornadoLoc, Sound.ENTITY_BLAZE_DEATH, 1.0f, 1.2f);
                    cancel();
                    return;
                }

                driftAngle += (Math.random() - 0.5) * 0.8;
                driftAngle = Math.max(-Math.PI / 3, Math.min(Math.PI / 3, driftAngle));
                driftOffset += (Math.random() - 0.5) * 0.6;

                double rad = Math.toRadians((double) baseYaw);
                Vector biasDir = new Vector(-Math.sin(rad), 0, Math.cos(rad)).normalize();

                double wanderX = Math.cos(driftAngle) * speed * 0.5;
                double wanderZ = Math.sin(driftOffset) * speed * 0.5;
                Vector wander = new Vector(wanderX, 0, wanderZ);
                Vector bias = biasDir.multiply(speed * 0.7);
                Vector movement = bias.add(wander);

                if (ticks < 10) {
                    movement.multiply(ticks / 10.0);
                }

                if (ticks > 5) {
                    Location nextLoc = tornadoLoc.clone().add(movement);
                    for (int h = 2; h <= 5; h++) {
                        Block check = nextLoc.getBlock().getRelative(0, h, 0);
                        if (!check.isPassable() && !check.isLiquid()) {
                            world.playSound(tornadoLoc, Sound.ENTITY_BLAZE_DEATH, 1.0f, 1.2f);
                            cancel();
                            return;
                        }
                    }
                }

                tornadoLoc.add(movement);

                double coneHeight = 12.0;
                double bottomRadius = activeRadius * 0.3;
                double topRadius = activeRadius * 1.2;

                for (int layer = 0; layer < 12; layer++) {
                    double t = layer / 11.0;
                    double y = t * coneHeight;
                    double r = bottomRadius + (topRadius - bottomRadius) * t;
                    int points = (int) (8 + t * 10);
                    double baseAngle = ticks * 0.4 + t * 2.0;

                    for (int i = 0; i < points; i++) {
                        double a = baseAngle + (2 * Math.PI * i / points);
                        double variance = 0.85 + Math.random() * 0.3;
                        double px = Math.cos(a) * r * variance;
                        double pz = Math.sin(a) * r * variance;
                        Location particleLoc = tornadoLoc.clone().add(px, y, pz);

                        particleLoc.getWorld().spawnParticle(Particle.CLOUD, particleLoc, 1, 0.05, 0.05, 0.05, 0.01);
                        if (i % 2 == 0) {
                            particleLoc.getWorld().spawnParticle(Utils.DUST, particleLoc, 0,
                                    new Particle.DustOptions(Color.fromRGB(180 + (int)(t * 75), 200, 230), (float)(0.3 + t * 0.5)));
                        }
                    }
                }

                for (int i = 0; i < 4; i++) {
                    double a = ticks * 0.5 + i * Math.PI / 2;
                    double r = activeRadius * 0.8;
                    double x = Math.cos(a) * r;
                    double z = Math.sin(a) * r;
                    for (double y = 0; y < coneHeight; y += 1.5) {
                        Location debrisLoc = tornadoLoc.clone().add(x, y, z);
                        if (Math.random() < 0.3) {
                            debrisLoc.getWorld().spawnParticle(Particle.FALLING_DUST, debrisLoc, 1, 0.1, 0.1, 0.1, 0,
                                    Material.CLAY.createBlockData());
                        }
                    }
                }

                tornadoLoc.getWorld().spawnParticle(Utils.DUST, tornadoLoc, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 220, 255), 1.5F));

                for (Entity entity : tornadoLoc.getWorld().getNearbyEntities(
                        tornadoLoc, activeRadius * 1.2, coneHeight, activeRadius * 1.2)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;

                    Vector toCenter = tornadoLoc.toVector().subtract(le.getLocation().toVector());
                    toCenter.setY(0);
                    double dist = toCenter.length();
                    if (dist < 0.3) continue;

                    double strength = activePull * (1.0 - Math.min(dist / (activeRadius * 1.2), 0.9));
                    toCenter.normalize().multiply(strength);
                    double pullUp = strength * 0.8 + 0.3;
                    le.setVelocity(le.getVelocity().add(toCenter).setY(Math.max(le.getVelocity().getY() + pullUp * 0.15, 0.2)));

                    if (dist < activeRadius * 0.5) {
                        SpellDamageUtil.damageWithSpell(
                                le, activeDamage, caster, wand, Tornado.this
                        );
                        le.getWorld().spawnParticle(Particle.CRIT, le.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0.2);
                    }

                    le.getWorld().spawnParticle(Particle.CLOUD, le.getLocation().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0);
                }

                for (double x = -0.5; x <= 0.5; x += 1.0) {
                    for (double z = -0.5; z <= 0.5; z += 1.0) {
                        Location baseLoc = tornadoLoc.clone().add(x, 0, z);
                        if (Math.random() < 0.4) {
                            baseLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, baseLoc, 1, 0.2, 0.05, 0.2, 0);
                        }
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;

        Location targetLoc = caster.getTarget().getLocation();

        new BukkitRunnable() {
            int ticks = 0;
            Location tornadoLoc = targetLoc.clone().add(0, 0.5, 0);

            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }

                Vector toTarget = caster.getTarget().getLocation().toVector()
                        .subtract(tornadoLoc.toVector()).setY(0).normalize().multiply(0.3);
                Vector rand = new Vector((Math.random() - 0.5) * 0.4, 0, (Math.random() - 0.5) * 0.4);
                tornadoLoc.add(toTarget.add(rand));

                double coneHeight = 10.0;
                for (int layer = 0; layer < 8; layer++) {
                    double t = layer / 7.0;
                    double y = t * coneHeight;
                    double r = radius * (0.3 + t * 0.9);
                    double a = ticks * 0.3 + t * 2.0;

                    for (int i = 0; i < 6; i++) {
                        double angle = a + (2 * Math.PI * i / 6);
                        double px = Math.cos(angle) * r;
                        double pz = Math.sin(angle) * r;
                        Location loc = tornadoLoc.clone().add(px, y, pz);
                        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 1, 0, 0, 0, 0);
                    }
                }

                for (Entity entity : tornadoLoc.getWorld().getNearbyEntities(
                        tornadoLoc, radius, coneHeight, radius)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;

                    Vector pull = tornadoLoc.toVector().subtract(le.getLocation().toVector());
                    double dist = pull.length();
                    if (dist < 0.3) continue;

                    pull.normalize().multiply(pullStrength * (1.0 - dist / radius));
                    pull.setY(0.4);
                    le.setVelocity(le.getVelocity().add(pull));

                    if (dist < 2.0) {
                        SpellDamageUtil.damageWithSpell(
                                le, damagePerTick, caster, wand, Tornado.this
                        );
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = caster.getEyeLocation();
            float yaw = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();
            Vector forward = playerLoc.getDirection().normalize().multiply(1.5);
            List<Location> points = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 3, 0);
            for (int i = 0; i < 100; i++) {
                for (Location loc : points) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 220, 255), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&bScroll of the Endless Gyre")
                .addCustomLoreLine("&8The winds spiral into an unstoppable force.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
