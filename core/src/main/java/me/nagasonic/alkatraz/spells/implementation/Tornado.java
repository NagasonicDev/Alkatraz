package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.util.ColorFormat;
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

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private double radius;
    private double duration;
    private double damagePerTick;
    private double pullStrength;
    private double speed;
    private int windUpDuration;

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
        this.windUpDuration = spellConfig.getInt("wind_up_duration", 2) * 20;
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double activeRadius = getModifiedStat(caster, "radius", radius);
        double activeDamage = getModifiedStat(caster, "damage_per_tick", damagePerTick);
        double activePull = getModifiedStat(caster, "pull_strength", pullStrength);

        Location targetLoc = Utils.resolveTarget(caster, 30);
        World world = caster.getWorld();
        Location ground = Utils.findTopSolid(targetLoc, 30);
        if (ground == null) {
            ground = Utils.findTopSolid(caster.getLocation(), 30);
        }
        if (ground == null) {
            caster.sendMessage(lang().get("spells.tornado.no_ground"));
            return;
        }

        Location finalGround = ground;
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchTornado(caster, wand, activeRadius, activeDamage, activePull, finalGround, world);
                    return;
                }

                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    return;
                }

                Location casterLoc = caster.getLocation();
                double progress = (double) ticks / windUpDuration;
                boolean phase1 = progress < 0.5;

                if (phase1) {
                    double phaseProgress = progress / 0.5;
                    double spiralRadius = 1.0 + phaseProgress * 3.0;
                    double spiralHeight = phaseProgress * 2.0;

                    for (int i = 0; i < 3; i++) {
                        double angle = (2 * Math.PI * i / 3) + (ticks * 0.1);
                        for (int j = 0; j < 8; j++) {
                            double h = (j / 8.0) * spiralHeight;
                            double r = spiralRadius * (1.0 - h / (spiralHeight + 0.1));
                            Location spiralLoc = casterLoc.clone().add(
                                    Math.cos(angle + j * 0.4) * r,
                                    h,
                                    Math.sin(angle + j * 0.4) * r
                            );
                            spiralLoc.getWorld().spawnParticle(Particle.CLOUD, spiralLoc, 2, 0.1, 0.1, 0.1, 0);
                            if (Math.random() < 0.3) {
                                spiralLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, spiralLoc, 1, 0.05, 0.05, 0.05, 0);
                            }
                        }
                    }

                    for (int i = 0; i < 5; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 2.0;
                        Location airLoc = casterLoc.clone().add(Math.cos(a) * r, Math.random() * 1.5, Math.sin(a) * r);
                        airLoc.getWorld().spawnParticle(Particle.CLOUD, airLoc, 1, 0.1, 0.1, 0.1, 0);
                    }

                    if (ticks % 15 == 0) {
                        world.playSound(casterLoc, Sound.ENTITY_BLAZE_AMBIENT, 0.4f, 0.8f + (float)(progress * 0.7f));
                    }
                } else {
                    double phaseProgress = (progress - 0.5) / 0.5;
                    double tornadoGrowth = phaseProgress;
                    double currentRadius = activeRadius * 0.3 * (1.0 + tornadoGrowth * 3.0);

                    Location tornadoCenter = finalGround.clone().add(0.5, 1, 0.5);

                    for (int i = 0; i < 15; i++) {
                        double angle = Math.random() * 2 * Math.PI;
                        double r = Math.random() * currentRadius;
                        Location airLoc = casterLoc.clone().add(
                                Math.cos(angle) * r * 0.5,
                                1.0 + Math.random() * 1.5,
                                Math.sin(angle) * r * 0.5
                        );
                        airLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, airLoc, 1, 0.05, 0.05, 0.05, 0);

                        Vector toTornado = tornadoCenter.toVector().subtract(airLoc.toVector());
                        if (toTornado.length() > 0.1) {
                            Location streamLoc = airLoc.clone().add(toTornado.normalize().multiply(0.5));
                            streamLoc.getWorld().spawnParticle(Particle.CLOUD, streamLoc, 1, 0.05, 0.05, 0.05, 0);
                        }
                    }

                    for (int layer = 0; layer < 6; layer++) {
                        double t = layer / 5.0;
                        double y = t * 6.0 * tornadoGrowth;
                        double r = currentRadius * (0.4 + t * 0.6);
                        int points = 8 + layer * 2;
                        double baseAngle = ticks * 0.3 + t * 2.0;

                        for (int i = 0; i < points; i++) {
                            double angle = baseAngle + (2 * Math.PI * i / points);
                            double px = Math.cos(angle) * r;
                            double pz = Math.sin(angle) * r;
                            Location particleLoc = tornadoCenter.clone().add(px, y, pz);
                            particleLoc.getWorld().spawnParticle(Particle.CLOUD, particleLoc, 1, 0.05, 0.05, 0.05, 0.01);
                            if (i % 2 == 0 && Math.random() < 0.5) {
                                particleLoc.getWorld().spawnParticle(Utils.DUST, particleLoc, 0,
                                        new Particle.DustOptions(Color.fromRGB(200, 220, 255), (float)(0.2 + t * 0.3)));
                            }
                        }
                    }

                    if (ticks % 10 == 0) {
                        world.playSound(casterLoc, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.9f + (float)(phaseProgress * 0.5f));
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void launchTornado(Player caster, ItemStack wand, double activeRadius, double activeDamage, double activePull, Location ground, World world) {
        world.playSound(ground, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        world.playSound(ground, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.6f);

        final Location tornadoLoc = ground.add(0.5, 1, 0.5);
        final double finalSpeed = speed;
        final double finalDuration = duration;
        final double finalActiveRadius = activeRadius;
        final double finalActiveDamage = activeDamage;
        final double finalActivePull = activePull;
        final World finalWorld = world;

        new BukkitRunnable() {
            int ticks = 0;
            double moveAngle = Math.random() * 2 * Math.PI;
            int directionTicks = 0;

            @Override
            public void run() {
                if (ticks >= finalDuration * 20) {
                    finalWorld.playSound(tornadoLoc, Sound.ENTITY_BLAZE_DEATH, 1.0f, 1.2f);
                    cancel();
                    return;
                }

                directionTicks++;
                if (directionTicks > 20 + (int)(Math.random() * 40)) {
                    moveAngle = Math.random() * 2 * Math.PI;
                    directionTicks = 0;
                }
                moveAngle += (Math.random() - 0.5) * 0.6;

                double moveX = Math.cos(moveAngle) * finalSpeed;
                double moveZ = Math.sin(moveAngle) * finalSpeed;
                Vector movement = new Vector(moveX, 0, moveZ);

                if (ticks < 10) {
                    movement.multiply(ticks / 10.0);
                }

                Location nextLoc = tornadoLoc.clone().add(movement);
                Block nextBlock = nextLoc.getBlock();
                if (!nextBlock.isPassable() && !nextBlock.isLiquid()) {
                    Location stepUp = nextLoc.clone().add(0, 1, 0);
                    Block stepBlock = stepUp.getBlock();
                    if (stepBlock.isPassable()) {
                        nextLoc = stepUp;
                    } else {
                        moveAngle += Math.PI;
                        directionTicks = 0;
                        movement = movement.multiply(-1);
                    }
                }

                Location groundCheck = Utils.findTopSolid(nextLoc.clone().add(0, 3, 0), 6);
                if (groundCheck == null || Math.abs(groundCheck.getY() + 1 - nextLoc.getY()) > 4) {
                    Location bestCandidate = null;
                    double bestDist = Double.MAX_VALUE;
                    for (int dx = -3; dx <= 3; dx++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            Location candidate = tornadoLoc.clone().add(dx, 0, dz);
                            Location candidateGround = Utils.findTopSolid(candidate.clone().add(0, 3, 0), 6);
                            if (candidateGround != null) {
                                double groundY = candidateGround.getY() + 1;
                                double diff = Math.abs(groundY - candidate.getY());
                                if (diff <= 4) {
                                    double dist = dx * dx + dz * dz;
                                    if (dist < bestDist) {
                                        bestDist = dist;
                                        bestCandidate = candidate.clone();
                                        bestCandidate.setY(groundY);
                                    }
                                }
                            }
                        }
                    }
                    if (bestCandidate != null) {
                        Vector redirect = bestCandidate.toVector().subtract(tornadoLoc.toVector());
                        if (redirect.length() > 0.01) {
                            moveAngle = Math.atan2(redirect.getZ(), redirect.getX());
                            directionTicks = 0;
                        }
                    } else {
                        moveAngle += Math.PI;
                        directionTicks = 0;
                        movement = movement.multiply(-1);
                    }
                }

                tornadoLoc.add(movement);
                Location groundBelow = Utils.findTopSolid(tornadoLoc.clone().add(0, 5, 0), 20);
                if (groundBelow != null) {
                    double groundY = groundBelow.getY() + 1;
                    double diff = groundY - tornadoLoc.getY();
                    if (Math.abs(diff) <= 2.5) {
                        tornadoLoc.setY(groundY);
                    } else if (diff > 0) {
                        tornadoLoc.setY(tornadoLoc.getY() + Math.min(diff, 0.5));
                    } else {
                        tornadoLoc.setY(Math.max(tornadoLoc.getY() + Math.max(diff, -0.5), groundY));
                    }
                }

                double coneHeight = 12.0;
                double bottomRadius = finalActiveRadius * 0.3;
                double topRadius = finalActiveRadius * 1.2;

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
                    double r = finalActiveRadius * 0.8;
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
                        tornadoLoc, finalActiveRadius * 1.2, coneHeight, finalActiveRadius * 1.2)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;

                    Vector toCenter = tornadoLoc.toVector().subtract(le.getLocation().toVector());
                    toCenter.setY(0);
                    double dist = toCenter.length();
                    if (dist < 0.3) continue;

                    double strength = finalActivePull * (1.0 - Math.min(dist / (finalActiveRadius * 1.2), 0.9));
                    toCenter = Utils.safeNormalize(toCenter).multiply(strength);
                    double pullUp = strength * 0.8 + 0.3;
                    le.setVelocity(le.getVelocity().add(toCenter).setY(Math.max(le.getVelocity().getY() + pullUp * 0.15, 0.2)));

                    if (dist < finalActiveRadius * 0.5) {
                        SpellDamageUtil.damageWithSpell(
                                le, finalActiveDamage, caster, wand, Tornado.this
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
        double wandp = getWandPowerOrDefault(wand);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchMobTornado(caster, wand, targetLoc, wandp);
                    return;
                }

                if (caster.isDead()) {
                    cancel();
                    return;
                }

                Location casterLoc = caster.getLocation();
                double progress = (double) ticks / windUpDuration;

                double spiralRadius = 1.0 + progress * 2.0;
                double spiralHeight = progress * 2.0;

                for (int i = 0; i < 2; i++) {
                    double angle = (2 * Math.PI * i / 2) + (ticks * 0.1);
                    for (int j = 0; j < 6; j++) {
                        double h = (j / 6.0) * spiralHeight;
                        double r = spiralRadius * (1.0 - h / (spiralHeight + 0.1));
                        Location spiralLoc = casterLoc.clone().add(
                                Math.cos(angle + j * 0.5) * r,
                                h,
                                Math.sin(angle + j * 0.5) * r
                        );
                        spiralLoc.getWorld().spawnParticle(Particle.CLOUD, spiralLoc, 1, 0.1, 0.1, 0.1, 0);
                    }
                }

                if (ticks % 15 == 0) {
                    casterLoc.getWorld().playSound(casterLoc, Sound.ENTITY_BLAZE_AMBIENT, 0.4f, 0.8f);
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void launchMobTornado(Mob caster, ItemStack wand, Location targetLoc, double wandp) {
        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.6f);

        double power = damagePerTick * wandp;

        new BukkitRunnable() {
            int ticks = 0;
            Location tornadoLoc = targetLoc.clone().add(0, 0.5, 0);

            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }

                Vector toTarget = Utils.safeNormalize(caster.getLocation().toVector()
                        .subtract(tornadoLoc.toVector()).setY(0)).multiply(0.3);
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

                    pull = Utils.safeNormalize(pull).multiply(pullStrength * (1.0 - dist / radius));
                    pull.setY(0.4);
                    le.setVelocity(le.getVelocity().add(pull));

                    if (dist < 2.0) {
                        SpellDamageUtil.damageWithSpell(
                                le, power, caster, wand, Tornado.this
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
                .setDisplayName(lang().get("spells.tornado.book_name"))
                .addCustomLoreLine(lang().get("spells.tornado.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
