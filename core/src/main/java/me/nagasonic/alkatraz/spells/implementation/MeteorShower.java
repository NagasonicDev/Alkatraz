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
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MeteorShower extends AttackSpell implements Listener {
    private static final Map<UUID, MeteorData> activeMeteorData = new ConcurrentHashMap<>();
    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private record MeteorData(Player caster, double totalPower, ItemStack wand) {}

    private int windUpDuration;

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
        this.windUpDuration = spellConfig.getInt("wind_up_duration", 6) * 20;
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @EventHandler
    private void onEntityExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof LargeFireball fb)) return;
        MeteorData data = activeMeteorData.remove(fb.getUniqueId());
        if (data == null) return;
        e.setCancelled(true);
        triggerMeteorExplosion(e.getLocation(), data.caster(), data.totalPower(), data.wand());
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
        Location targetLoc = Utils.resolveTarget(caster, 30);
        double radius = getModifiedStat(caster, "radius", 8);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchMeteorAttack(caster, wand, props, targetLoc, radius);
                    return;
                }

                if (caster.isDead() || (caster instanceof Player && !((Player) caster).isOnline())) {
                    cancel();
                    return;
                }

                Location casterLoc = caster.getLocation();
                double progress = (double) ticks / windUpDuration;
                boolean phase1 = progress < 0.67;

                if (phase1) {
                    double phaseProgress = progress / 0.67;
                    for (int ring = 0; ring < 3; ring++) {
                        double ringRadius = 1.5 + ring * 1.0 + phaseProgress * 2.0;
                        double ringHeight = phaseProgress * 3.0 + ring * 0.5;
                        int points = 12 + ring * 4;
                        for (int i = 0; i < points; i++) {
                            double angle = (2 * Math.PI * i / points) + (ticks * 0.05 * (ring + 1));
                            Location flameLoc = casterLoc.clone().add(
                                    Math.cos(angle) * ringRadius,
                                    ringHeight,
                                    Math.sin(angle) * ringRadius
                            );
                            flameLoc.getWorld().spawnParticle(Particle.FLAME, flameLoc, 2, 0.1, 0.1, 0.1, 0.02);
                        }
                    }

                    for (int i = 0; i < 8; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 2.5;
                        Location groundLoc = casterLoc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                        groundLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, groundLoc, 1, 0.2, 0.1, 0.2, 0.01);
                    }

                    if (ticks % 20 == 0) {
                        world.playSound(casterLoc, Sound.ENTITY_BLAZE_SHOOT, 0.4f, 0.5f + (float)(progress * 1.5f));
                    }
                } else {
                    double phaseProgress = (progress - 0.67) / 0.33;
                    double compressRadius = 3.0 * (1.0 - phaseProgress);
                    double columnHeight = 3.0 + phaseProgress * 8.0;

                    for (int i = 0; i < 20; i++) {
                        double angle = Math.random() * 2 * Math.PI;
                        double r = Math.random() * compressRadius;
                        Location colLoc = casterLoc.clone().add(
                                Math.cos(angle) * r,
                                columnHeight + Math.random() * 2,
                                Math.sin(angle) * r
                        );
                        colLoc.getWorld().spawnParticle(Particle.FLAME, colLoc, 2, 0.05, 0.05, 0.05, 0.03);
                    }

                    if (targetLoc != null) {
                        double markerRadius = 1.0 + phaseProgress * 3.0;
                        int markerPoints = 16;
                        for (int i = 0; i < markerPoints; i++) {
                            double angle = (2 * Math.PI * i / markerPoints) + (ticks * 0.1);
                            Location markerLoc = targetLoc.clone().add(
                                    Math.cos(angle) * markerRadius,
                                    0.1,
                                    Math.sin(angle) * markerRadius
                            );
                            markerLoc.getWorld().spawnParticle(Utils.DUST, markerLoc, 0,
                                    new Particle.DustOptions(Color.fromRGB(255, 100, 0), 1.0F));
                        }
                    }

                    if (ticks % 15 == 0) {
                        world.playSound(casterLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 0.4f);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void launchMeteorAttack(Player caster, ItemStack wand, AttackProperties props, Location targetLoc, double radius) {
        World world = caster.getWorld();
        double totalPower = getPower(caster, getBasePower()) * getWandPower(wand);
        int totalMeteors = 6;
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

        MeteorData meteorData = new MeteorData(caster, totalPower, wand);

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
                    Location impactLoc = targetLoc.clone().add(x, 0, z);
                    impactLoc.setY(targetLoc.getY());
                    Block below = impactLoc.getBlock();
                    while (below.getY() > targetLoc.getY() - 10 && below.getY() > -64 && below.isPassable()) {
                        impactLoc.subtract(0, 1, 0);
                        below = impactLoc.getBlock();
                    }
                    impactLoc.add(0, 1, 0);

                    Location spawnLoc = impactLoc.clone().add(0, 30 + Math.random() * 20, 0);

                    LargeFireball fireball = world.spawn(spawnLoc, LargeFireball.class);
                    Vector direction = Utils.safeNormalize(impactLoc.toVector().subtract(spawnLoc.toVector())).multiply(2);
                    fireball.setDirection(direction);
                    fireball.setYield(0);
                    fireball.setIsIncendiary(false);
                    fireball.setShooter(caster);

                    activeMeteorData.put(fireball.getUniqueId(), meteorData);

                    if (Math.random() < 0.3) {
                        world.playSound(spawnLoc, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.5f);
                    }

                    meteorsLaunched++;
                }

                ticksElapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void triggerMeteorExplosion(Location explodeLoc, Player caster, double totalPower, ItemStack wand) {
        World world = explodeLoc.getWorld();

        world.spawnParticle(Particle.EXPLOSION_HUGE, explodeLoc, 0, 0, 0, 0, 0);
        world.spawnParticle(Particle.LAVA, explodeLoc, 30, 2, 1, 2, 0);
        world.spawnParticle(Particle.FLAME, explodeLoc, 60, 3, 1.5, 3, 0.05);
        world.spawnParticle(Particle.SMOKE_LARGE, explodeLoc, 20, 2, 1, 2, 0);
        world.spawnParticle(Utils.DUST, explodeLoc, 0, new Particle.DustOptions(Color.fromRGB(255, 100, 0), 2.0F));
        world.playSound(explodeLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
        world.playSound(explodeLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.3f);

        for (LivingEntity le : Utils.getNearbyLivingEntities(explodeLoc, 5.0)) {
            if (le.equals(caster)) continue;
            SpellDamageUtil.damageWithSpell(
                    le,
                    getPower(caster, le, totalPower),
                    caster,
                    wand,
                    this
            );
        }

        int destroyRadius = 5;
        for (int dx = -destroyRadius; dx <= destroyRadius; dx++) {
            for (int dy = -destroyRadius; dy <= destroyRadius; dy++) {
                for (int dz = -destroyRadius; dz <= destroyRadius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > destroyRadius * destroyRadius) continue;
                    Location blockLoc = explodeLoc.clone().add(dx, dy, dz);
                    Block b = blockLoc.getBlock();
                    if (b.getType().isSolid() && !b.getType().toString().contains("BEDROCK")) {
                        b.getWorld().spawnParticle(Particle.BLOCK_CRACK, blockLoc.clone().add(0.5, 0.5, 0.5),
                                8, 0.3, 0.3, 0.3, 0.3, b.getBlockData());
                        if (Math.random() < 0.3) {
                            b.breakNaturally();
                        } else {
                            b.setType(Material.AIR);
                        }
                    }
                }
            }
        }

        for (int dx = -destroyRadius; dx <= destroyRadius; dx++) {
            for (int dy = -destroyRadius; dy <= destroyRadius; dy++) {
                for (int dz = -destroyRadius; dz <= destroyRadius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > destroyRadius * destroyRadius) continue;
                    Location blockLoc = explodeLoc.clone().add(dx, dy, dz);
                    Block b = blockLoc.getBlock();
                    if (b.isPassable() && b.getRelative(0, -1, 0).getType().isSolid()) {
                        b.setType(Material.FIRE);
                    }
                }
            }
        }
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
        World world = caster.getWorld();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchMobMeteorAttack(caster, wand, props, targetLoc);
                    return;
                }

                if (caster.isDead()) {
                    cancel();
                    return;
                }

                Location casterLoc = caster.getLocation();
                double progress = (double) ticks / windUpDuration;
                boolean phase1 = progress < 0.67;

                if (phase1) {
                    double phaseProgress = progress / 0.67;
                    for (int ring = 0; ring < 3; ring++) {
                        double ringRadius = 1.5 + ring * 1.0 + phaseProgress * 2.0;
                        double ringHeight = phaseProgress * 3.0 + ring * 0.5;
                        int points = 12 + ring * 4;
                        for (int i = 0; i < points; i++) {
                            double angle = (2 * Math.PI * i / points) + (ticks * 0.05 * (ring + 1));
                            Location flameLoc = casterLoc.clone().add(
                                    Math.cos(angle) * ringRadius,
                                    ringHeight,
                                    Math.sin(angle) * ringRadius
                            );
                            flameLoc.getWorld().spawnParticle(Particle.FLAME, flameLoc, 2, 0.1, 0.1, 0.1, 0.02);
                        }
                    }

                    if (ticks % 20 == 0) {
                        world.playSound(casterLoc, Sound.ENTITY_BLAZE_SHOOT, 0.4f, 0.5f + (float)(progress * 1.5f));
                    }
                } else {
                    double phaseProgress = (progress - 0.67) / 0.33;
                    double compressRadius = 3.0 * (1.0 - phaseProgress);
                    double columnHeight = 3.0 + phaseProgress * 8.0;

                    for (int i = 0; i < 20; i++) {
                        double angle = Math.random() * 2 * Math.PI;
                        double r = Math.random() * compressRadius;
                        Location colLoc = casterLoc.clone().add(
                                Math.cos(angle) * r,
                                columnHeight + Math.random() * 2,
                                Math.sin(angle) * r
                        );
                        colLoc.getWorld().spawnParticle(Particle.FLAME, colLoc, 2, 0.05, 0.05, 0.05, 0.03);
                    }

                    if (targetLoc != null) {
                        double markerRadius = 1.0 + phaseProgress * 3.0;
                        int markerPoints = 16;
                        for (int i = 0; i < markerPoints; i++) {
                            double angle = (2 * Math.PI * i / markerPoints) + (ticks * 0.1);
                            Location markerLoc = targetLoc.clone().add(
                                    Math.cos(angle) * markerRadius,
                                    0.1,
                                    Math.sin(angle) * markerRadius
                            );
                            markerLoc.getWorld().spawnParticle(Utils.DUST, markerLoc, 0,
                                    new Particle.DustOptions(Color.fromRGB(255, 100, 0), 1.0F));
                        }
                    }

                    if (ticks % 15 == 0) {
                        world.playSound(casterLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 0.4f);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void launchMobMeteorAttack(Mob caster, ItemStack wand, AttackProperties props, Location targetLoc) {
        double power = getPower(caster, getBasePower()) * getWandPowerOrDefault(wand);
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
                                getPower(caster, le, power),
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
                .setDisplayName(lang().get("spells.meteorshower.book_name"))
                .addCustomLoreLine(lang().get("spells.meteorshower.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
