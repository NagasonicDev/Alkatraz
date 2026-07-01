package me.nagasonic.alkatraz.spells.implementation;

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
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.properties.implementation.BarrierProperties;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import de.tr7zw.nbtapi.NBT;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class WindBarrier extends BarrierSpell implements Listener {

    public WindBarrier(String type) {
        super(type);
    }

    private double radius;
    private double duration;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/wind_barrier_options.yml");
        Alkatraz.getInstance().save("spells/wind_barrier.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/wind_barrier.yml").get();
        this.radius = spellConfig.getDouble("radius");
        this.duration = spellConfig.getDouble("duration");
        loadCommonConfig(spellConfig);
        loadOptions();
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        Location center = p.getLocation().clone().add(0, 1, 0);
        double activeRadius = (Double) getOption("barrier_size").getSelectedValue(p).getValue();
        double activeDuration = (Double) getOption("barrier_duration").getSelectedValue(p).getValue();
        double activeHitpoints = maxHitpoints;
        boolean followPlayer = (Double) getOption("follow_player").getSelectedValue(p).getValue() > 0;
        BarrierProperties properties = new BarrierProperties(p, center, activeHitpoints, BarrierType.COMBINED, activeRadius);
        properties.getHealthBar().addPlayer(p);

        new BukkitRunnable() {
            int ticksPassed = 0;

            @Override
            public void run() {
                if (followPlayer) {
                    Location newCenter = p.getLocation().clone().add(0, 1, 0);
                    center.setX(newCenter.getX());
                    center.setY(newCenter.getY());
                    center.setZ(newCenter.getZ());
                    properties.setCastLocation(center);
                }

                if (properties.isBroken() || ticksPassed >= activeDuration * 20 / 4) {
                    onBarrierBreak(center);
                    properties.getHealthBar().removeAll();
                    cancel();
                    return;
                }

                // Push nearby entities away (for PHYSICAL/COMBINED barriers)
                if (properties.getType() == BarrierType.PHYSICAL || properties.getType() == BarrierType.COMBINED) {
                    for (Entity entity : center.getWorld().getNearbyEntities(center, activeRadius, activeRadius, activeRadius)) {
                        if (!(entity instanceof LivingEntity living)) continue;
                        if (entity == p) continue;
                        if (NBT.getPersistentData(entity, nbt -> nbt.getBoolean("summoned_zombie"))) continue;

                        Vector push = entity.getLocation().toVector().subtract(center.toVector());
                        push.setY(0);
                        if (push.lengthSquared() < 0.01) continue;
                        push.normalize().multiply(0.3);
                        living.setVelocity(living.getVelocity().add(push));
                    }
                }

                List<Location> particleLocations = ParticleUtils.sphere(center, activeRadius, 100);

                for (Location loc : particleLocations) {
                    SpellParticleComponent particle = new SpellParticleComponent(
                            WindBarrier.this,
                            properties,
                            p,
                            wand,
                            SpellComponentType.DEFENSE,
                            loc,
                            0.8,
                            4
                    );
                    SpellComponentHandler.register(particle);

                    loc.getWorld().spawnParticle(Particle.CLOUD, loc, 1, 0, 0, 0, 0);
                    if (Math.random() < 0.1) {
                        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1);
                    }
                }

                ticksPassed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0, 4);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        Location center = caster.getLocation().clone().add(0, 1, 0);
        BarrierProperties properties = new BarrierProperties(caster, center, maxHitpoints, BarrierType.COMBINED, radius);

        new BukkitRunnable() {
            int ticksPassed = 0;

            @Override
            public void run() {
                if (properties.isBroken() || ticksPassed >= duration * 20 / 4) {
                    onBarrierBreak(center);
                    properties.getHealthBar().removeAll();
                    cancel();
                    return;
                }

                List<Location> particleLocations = ParticleUtils.sphere(center, radius, 100);

                for (Location loc : particleLocations) {
                    SpellParticleComponent particle = new SpellParticleComponent(
                            WindBarrier.this,
                            properties,
                            caster,
                            wand,
                            SpellComponentType.DEFENSE,
                            loc,
                            0.8,
                            4
                    );
                    SpellComponentHandler.register(particle);

                    loc.getWorld().spawnParticle(Particle.CLOUD, loc, 1, 0, 0, 0, 0);
                }

                ticksPassed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0, 4);
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
                .setDisplayName("&fAegis of the Zephyr")
                .addCustomLoreLine("&8&oThe wind shall shield thee.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }

    @Override
    public void onHit(double damage, AttackSpell source) {
    }

    @Override
    public void onBarrierBreak(Location center) {
        center.getWorld().spawnParticle(Particle.CLOUD, center, 50, radius, 1, radius, 0.2);
        center.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, center, 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
    }
}
