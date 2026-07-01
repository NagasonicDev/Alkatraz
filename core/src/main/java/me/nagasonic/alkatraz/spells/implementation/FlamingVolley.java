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
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class FlamingVolley extends AttackSpell implements Listener {

    private double arrowSpeed;
    private double arrowRange;
    private double arrowSpread;

    public FlamingVolley(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        location.getWorld().spawnParticle(Particle.FLAME, location, 15);
        location.getWorld().spawnParticle(Particle.LAVA, location, 5);
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.FLAME, location, 30);
        location.getWorld().spawnParticle(Particle.LAVA, location, 10);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/flaming_volley_options.yml");
        Alkatraz.getInstance().save("spells/flaming_volley.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/flaming_volley.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.arrowSpeed = spellConfig.getDouble("arrow_speed");
        this.arrowRange = spellConfig.getDouble("arrow_range");
        this.arrowSpread = spellConfig.getDouble("arrow_spread");
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        int arrowCount = (int) getModifiedStat(caster, "arrow_count",
                ((Number) getOption("arrow_count").getSelectedValue(caster).getValue()).intValue());
        double power = getPower(caster, getBasePower())
                * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        AttackProperties props = new AttackProperties(
                caster,
                caster.getEyeLocation(),
                power,
                AttackType.MAGIC
        );
        fireVolley(caster, wand, arrowCount, props);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        int arrowCount = 3;
        double wandp = wand == null ? 1 : NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        double power = getPower(caster, getBasePower()) * wandp;
        AttackProperties props = new AttackProperties(
                caster,
                caster.getEyeLocation(),
                power,
                AttackType.MAGIC
        );
        fireVolley(caster, wand, arrowCount, props);
    }

    private void fireVolley(LivingEntity caster, ItemStack wand, int count, AttackProperties props) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.8f);

        Vector aimDir;
        if (caster instanceof Mob m && m.getTarget() != null) {
            aimDir = m.getTarget().getLocation().toVector().subtract(m.getLocation().toVector()).normalize();
        } else {
            aimDir = caster.getEyeLocation().getDirection().normalize();
        }

        List<Double> offsets = new ArrayList<>();
        if (count == 1) {
            offsets.add(0.0);
        } else {
            double halfSpread = Math.toRadians(arrowSpread) / 2.0;
            double step = Math.toRadians(arrowSpread) / (count - 1);
            for (int i = 0; i < count; i++) {
                offsets.add(-halfSpread + step * i);
            }
        }

        for (double yawOffset : offsets) {
            Vector arrowDir = rotateAroundY(aimDir.clone(), yawOffset);
            launchArrow(caster, wand, props, arrowDir);
        }
    }

    private void launchArrow(LivingEntity caster, ItemStack wand, AttackProperties props, Vector direction) {
        new BukkitRunnable() {
            final Location position = caster.getEyeLocation().clone();
            final Vector step = direction.clone().multiply(arrowSpeed);
            double distanceTravelled = 0;
            final Set<UUID> hit = new HashSet<>();

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered()) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                position.add(step);
                distanceTravelled += arrowSpeed;

                if (distanceTravelled > arrowRange) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                if (!position.getBlock().getType().isAir()) {
                    spawnImpact(position, caster);
                    cancel();
                    return;
                }

                spawnArrowParticles(position);

                SpellParticleComponent comp = new SpellParticleComponent(
                        FlamingVolley.this,
                        props,
                        caster,
                        wand,
                        SpellComponentType.OFFENSE,
                        position.clone(),
                        0.5,
                        1
                );
                SpellComponentHandler.register(comp);

                for (Entity entity : position.getWorld().getNearbyEntities(position, 0.6, 0.6, 0.6)) {
                    if (!(entity instanceof LivingEntity target)) continue;
                    if (entity.equals(caster)) continue;
                    if (hit.contains(entity.getUniqueId())) continue;

                    hit.add(entity.getUniqueId());
                    double damage = getPower(caster, target, props.getRemainingPower());
                    target.damage(damage, caster);
                    target.setFireTicks(100);

                    spawnImpact(position, caster);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void spawnArrowParticles(Location center) {
        center.getWorld().spawnParticle(Particle.FLAME, center, 2, 0.05, 0.05, 0.05, 0.01);
        center.getWorld().spawnParticle(Particle.SMOKE_NORMAL, center, 1, 0.05, 0.05, 0.05, 0.01);
    }

    private void spawnDisperse(Location loc) {
        loc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc, 8, 0.3, 0.3, 0.3, 0.05);
    }

    private void spawnImpact(Location loc, LivingEntity caster) {
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 20, 0.5, 0.5, 0.5, 0.1);
        loc.getWorld().spawnParticle(Particle.LAVA, loc, 10, 0.3, 0.3, 0.3, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 1.2f);

        if (caster instanceof Player p
                && (boolean) getOption("fire_blocks").getSelectedValue(p).getValue()) {
            if (loc.getBlock().getType() == Material.AIR) {
                loc.getBlock().setType(Material.FIRE);
            }
        }
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
                            new Particle.DustOptions(Color.RED, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&cInferno Volley Codex")
                .addCustomLoreLine("&8&oA rain of fire upon thy enemies.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }

    private static Vector rotateAroundY(Vector v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = v.getX() * cos + v.getZ() * sin;
        double z = -v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }
}
