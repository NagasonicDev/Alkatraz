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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class ShadowRealm extends Spell {
    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private double radius;
    private double duration;
    private double damagePerTick;
    private int windUpDuration;

    public ShadowRealm(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/shadow_realm_options.yml");
        Alkatraz.getInstance().save("spells/shadow_realm.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/shadow_realm.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.radius = spellConfig.getDouble("radius");
        this.duration = spellConfig.getDouble("duration");
        this.damagePerTick = spellConfig.getDouble("damage_per_tick");
        this.windUpDuration = spellConfig.getInt("wind_up_duration", 2) * 20;
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        Location targetLoc = Utils.resolveTarget(caster, 30);
        double activeRadius = getModifiedStat(caster, "radius", radius);
        double activeDamage = getModifiedStat(caster, "damage_per_tick", damagePerTick);
        World world = targetLoc.getWorld();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpDuration) {
                    cancel();
                    launchShadowRealm(caster, wand, targetLoc, activeRadius, activeDamage, world);
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
                    double sphereRadius = phaseProgress * activeRadius * 0.6;

                    for (int i = 0; i < 20; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double b = Math.acos(2 * Math.random() - 1);
                        double r = Math.random() * sphereRadius;
                        double x = r * Math.sin(b) * Math.cos(a);
                        double y = r * Math.sin(b) * Math.sin(a);
                        double z = r * Math.cos(b);
                        Location sphereLoc = targetLoc.clone().add(x, y, z);
                        sphereLoc.getWorld().spawnParticle(Utils.DUST, sphereLoc, 0,
                                new Particle.DustOptions(Color.fromRGB(20, 0, 40), (float)(0.3 + phaseProgress * 0.4)));
                        if (Math.random() < 0.2) {
                            sphereLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, sphereLoc, 1, 0.1, 0.1, 0.1, 0);
                        }
                    }

                    for (int i = 0; i < 5; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = Math.random() * 1.5;
                        Location tendrilLoc = casterLoc.clone().add(Math.cos(a) * r, 0.5 + Math.random(), Math.sin(a) * r);
                        tendrilLoc.getWorld().spawnParticle(Particle.SPELL_WITCH, tendrilLoc, 1, 0.1, 0.1, 0.1, 0);
                    }

                    if (ticks % 15 == 0) {
                        world.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 0.3f + (float)(progress * 0.7f));
                    }
                } else {
                    double phaseProgress = (progress - 0.5) / 0.5;
                    double currentRadius = activeRadius * (0.3 + phaseProgress * 0.7);

                    for (int i = 0; i < 30; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double b = Math.acos(2 * Math.random() - 1);
                        double r = currentRadius * (0.7 + Math.random() * 0.3);
                        double x = r * Math.sin(b) * Math.cos(a);
                        double y = r * Math.sin(b) * Math.sin(a);
                        double z = r * Math.cos(b);
                        Location sphereLoc = targetLoc.clone().add(x, y, z);
                        sphereLoc.getWorld().spawnParticle(Utils.DUST, sphereLoc, 0,
                                new Particle.DustOptions(Color.fromRGB(40 + (int)(Math.random() * 20), 0, 60 + (int)(Math.random() * 20)), (float)(0.4 + phaseProgress * 0.3)));
                        if (Math.random() < 0.15) {
                            sphereLoc.getWorld().spawnParticle(Particle.DRAGON_BREATH, sphereLoc, 1, 0.1, 0.1, 0.1, 0, 0.0f);
                        }
                    }

                    for (int i = 0; i < 8; i++) {
                        double a = Math.random() * 2 * Math.PI;
                        double r = currentRadius * (0.5 + Math.random() * 0.5);
                        Location tendrilLoc = targetLoc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                        tendrilLoc.getWorld().spawnParticle(Particle.SPELL_WITCH, tendrilLoc, 1, 0.1, 0.1, 0.1, 0);
                        if (Math.random() < 0.3) {
                            tendrilLoc.getWorld().spawnParticle(Particle.PORTAL, tendrilLoc, 1, 0.1, 0.1, 0.1, 0.1);
                        }
                    }

                    if (ticks % 10 == 0) {
                        world.playSound(targetLoc, Sound.ENTITY_WITHER_AMBIENT, 0.5f, 0.4f + (float)(phaseProgress * 0.4f));
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void launchShadowRealm(Player caster, ItemStack wand, Location targetLoc, double activeRadius, double activeDamage, World world) {
        world.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.3f);
        world.playSound(targetLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.2f);

        List<Location> domePoints = ParticleUtils.fibonacciSphere(targetLoc, activeRadius, 200);
        List<Location> floorPoints = ParticleUtils.circle(targetLoc, activeRadius, 2, 0, 0);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    world.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
                    world.playSound(targetLoc, Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f);
                    cancel();
                    return;
                }

                for (Location loc : domePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                            new Particle.DustOptions(Color.fromRGB(40 + (int)(Math.random() * 30), 0, 60 + (int)(Math.random() * 40)), 0.5F));
                    if (Math.random() < 0.15) {
                        loc.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 1, 0.1, 0.1, 0.1, 0, 0.0f);
                    }
                }

                for (int i = 0; i < 12; i++) {
                    double a = angle + (2 * Math.PI * i / 12);
                    double r = activeRadius * (0.4 + (Math.sin(ticks * 0.03 + i) + 1) * 0.3);
                    double x = Math.cos(a) * r;
                    double z = Math.sin(a) * r;
                    for (double y = 0; y < activeRadius * 1.5; y += 0.8) {
                        double wallR = r * (1.0 - y / (activeRadius * 1.8));
                        double wx = Math.cos(a) * wallR;
                        double wz = Math.sin(a) * wallR;
                        Location wallLoc = targetLoc.clone().add(wx, y, wz);

                        wallLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, wallLoc, 2, 0.15, 0.15, 0.15, 0.01);
                        if (Math.random() < 0.1) {
                            wallLoc.getWorld().spawnParticle(Particle.SPELL_WITCH, wallLoc, 1, 0.1, 0.1, 0.1, 0);
                        }
                    }
                }

                for (Location floorLoc : floorPoints) {
                    floorLoc.getWorld().spawnParticle(Utils.DUST, floorLoc.clone().add(0, 0.1, 0), 0,
                            new Particle.DustOptions(Color.fromRGB(20, 0, 40), 0.6F));
                    if (Math.random() < 0.2) {
                        floorLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, floorLoc.clone().add(0, 0.1, 0), 1, 0.05, 0.05, 0.05, 0);
                    }
                }

                double ceilingY = activeRadius * 1.5;
                for (int i = 0; i < 8; i++) {
                    double a = Math.random() * 2 * Math.PI;
                    double r = Math.random() * activeRadius * 0.8;
                    Location ceilingLoc = targetLoc.clone().add(Math.cos(a) * r, ceilingY, Math.sin(a) * r);
                    ceilingLoc.getWorld().spawnParticle(Particle.DRAGON_BREATH, ceilingLoc, 2, 0.2, 0.1, 0.2, 0, 0.0f);
                    ceilingLoc.getWorld().spawnParticle(Utils.DUST, ceilingLoc, 0,
                            new Particle.DustOptions(Color.fromRGB(60, 0, 80), 0.4F));
                }

                for (int i = 0; i < 4; i++) {
                    double a = Math.random() * 2 * Math.PI;
                    double r = Math.random() * activeRadius * 0.6;
                    double y = Math.random() * activeRadius * 1.2;
                    Location innerLoc = targetLoc.clone().add(Math.cos(a) * r, y, Math.sin(a) * r);
                    innerLoc.getWorld().spawnParticle(Particle.PORTAL, innerLoc, 5, 0.3, 0.3, 0.3, 0.1);
                }

                for (Entity entity : world.getNearbyEntities(
                        targetLoc, activeRadius, activeRadius, activeRadius)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;

                    if (le instanceof Player targetPlayer) {
                        targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1, false, true));
                        targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2, false, true));
                        targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1, false, true));
                    } else {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2, false, true));
                        le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1, false, true));
                    }

                    le.getWorld().spawnParticle(Particle.SMOKE_LARGE, le.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0);

                    SpellDamageUtil.damageWithSpell(
                            le, activeDamage, caster, wand, ShadowRealm.this
                    );
                }

                if (ticks % 2 == 0) {
                    double a = Math.random() * 2 * Math.PI;
                    double r = activeRadius * 0.3;
                    Location pulseLoc = targetLoc.clone().add(Math.cos(a) * r, Math.random() * activeRadius, Math.sin(a) * r);
                    pulseLoc.getWorld().spawnParticle(Particle.DRAGON_BREATH, pulseLoc, 10, 0.5, 0.3, 0.5, 0, 0.0f);
                    pulseLoc.getWorld().spawnParticle(Utils.DUST, pulseLoc, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 0, 150), 1.0F));
                }

                angle += 0.4;
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 5L);
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
                    launchMobShadowRealm(caster, wand, targetLoc, wandp);
                    return;
                }

                if (caster.isDead()) {
                    cancel();
                    return;
                }

                double progress = (double) ticks / windUpDuration;
                double currentRadius = radius * (0.3 + progress * 0.7);

                for (int i = 0; i < 15; i++) {
                    double a = Math.random() * 2 * Math.PI;
                    double b = Math.acos(2 * Math.random() - 1);
                    double r = currentRadius * (0.7 + Math.random() * 0.3);
                    double x = r * Math.sin(b) * Math.cos(a);
                    double y = r * Math.sin(b) * Math.sin(a);
                    double z = r * Math.cos(b);
                    Location sphereLoc = targetLoc.clone().add(x, y, z);
                    sphereLoc.getWorld().spawnParticle(Utils.DUST, sphereLoc, 0,
                            new Particle.DustOptions(Color.fromRGB(30, 0, 50), 0.3F));
                    if (Math.random() < 0.1) {
                        sphereLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, sphereLoc, 1, 0.1, 0.1, 0.1, 0);
                    }
                }

                if (ticks % 15 == 0) {
                    targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 0.3f);
                }

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void launchMobShadowRealm(Mob caster, ItemStack wand, Location targetLoc, double wandp) {
        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.3f);
        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.2f);

        double power = damagePerTick * wandp;

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }

                List<Location> spherePoints = ParticleUtils.fibonacciSphere(targetLoc, radius, 80);
                for (Location loc : spherePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                            new Particle.DustOptions(Color.fromRGB(50, 0, 80), 0.4F));
                    if (Math.random() < 0.1) {
                        loc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc, 1, 0.1, 0.1, 0.1, 0);
                    }
                }

                for (Entity entity : targetLoc.getWorld().getNearbyEntities(
                        targetLoc, radius, radius, radius)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;

                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1, false, true));
                    SpellDamageUtil.damageWithSpell(
                            le, power, caster, wand, ShadowRealm.this
                    );
                }

                targetLoc.getWorld().spawnParticle(Particle.SMOKE_LARGE, targetLoc, 15, radius, 1, radius, 0);
                targetLoc.getWorld().spawnParticle(Particle.SPELL_WITCH, targetLoc, 5, radius, 1, radius, 0);

                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 20L);
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
                            new Particle.DustOptions(Color.fromRGB(80, 0, 120), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.shadowrealm.book_name"))
                .addCustomLoreLine(lang().get("spells.shadowrealm.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
