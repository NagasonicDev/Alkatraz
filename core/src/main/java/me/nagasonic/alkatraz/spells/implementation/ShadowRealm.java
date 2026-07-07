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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class ShadowRealm extends Spell {
    private double radius;
    private double duration;
    private double damagePerTick;

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
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        Location targetLoc = caster.getTargetBlock(null, 30).getLocation().add(0.5, 0, 0.5);
        double activeRadius = getModifiedStat(caster, "radius", radius);
        double activeDamage = getModifiedStat(caster, "damage_per_tick", damagePerTick);
        World world = targetLoc.getWorld();

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
                            le, damagePerTick, caster, wand, ShadowRealm.this
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
                .setDisplayName("&5Necronomicon of the Void")
                .addCustomLoreLine("&8The abyss gazes back into you.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}
