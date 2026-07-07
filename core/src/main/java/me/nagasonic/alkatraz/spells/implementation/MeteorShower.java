package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class MeteorShower extends AttackSpell {
    public MeteorShower(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/meteor_shower_options.yml");
        Alkatraz.getInstance().save("spells/meteor_shower.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/meteor_shower.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double totalPower = getPower(caster, getBasePower()) * getWandPower(wand);
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                totalPower,
                AttackType.MAGIC
        );

        World world = caster.getWorld();
        Location targetLoc = caster.getTargetBlock(null, 40).getLocation().add(0.5, 0, 0.5);
        double radius = getModifiedStat(caster, "radius", 8);
        int totalMeteors = 12;
        int durationTicks = 80;

        world.playSound(targetLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.6f);
        world.playSound(targetLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.3f);

        for (int i = 0; i < 30; i++) {
            double a = Math.random() * 2 * Math.PI;
            double r = Math.random() * radius * 1.5;
            Location glowLoc = targetLoc.clone().add(Math.cos(a) * r, Math.random() * 12 + 4, Math.sin(a) * r);
            glowLoc.getWorld().spawnParticle(Particle.FLAME, glowLoc, 1, 0, 0, 0, 0);
        }

        targetLoc.getWorld().spawnParticle(Utils.DUST, targetLoc, 0, new Particle.DustOptions(Color.fromRGB(255, 100, 0), 2.0F));

        new BukkitRunnable() {
            int ticksElapsed = 0;
            int meteorsLaunched = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered()) {
                    cancel();
                    return;
                }
                if (meteorsLaunched >= totalMeteors || ticksElapsed >= durationTicks) {
                    cancel();
                    return;
                }

                int meteorsThisTick = 0;
                if (ticksElapsed < 10) {
                    meteorsThisTick = 2;
                } else if (ticksElapsed < 50) {
                    meteorsThisTick = 1;
                } else if (ticksElapsed < 70) {
                    meteorsThisTick = 1;
                }

                for (int m = 0; m < meteorsThisTick && meteorsLaunched < totalMeteors; m++) {
                    double angle = Math.random() * 2 * Math.PI;
                    double r = Math.random() * radius;
                    double x = Math.cos(angle) * r;
                    double z = Math.sin(angle) * r;
                    double startY = targetLoc.getY() + 30 + Math.random() * 20;
                    Location spawnLoc = targetLoc.clone().add(x, 0, z);
                    spawnLoc.setY(startY);
                    Location impactLoc = targetLoc.clone().add(x, 0, z);
                    impactLoc.setY(targetLoc.getY());
                    Block below = impactLoc.getBlock();
                    while (below.getY() > targetLoc.getY() - 10 && below.getY() > -64 && below.isPassable()) {
                        impactLoc.subtract(0, 1, 0);
                        below = impactLoc.getBlock();
                    }
                    impactLoc.add(0, 1, 0);
                    double meteorHeight = spawnLoc.getY() - impactLoc.getY();

                    new BukkitRunnable() {
                        double progress = 0;
                        final double descentSpeed = 0.04;

                        @Override
                        public void run() {
                            double y = (1.0 - progress) * meteorHeight + 1.0;
                            Location currentLoc = impactLoc.clone().add(0, y, 0);

                            boolean hitBlock = !currentLoc.getBlock().isPassable();

                            if (hitBlock || progress >= 1.0) {
                                Location explodeLoc = hitBlock ? currentLoc : impactLoc;

                                explodeLoc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, explodeLoc, 0, 0, 0, 0, 0);
                                explodeLoc.getWorld().spawnParticle(Particle.LAVA, explodeLoc, 20, 1.5, 0.5, 1.5, 0);
                                explodeLoc.getWorld().spawnParticle(Particle.FLAME, explodeLoc, 40, 2, 1, 2, 0.05);
                                explodeLoc.getWorld().spawnParticle(Particle.SMOKE_LARGE, explodeLoc, 15, 2, 1, 2, 0);
                                explodeLoc.getWorld().spawnParticle(Utils.DUST, explodeLoc, 0, new Particle.DustOptions(Color.fromRGB(255, 150, 0), 1.5F));
                                explodeLoc.getWorld().playSound(explodeLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);

                                double impactRadius = 3.0;
                                for (double dy = -1; dy <= 1; dy++) {
                                    for (double dx = -impactRadius; dx <= impactRadius; dx += 0.5) {
                                        for (double dz = -impactRadius; dz <= impactRadius; dz += 0.5) {
                                            if (dx*dx + dz*dz + dy*dy > impactRadius*impactRadius) continue;
                                            Location fireLoc = explodeLoc.clone().add(dx, dy, dz);
                                            if (Math.random() < 0.15) {
                                                explodeLoc.getWorld().spawnParticle(Particle.FLAME, fireLoc, 1, 0.1, 0.1, 0.1, 0);
                                            }
                                        }
                                    }
                                }

                                double meteorPower = totalPower / (totalMeteors * 0.6);
                                for (LivingEntity le : Utils.getNearbyLivingEntities(explodeLoc, 4.0)) {
                                    if (le.equals(caster)) continue;
                                    SpellDamageUtil.damageWithSpell(
                                            le,
                                            getPower(caster, le, meteorPower),
                                            caster,
                                            wand,
                                            MeteorShower.this
                                    );
                                }

                                for (double dx = -2.5; dx <= 2.5; dx++) {
                                    for (double dz = -2.5; dz <= 2.5; dz++) {
                                        if (dx*dx + dz*dz > 2.5*2.5) continue;
                                        for (int dy = -1; dy <= 0; dy++) {
                                            Location blockLoc = explodeLoc.clone().add(dx, dy, dz);
                                            Block b = blockLoc.getBlock();
                                            if (b.getType().isSolid() && !b.getType().toString().contains("BEDROCK")) {
                                                b.getWorld().spawnParticle(Particle.BLOCK_CRACK, blockLoc.add(0.5, 0.5, 0.5),
                                                        8, 0.3, 0.3, 0.3, 0.3, b.getBlockData());
                                                b.breakNaturally();
                                            }
                                        }
                                    }
                                }
                                cancel();
                                return;
                            }

                            currentLoc.getWorld().spawnParticle(Particle.FLAME, currentLoc, 3, 0.3, 0.3, 0.3, 0.02);
                            currentLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, currentLoc, 2, 0.2, 0.2, 0.2, 0.01);
                            if (progress > 0.1) {
                                currentLoc.getWorld().spawnParticle(Particle.LAVA, currentLoc, 1, 0.1, 0.1, 0.1, 0);
                            }

                            if (Math.random() < 0.3) {
                                currentLoc.getWorld().playSound(currentLoc, Sound.ENTITY_BLAZE_SHOOT, 0.3f, 0.5f);
                            }

                            progress += descentSpeed;
                        }
                    }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);

                    meteorsLaunched++;
                }

                ticksElapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;

        double wandp = getWandPowerOrDefault(wand);
        double power = getPower(caster, getBasePower()) * wandp;
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                power,
                AttackType.MAGIC
        );

        Location targetLoc = caster.getTarget().getLocation();
        int totalMeteors = 6;

        new BukkitRunnable() {
            int meteorsLaunched = 0;
            int ticksElapsed = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || meteorsLaunched >= totalMeteors || ticksElapsed >= 60) {
                    cancel();
                    return;
                }

                if (ticksElapsed % 10 == 0) {
                    double angle = Math.random() * 2 * Math.PI;
                    double r = Math.random() * 5;
                    double x = Math.cos(angle) * r;
                    double z = Math.sin(angle) * r;
                    Location impactLoc = targetLoc.clone().add(x, 0, z);
                    impactLoc.getWorld().spawnParticle(Particle.FLAME, impactLoc, 10, 0.5, 0.5, 0.5, 0.05);
                    impactLoc.getWorld().spawnParticle(Particle.LAVA, impactLoc, 5, 0.5, 0.5, 0.5, 0);

                    for (LivingEntity le : Utils.getNearbyLivingEntities(impactLoc, 2.5)) {
                        if (le.equals(caster)) continue;
                        SpellDamageUtil.damageWithSpell(
                                le,
                                getPower(caster, le, power / totalMeteors),
                                caster,
                                wand,
                                MeteorShower.this
                        );
                    }
                    meteorsLaunched++;
                }
                ticksElapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        location.getWorld().spawnParticle(Particle.LAVA, location, 20, 0.5, 0.5, 0.5, 0);
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.SMOKE_LARGE, location, 30, 1, 1, 1, 0);
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
                            new Particle.DustOptions(Color.fromRGB(255, 69, 0), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&6Codex of Falling Stars")
                .addCustomLoreLine("&8The heavens answer the caller's will.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
