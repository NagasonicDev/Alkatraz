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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class Whirlpool extends AttackSpell {

    private double radius;
    private double duration;
    private double damagePerTick;
    private double pullStrength;

    public Whirlpool(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/whirlpool_options.yml");
        Alkatraz.getInstance().save("spells/whirlpool.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/whirlpool.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.radius = spellConfig.getDouble("radius");
        this.duration = spellConfig.getDouble("duration");
        this.damagePerTick = spellConfig.getDouble("damage_per_tick");
        this.pullStrength = spellConfig.getDouble("pull_strength");
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double power = getPower(caster, getBasePower()) * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                power,
                AttackType.MAGIC
        );

        Location targetLoc = caster.getTargetBlock(null, 20).getLocation().add(0.5, 1, 0.5);
        double activeRadius = getModifiedStat(caster, "radius", radius);
        double activeDuration = getModifiedStat(caster, "duration", duration);
        double activeDamage = getModifiedStat(caster, "damage_per_tick", damagePerTick);
        double activePull = getModifiedStat(caster, "pull_strength", pullStrength);

        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_DROWNED_SWIM, 1.5f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || ticks >= activeDuration * 20) {
                    targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 0.6f);
                    cancel();
                    return;
                }

                for (int i = 0; i < 16; i++) {
                    double a = angle + (2 * Math.PI * i / 16);
                    double r = activeRadius * (1.0 - (ticks % 40) / 40.0);
                    double x = Math.cos(a) * r;
                    double z = Math.sin(a) * r;
                    double y = Math.sin(ticks * 0.2 + i) * 1.5 + 1.0;
                    Location particleLoc = targetLoc.clone().add(x, y, z);
                    particleLoc.getWorld().spawnParticle(Utils.DUST, particleLoc, 0,
                            new Particle.DustOptions(Color.fromRGB(30, 120, 200), 0.8F));

                    SpellParticleComponent comp = new SpellParticleComponent(
                            Whirlpool.this,
                            props,
                            caster,
                            wand,
                            SpellComponentType.OFFENSE,
                            particleLoc,
                            0.5,
                            4
                    );
                    SpellComponentHandler.register(comp);

                    if (i % 4 == 0) {
                        Location surfaceLoc = targetLoc.clone().add(x, 0, z);
                        surfaceLoc.getWorld().spawnParticle(Particle.WATER_SPLASH, surfaceLoc, 2, 0.3, 0.1, 0.3, 0);
                    }
                }

                if (ticks % 2 == 0) {
                    targetLoc.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, targetLoc, 8, activeRadius, 2, activeRadius, 0);
                }

                if (ticks % 3 == 0) {
                    targetLoc.getWorld().spawnParticle(Particle.WATER_SPLASH, targetLoc, 15, activeRadius, 0.5, activeRadius, 0);
                }

                for (Entity entity : targetLoc.getWorld().getNearbyEntities(targetLoc, activeRadius, activeRadius, activeRadius)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;

                    Vector pull = targetLoc.toVector().subtract(le.getLocation().toVector());
                    double dist = pull.length();
                    if (dist < 0.5) continue;

                    pull.normalize().multiply(activePull * (1.0 - dist / activeRadius));
                    pull.setY(0.2);
                    le.setVelocity(le.getVelocity().add(pull));

                    if (dist < 1.5) {
                        le.damage(activeDamage, caster);
                        if (Math.random() < 0.3) {
                            le.getWorld().spawnParticle(Particle.WATER_SPLASH, le.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
                        }
                    }
                }

                angle += 0.4;
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
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

        if (caster.getTarget() == null) return;
        Location targetLoc = caster.getTarget().getLocation();

        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_DROWNED_SWIM, 1.5f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || ticks >= duration * 20) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 6; i++) {
                    double a = angle + (2 * Math.PI * i / 6);
                    double r = radius * (1.0 - (ticks % 40) / 40.0);
                    double x = Math.cos(a) * r;
                    double z = Math.sin(a) * r;
                    double y = Math.sin(ticks * 0.2 + i) * 1.0 + 0.5;
                    Location particleLoc = targetLoc.clone().add(x, y, z);
                    particleLoc.getWorld().spawnParticle(Utils.DUST, particleLoc, 0,
                            new Particle.DustOptions(Color.fromRGB(30, 120, 200), 0.6F));
                }

                for (Entity entity : targetLoc.getWorld().getNearbyEntities(targetLoc, radius, radius, radius)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;

                    Vector pull = targetLoc.toVector().subtract(le.getLocation().toVector());
                    double dist = pull.length();
                    if (dist < 0.5) continue;

                    pull.normalize().multiply(pullStrength * (1.0 - dist / radius));
                    pull.setY(0.2);
                    le.setVelocity(le.getVelocity().add(pull));

                    if (dist < 1.5) {
                        le.damage(damagePerTick, caster);
                    }
                }

                angle += 0.4;
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        location.getWorld().spawnParticle(Particle.WATER_SPLASH, location, 30, 0.5, 0.5, 0.5, 0);
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.WATER_SPLASH, location, 50, 1, 1, 1, 0.5);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
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
                            new Particle.DustOptions(Color.fromRGB(30, 120, 200), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&9Tome of the Maelstrom")
                .addCustomLoreLine("&8&oThe depths rise to claim all.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }
}
