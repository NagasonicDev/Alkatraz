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
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Radiance extends Spell {
    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private double healAmount;
    private double radius;
    private double duration;
    private int windUpDuration;

    private static final Set<EntityType> UNDEAD_TYPES = new HashSet<>();

    static {
        UNDEAD_TYPES.add(EntityType.ZOMBIE);
        UNDEAD_TYPES.add(EntityType.ZOMBIE_VILLAGER);
        UNDEAD_TYPES.add(EntityType.HUSK);
        UNDEAD_TYPES.add(EntityType.DROWNED);
        UNDEAD_TYPES.add(EntityType.SKELETON);
        UNDEAD_TYPES.add(EntityType.WITHER_SKELETON);
        UNDEAD_TYPES.add(EntityType.STRAY);
        UNDEAD_TYPES.add(EntityType.WITHER);
        UNDEAD_TYPES.add(EntityType.PHANTOM);
        UNDEAD_TYPES.add(EntityType.ZOGLIN);
        UNDEAD_TYPES.add(EntityType.ZOMBIFIED_PIGLIN);
        UNDEAD_TYPES.add(EntityType.SKELETON_HORSE);
        UNDEAD_TYPES.add(EntityType.ZOMBIE_HORSE);
    }

    public Radiance(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/radiance_options.yml");
        Alkatraz.getInstance().save("spells/radiance.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/radiance.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.healAmount = spellConfig.getDouble("heal_amount");
        this.radius = spellConfig.getDouble("radius");
        this.duration = spellConfig.getDouble("duration", 5.0);
        this.windUpDuration = spellConfig.getInt("wind_up_duration", 3) * 20;
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchRadiance(caster, wand);
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
                    double maxDistance = 6.0;
                    double currentDistance = maxDistance * (1.0 - phaseProgress);

                    for (int i = 0; i < 8; i++) {
                        double angle = (2 * Math.PI * i / 8) + (ticks * 0.05);
                        double x = Math.cos(angle) * currentDistance;
                        double z = Math.sin(angle) * currentDistance;
                        Location rayLoc = casterLoc.clone().add(x, 0.5 + Math.sin(ticks * 0.15 + i) * 1.0, z);
                        rayLoc.getWorld().spawnParticle(Particle.GLOW, rayLoc, 2, 0.1, 0.1, 0.1, 0);

                        Vector inward = casterLoc.toVector().subtract(rayLoc.toVector()).normalize().multiply(0.3);
                        Location trailLoc = rayLoc.clone().add(inward);
                        trailLoc.getWorld().spawnParticle(Particle.GLOW, trailLoc, 1, 0.05, 0.05, 0.05, 0);
                    }

                    for (int i = 0; i < 5; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 1.5;
                        Location hazeLoc = casterLoc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                        hazeLoc.getWorld().spawnParticle(Utils.DUST, hazeLoc, 1, 0.3, 0.1, 0.3, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 255, 200), 0.4F));
                    }

                    if (ticks % 20 == 0) {
                        casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f,
                                0.8f + (float)(phaseProgress * 0.7f));
                    }
                } else {
                    double phaseProgress = (progress - 0.5) / 0.5;
                    double density = 1.0 + phaseProgress * 3.0;

                    for (int i = 0; i < (int)(4 * density); i++) {
                        double angle = (2 * Math.PI * i / (int)(4 * density)) + (ticks * 0.08);
                        double distance = 2.0 * (1.0 - phaseProgress * 0.7);
                        double x = Math.cos(angle) * distance;
                        double z = Math.sin(angle) * distance;
                        Location convergeLoc = casterLoc.clone().add(x, 0.5 + Math.sin(ticks * 0.2 + i) * 1.0, z);
                        convergeLoc.getWorld().spawnParticle(Particle.GLOW, convergeLoc, 3, 0.05, 0.05, 0.05, 0);
                    }

                    for (int i = 0; i < (int)(3 * phaseProgress); i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 1.0;
                        Location sparkleLoc = casterLoc.clone().add(Math.cos(a) * r, Math.random() * 2, Math.sin(a) * r);
                        sparkleLoc.getWorld().spawnParticle(Particle.END_ROD, sparkleLoc, 1, 0.05, 0.05, 0.05, 0.02);
                    }

                    if (phaseProgress > 0.8) {
                        double burstIntensity = (phaseProgress - 0.8) / 0.2;
                        for (int i = 0; i < (int)(6 * burstIntensity); i++) {
                            double a = Math.random() * 2 * Math.PI;
                            double r = Math.random() * 0.5;
                            Location burstLoc = casterLoc.clone().add(Math.cos(a) * r, 1.0 + Math.random(), Math.sin(a) * r);
                            burstLoc.getWorld().spawnParticle(Particle.GLOW, burstLoc, 2, 0.1, 0.1, 0.1, 0.1);
                        }
                    }

                    if (ticks % 15 == 0) {
                        casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_BEACON_POWER_SELECT, 0.7f,
                                1.2f + (float)(phaseProgress * 0.5f));
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private boolean isUndead(LivingEntity entity) {
        return UNDEAD_TYPES.contains(entity.getType());
    }

    private void launchRadiance(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double activeRadius = getModifiedStat(caster, "radius", radius);
        double wandPower = getWandPower(wand);
        double healPerTick = getModifiedStat(caster, "heal", healAmount * wandPower) / duration;
        int totalTicks = (int) (duration * 20);

        World world = caster.getWorld();
        Location center = caster.getLocation().clone();

        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
        world.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);

        new BukkitRunnable() {
            int tick = 0;
            double ringAngle = 0;

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    center.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.5f);
                    cancel();
                    return;
                }

                ringAngle += 0.15;

                for (int ring = 0; ring < 3; ring++) {
                    double r = activeRadius * (ring + 1) / 3.0;
                    double angleOffset = ringAngle + ring * 2.0;
                    int points = 12 + ring * 4;

                    for (int i = 0; i < points; i++) {
                        double a = angleOffset + (2 * Math.PI * i / points);
                        double x = Math.cos(a) * r;
                        double z = Math.sin(a) * r;
                        Location ringLoc = center.clone().add(x, 0.5 + Math.sin(tick * 0.1 + ring) * 0.3, z);

                        ringLoc.getWorld().spawnParticle(Particle.GLOW, ringLoc, 1, 0, 0, 0, 0);
                        ringLoc.getWorld().spawnParticle(Utils.DUST, ringLoc, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 255, 200), 0.5F + ring * 0.2F));
                    }
                }

                for (int i = 0; i < 3; i++) {
                    double a = tick * 0.3 + i * 2.0;
                    double r = activeRadius * (0.3 + (Math.sin(tick * 0.05) * 0.5 + 0.5) * 0.7);
                    double x = Math.cos(a) * r;
                    double z = Math.sin(a) * r;
                    Location orbLoc = center.clone().add(x, 1.0 + Math.sin(tick * 0.2 + i) * 1.5, z);
                    orbLoc.getWorld().spawnParticle(Particle.END_ROD, orbLoc, 2, 0.1, 0.1, 0.1, 0.02);
                }

                for (int i = 0; i < 5; i++) {
                    double a = Math.random() * 2 * Math.PI;
                    double r = Math.random() * activeRadius;
                    double x = Math.cos(a) * r;
                    double z = Math.sin(a) * r;
                    Location radiantLoc = center.clone().add(x, Math.random() * 3 + 0.5, z);
                    radiantLoc.getWorld().spawnParticle(Particle.GLOW, radiantLoc, 1, 0.05, 0.05, 0.05, 0);
                }

                for (Entity entity : world.getNearbyEntities(center, activeRadius, activeRadius, activeRadius)) {
                    if (entity.equals(caster)) {
                        if (tick % 10 == 0) {
                            double maxHealth = caster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                            caster.setHealth(Math.min(maxHealth, caster.getHealth() + healPerTick));

                            Location eyes = caster.getEyeLocation();
                            eyes.getWorld().spawnParticle(Particle.HEART, eyes.clone().add(0, 0.5, 0), 2, 0.3, 0.3, 0.3, 0);
                        }
                        continue;
                    }

                    if (!(entity instanceof LivingEntity le)) continue;

                    if (isUndead(le)) {
                        if (tick % 10 == 0) {
                            SpellDamageUtil.damageWithSpell(
                                    le, calcPower(healPerTick, le, caster),
                                    caster, wand, Radiance.this
                            );
                            le.getWorld().spawnParticle(Particle.FLAME, le.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
                        }
                    } else if (entity instanceof Player targetPlayer) {
                        if (tick % 10 == 0) {
                            double maxHealth = targetPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                            targetPlayer.setHealth(Math.min(maxHealth, targetPlayer.getHealth() + healPerTick));
                            targetPlayer.getWorld().spawnParticle(Particle.HEART, targetPlayer.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
                        }
                    }
                }

                for (int i = 0; i < 2; i++) {
                    float yaw = (float) (Math.random() * 360);
                    float pitch = (float) (Math.random() * 360);
                    Vector forward = new Vector(0, 0, 1);
                    List<Location> circlePoints = ParticleUtils.magicCircle(
                            center.clone().add(0, 0.5 + Math.random() * 2, 0),
                            yaw, pitch, forward, 1.0 + Math.random() * 0.5, 0);
                    for (Location loc : circlePoints) {
                        if (Math.random() < 0.3) {
                            loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                                    new Particle.DustOptions(Color.YELLOW, 0.3F));
                        }
                    }
                }

                tick++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;

        double totalHeal = healAmount * getWandPowerOrDefault(wand);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchMobRadiance(caster, totalHeal);
                    return;
                }

                if (caster.isDead()) {
                    cancel();
                    return;
                }

                Location casterLoc = caster.getLocation();
                double progress = (double) ticks / windUpDuration;

                for (int i = 0; i < 4; i++) {
                    double angle = (2 * Math.PI * i / 4) + (ticks * 0.05);
                    double distance = 3.0 * (1.0 - progress);
                    double x = Math.cos(angle) * distance;
                    double z = Math.sin(angle) * distance;
                    Location rayLoc = casterLoc.clone().add(x, 0.5, z);
                    rayLoc.getWorld().spawnParticle(Particle.GLOW, rayLoc, 2, 0.1, 0.1, 0.1, 0);
                }

                if (ticks % 20 == 0) {
                    casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 0.8f);
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void launchMobRadiance(Mob caster, double totalHeal) {
        if (caster.isDead()) return;

        new BukkitRunnable() {
            int healTicks = 0;

            @Override
            public void run() {
                if (healTicks >= (int)(duration * 20)) {
                    cancel();
                    return;
                }

                if (healTicks % 10 == 0) {
                    double healPerTick = totalHeal / duration;
                    double maxHealth = caster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    caster.setHealth(Math.min(maxHealth, caster.getHealth() + healPerTick));
                    caster.getWorld().spawnParticle(Particle.GLOW, caster.getLocation(), 10, 2, 1, 2, 0);
                }

                healTicks++;
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
                            new Particle.DustOptions(Color.fromRGB(255, 255, 150), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.radiance.book_name"))
                .addCustomLoreLine(lang().get("spells.radiance.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
