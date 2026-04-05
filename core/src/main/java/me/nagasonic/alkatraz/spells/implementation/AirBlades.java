package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.ManaCostImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
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
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Air Blades - Immediately fires a fan of wind blades on cast.
 *
 * Behavior:
 * - Fires 1, 3, or 5 blades instantly depending on the chosen spell option.
 * - Each blade travels in a straight line, dealing damage to the first entity hit.
 * - Multiple blades are spread in an even horizontal fan around the player's aim direction.
 */
public class AirBlades extends AttackSpell implements Listener {

    // Config values
    private double bladeSpeed;
    private double bladeRange;
    private double bladeSpread;

    public AirBlades(String type) {
        super(type);
    }

    // ============================================
    // Spell Options
    // ============================================

    @Override
    protected void setupOptions() {
        SpellOption bladeCountOption = new SpellOption(
                this, "blade_count",
                "Number of blades fired per click.",
                Material.FEATHER,
                1
        );

        // --- Single blade ---
        OptionValue<Integer> oneBlade = new OptionValue<>(
                "one", "Single Blade", "Fires one focused blade.",
                Material.FEATHER, 1
        );
        oneBlade.addImpact(new StatModifierImpact(this, "blade_count", 1, StatModifierImpact.ModifierType.SET));
        oneBlade.addImpact(new ManaCostImpact(this, -10));
        bladeCountOption.addValue(oneBlade);

        // --- Triple blade (default) ---
        OptionValue<Integer> threeBlades = new OptionValue<>(
                "three", "Triple Blades", "Fires three blades in a spread.",
                Material.FEATHER, 3
        );
        threeBlades.addImpact(new StatModifierImpact(this, "blade_count", 3, StatModifierImpact.ModifierType.SET));
        bladeCountOption.addValue(threeBlades);

        // --- Five blades (requires circle level 3) ---
        OptionValue<Integer> fiveBlades = new OptionValue<>(
                "five", "Five Blades", "Fires five blades in a wide spread.",
                Material.FEATHER, 5
        );
        fiveBlades.addRequirement(new NumberStatRequirement<>("circleLevel", 3, "Requires Circle Level 3"));
        fiveBlades.addImpact(new StatModifierImpact(this, "blade_count", 5, StatModifierImpact.ModifierType.SET));
        fiveBlades.addImpact(new ManaCostImpact(this, 15));
        bladeCountOption.addValue(fiveBlades);

        addOption(bladeCountOption);
    }

    // ============================================
    // Configuration
    // ============================================

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/air_blades.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/air_blades.yml").get();
        loadCommonConfig(spellConfig);

        this.bladeSpeed    = spellConfig.getDouble("blade_speed");
        this.bladeRange    = spellConfig.getDouble("blade_range");
        this.bladeSpread   = spellConfig.getDouble("blade_spread");

        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    // ============================================
    // Cast logic — fires immediately
    // ============================================

    @Override
    public void castAction(Player caster, ItemStack wand) {
        int bladeCount = (int) getModifiedStat(caster, "blade_count",
                (double) (int) getOption("blade_count").getSelectedValue(caster).getValue());

        double power = getPower(caster, getBasePower())
                * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));

        fireBlades(caster, wand, bladeCount, power);
    }

    // ============================================
    // Blade firing
    // ============================================

    /**
     * Fires {@code count} blades spread evenly in a horizontal fan.
     * With 1 blade: no spread (fires straight).
     * With 3 or 5 blades: evenly distributed across {@code bladeSpread} degrees.
     */
    private void fireBlades(Player caster, ItemStack wand, int count, double power) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.9f, 1.6f);

        Vector aimDir = caster.getEyeLocation().getDirection().normalize();

        // Build the list of yaw offsets (in radians) for this volley
        List<Double> offsets = new ArrayList<>();
        if (count == 1) {
            offsets.add(0.0);
        } else {
            double halfSpread = Math.toRadians(bladeSpread) / 2.0;
            double step = Math.toRadians(bladeSpread) / (count - 1);
            for (int i = 0; i < count; i++) {
                offsets.add(-halfSpread + step * i);
            }
        }

        for (double yawOffset : offsets) {
            // Rotate aimDir around the Y axis by yawOffset
            Vector bladeDir = rotateAroundY(aimDir.clone(), yawOffset);

            AttackProperties props = new AttackProperties(
                    caster,
                    caster.getEyeLocation(),
                    power,
                    AttackType.MAGIC
            );

            launchBlade(caster, wand, props, bladeDir);
        }
    }

    /**
     * Launches a single blade projectile along {@code direction}.
     */
    private void launchBlade(Player caster, ItemStack wand, AttackProperties props, Vector direction) {
        new BukkitRunnable() {
            // Travel in small steps; each step is one tick
            final Location position = caster.getEyeLocation().clone();
            final Vector step       = direction.clone().multiply(bladeSpeed);
            double distanceTravelled = 0;
            final Set<UUID> hit      = new HashSet<>();

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered()) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                // Advance position
                position.add(step);
                distanceTravelled += bladeSpeed;

                // Stop if max range exceeded
                if (distanceTravelled > bladeRange) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                // Stop if the blade enters a solid block
                if (!position.getBlock().getType().isAir()) {
                    spawnDisperse(position);
                    cancel();
                    return;
                }

                // ---- Visual: blade cross-section (two perpendicular lines of particles) ----
                spawnBladeParticles(position, direction);

                // ---- Register as an offensive spell component for the counter/barrier system ----
                SpellParticleComponent comp = new SpellParticleComponent(
                        AirBlades.this,
                        props,
                        caster,
                        wand,
                        SpellComponentType.OFFENSE,
                        position.clone(),
                        0.5,  // collision radius
                        1     // lifetime (ticks)
                );
                SpellComponentHandler.register(comp);

                // ---- Damage nearby entities ----
                for (Entity entity : position.getWorld().getNearbyEntities(position, 0.6, 0.6, 0.6)) {
                    if (!(entity instanceof LivingEntity target)) continue;
                    if (entity.equals(caster)) continue;
                    if (hit.contains(entity.getUniqueId())) continue;

                    hit.add(entity.getUniqueId());
                    double damage = getPower(caster, target, props.getRemainingPower());
                    target.damage(damage, caster);

                    // Hit sound & particles
                    position.getWorld().spawnParticle(Particle.SWEEP_ATTACK, position, 3, 0.2, 0.2, 0.2, 0);
                    position.getWorld().playSound(position, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);

                    // One blade, one entity — stop after first hit
                    spawnDisperse(position);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    // ============================================
    // Particles
    // ============================================

    /**
     * Draws a blade silhouette: a short line of particles perpendicular to travel direction.
     */
    private void spawnBladeParticles(Location center, Vector direction) {
        // Perpendicular vector in the horizontal plane
        Vector perp = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

        int steps = 5;
        double halfLen = 0.6; // half-length of the blade cross in blocks

        for (int i = -steps; i <= steps; i++) {
            double t = ((double) i / steps) * halfLen;
            Location loc = center.clone().add(perp.clone().multiply(t));
            loc.getWorld().spawnParticle(
                    Particle.REDSTONE, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(220, 235, 255), 0.7f)
            );
        }

        // Thin vertical sliver for the blade "edge"
        for (int i = -2; i <= 2; i++) {
            double t = ((double) i / 2) * 0.3;
            Location loc = center.clone().add(0, t, 0);
            loc.getWorld().spawnParticle(
                    Particle.REDSTONE, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.WHITE, 0.5f)
            );
        }
    }

    /**
     * Dispersal burst when a blade stops.
     */
    private void spawnDisperse(Location loc) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 8, 0.3, 0.3, 0.3, 0.05);
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 2, 0.2, 0.2, 0.2, 0);
    }

    // ============================================
    // Barrier & counter callbacks
    // ============================================

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, Player caster) {
        location.getWorld().spawnParticle(Particle.CLOUD, location, 20, 0.5, 0.5, 0.5, 0.1);
        location.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location, 4);
        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.6f);
    }

    @Override
    public void onCountered(Location location) {
        location.getWorld().spawnParticle(Particle.CLOUD, location, 40, 1, 1, 1, 0.15);
        location.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, location, 1);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
    }

    // ============================================
    // Circle animation
    // ============================================

    @Override
    public int circleAction(Player p, PlayerSpellPrepareEvent e) {
        return Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = p.getEyeLocation();
            float yaw   = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();
            Vector forward = playerLoc.getDirection().normalize().multiply(1.5);

            List<Location> circlePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 2, 0);

            for (int i = 0; i < 100; i++) {
                for (Location loc : circlePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                            new Particle.DustOptions(Color.fromRGB(210, 230, 255), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    // ============================================
    // Spell book
    // ============================================

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&fThe Iron Fan Treatise")
                .addCustomLoreLine("&8The discipline of the war-fan: a blade of wind")
                .addCustomLoreLine("&8for each spoke of the iron ribs.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }

    // ============================================
    // Utility
    // ============================================

    /**
     * Rotates a vector around the Y axis by the given angle (radians).
     */
    private static Vector rotateAroundY(Vector v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = v.getX() * cos + v.getZ() * sin;
        double z = -v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }
}
