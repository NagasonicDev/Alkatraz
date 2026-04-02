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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Wind Vortex - Creates a pulling vortex that sucks enemies toward the caster
 * 
 * Behavior:
 * - Creates a cone-shaped vortex of swirling wind
 * - Pulls all enemies within radius toward caster
 * - Deals damage over time to caught enemies
 * - Applies levitation briefly to lift enemies
 * - Can be countered by other spells via SpellParticleComponent
 */
public class WindVortex extends AttackSpell implements Listener {
    
    // Configuration values
    private int vortexDuration;
    private double vortexRadius;
    private double pullStrength;
    private int tickRate;

    public WindVortex(String type) {
        super(type);
    }

    @Override
    protected void setupOptions() {
        // Pull Strength Option
        SpellOption pullOption = new SpellOption(this, "pull_strength",
                "Adjust vortex pull strength", Material.FISHING_ROD, 1);

        OptionValue<Double> gentlePull = new OptionValue<>(
                "gentle", "Gentle Pull", "Weaker pull, reduced damage",
                Material.STRING, 0.3
        );
        gentlePull.addImpact(new StatModifierImpact(this, "pull_strength", 0.15, StatModifierImpact.ModifierType.SET));
        gentlePull.addImpact(new ManaCostImpact(this, -10));
        pullOption.addValue(gentlePull);

        OptionValue<Double> normalPull = new OptionValue<>(
                "normal", "Normal Pull", "Standard pull strength",
                Material.FISHING_ROD, 0.4
        );
        normalPull.addImpact(new StatModifierImpact(this, "pull_strength", 0.25, StatModifierImpact.ModifierType.SET));
        pullOption.addValue(normalPull);

        OptionValue<Double> strongPull = new OptionValue<>(
                "strong", "Strong Pull", "Powerful pull, increased damage",
                Material.TRIPWIRE_HOOK, 0.6
        );
        strongPull.addRequirement(new NumberStatRequirement<>("circleLevel", 3, "Requires Circle Level 3"));
        strongPull.addImpact(new StatModifierImpact(this, "pull_strength", 0.4, StatModifierImpact.ModifierType.SET));
        strongPull.addImpact(new ManaCostImpact(this, 15));
        pullOption.addValue(strongPull);

        addOption(pullOption);

        // Radius Option
        SpellOption radiusOption = new SpellOption(this, "radius",
                "Adjust vortex size", Material.ENDER_EYE, 1);

        OptionValue<Double> smallVortex = new OptionValue<>(
                "small", "Small Vortex", "Smaller area, faster pull",
                Material.ENDER_PEARL, 5.0
        );
        smallVortex.addImpact(new StatModifierImpact(this, "radius", 5.0, StatModifierImpact.ModifierType.SET));
        smallVortex.addImpact(new StatModifierImpact(this, "pull_strength", 1.2, StatModifierImpact.ModifierType.MULTIPLY));
        smallVortex.addImpact(new ManaCostImpact(this, -10));
        radiusOption.addValue(smallVortex);

        OptionValue<Double> normalVortex = new OptionValue<>(
                "normal", "Normal Vortex", "Standard area",
                Material.ENDER_EYE, 8.0
        );
        normalVortex.addImpact(new StatModifierImpact(this, "radius", 8.0, StatModifierImpact.ModifierType.SET));
        radiusOption.addValue(normalVortex);

        OptionValue<Double> largeVortex = new OptionValue<>(
                "large", "Large Vortex", "Massive area, weaker pull",
                Material.END_CRYSTAL, 12.0
        );
        largeVortex.addRequirement(new NumberStatRequirement<>("circleLevel", 4, "Requires Circle Level 4"));
        largeVortex.addImpact(new StatModifierImpact(this, "radius", 12.0, StatModifierImpact.ModifierType.SET));
        largeVortex.addImpact(new StatModifierImpact(this, "pull_strength", 0.8, StatModifierImpact.ModifierType.MULTIPLY));
        largeVortex.addImpact(new ManaCostImpact(this, 20));
        radiusOption.addValue(largeVortex);

        addOption(radiusOption);

        // Duration Option
        SpellOption durationOption = new SpellOption(this, "duration",
                "Adjust vortex duration", Material.CLOCK, 1);

        OptionValue<Integer> shortVortex = new OptionValue<>(
                "brief", "Brief Vortex", "3 second duration",
                Material.CLOCK, 3
        );
        shortVortex.addImpact(new StatModifierImpact(this, "duration", 3, StatModifierImpact.ModifierType.SET));
        shortVortex.addImpact(new ManaCostImpact(this, -15));
        durationOption.addValue(shortVortex);

        OptionValue<Integer> normalDuration = new OptionValue<>(
                "normal", "Normal Vortex", "5 second duration",
                Material.CLOCK, 5
        );
        normalDuration.addImpact(new StatModifierImpact(this, "duration", 5, StatModifierImpact.ModifierType.SET));
        durationOption.addValue(normalDuration);

        OptionValue<Integer> extendedVortex = new OptionValue<>(
                "extended", "Extended Vortex", "8 second duration",
                Material.CLOCK, 8
        );
        extendedVortex.addRequirement(new NumberStatRequirement<>("circleLevel", 3, "Requires Circle Level 3"));
        extendedVortex.addImpact(new StatModifierImpact(this, "duration", 8, StatModifierImpact.ModifierType.SET));
        extendedVortex.addImpact(new ManaCostImpact(this, 20));
        durationOption.addValue(extendedVortex);

        addOption(durationOption);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, Player caster) {
        // Wind disperses on barrier
        location.getWorld().spawnParticle(Particle.CLOUD, location, 30, 1, 1, 1, 0.1);
        location.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location, 5);
    }

    @Override
    public void onCountered(Location location) {
        // Vortex disperses
        location.getWorld().spawnParticle(Particle.CLOUD, location, 50, 2, 2, 2, 0.2);
        location.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, location, 1);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/wind_vortex.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/wind_vortex.yml").get();
        loadCommonConfig(spellConfig);
        
        // Load vortex-specific config
        this.vortexDuration = spellConfig.getInt("vortex_duration");
        this.vortexRadius = spellConfig.getDouble("vortex_radius");
        this.pullStrength = spellConfig.getDouble("pull_strength");
        this.tickRate = spellConfig.getInt("tick_rate");
        
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        // Get modified stats
        double radius = getModifiedStat(caster, "radius", vortexRadius);
        double pull = getModifiedStat(caster, "pull_strength", pullStrength);
        int duration = (int) getModifiedStat(caster, "duration", vortexDuration);

        // Create attack properties
        double power = getPower(caster, getBasePower()) * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        AttackProperties props = new AttackProperties(
                caster,
                caster.getLocation(),
                power,
                AttackType.MAGIC
        );

        // Start vortex behavior
        VortexBehavior task = new VortexBehavior(caster, wand, props, radius, pull, duration, power);
        task.runTaskTimer(Alkatraz.getInstance(), 0L, tickRate);

        // Initial sound effect
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
    }

    @Override
    public int circleAction(Player p, PlayerSpellPrepareEvent e) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = p.getEyeLocation();
            float yaw = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();

            Vector forward = playerLoc.getDirection().normalize().multiply(1.5);
            List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 2, 0);

            // White/gray color for air
            for (int i = 0; i < 100; i++) {
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, 
                            new Particle.DustOptions(Color.fromRGB(220, 220, 220), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&fTome of Wind &oVol. 2")
                .addLoreLine("")
                .addLoreLine("&7Demonstrates the influence of wind on surroundings.")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }

    /**
     * Vortex behavior - pulls entities and deals damage
     */
    private class VortexBehavior extends BukkitRunnable {
        private final Player caster;
        private final ItemStack wand;
        private final AttackProperties props;
        private final double radius;
        private final double pullStrength;
        private final int maxDuration;
        
        private int ticksElapsed = 0;
        private double rotation = 0;

        public VortexBehavior(Player caster, ItemStack wand, AttackProperties props, 
                             double radius, double pullStrength, int duration, double damagePerTick) {
            this.caster = caster;
            this.wand = wand;
            this.props = props;
            this.radius = radius;
            this.pullStrength = pullStrength;
            this.maxDuration = duration * 20; // Convert to ticks
        }

        @Override
        public void run() {
            // Check if cancelled or countered
            if (props.isCancelled() || props.isCountered()) {
                cleanup();
                cancel();
                return;
            }

            // Check if caster is still valid
            if (caster.isDead() || !caster.isOnline()) {
                cleanup();
                cancel();
                return;
            }

            // Check duration
            ticksElapsed++;
            if (ticksElapsed >= maxDuration) {
                cleanup();
                cancel();
                return;
            }

            Location center = caster.getLocation();
            
            // Update rotation for animation
            rotation += 0.2;
            if (rotation >= Math.PI * 2) rotation -= Math.PI * 2;

            // Spawn vortex particles and register components
            spawnVortexParticles(center);
            pullEntities(center);

            // Sound effects every second
            if (ticksElapsed % 20 == 0) {
                center.getWorld().playSound(center, Sound.ENTITY_PHANTOM_FLAP, 0.5f, 1.2f);
            }
        }

        /**
         * Spawns the visual particles for the vortex and registers spell components
         */
        private void spawnVortexParticles(Location center) {
            // Main spiral - register each particle as a component for collision
            List<Location> spiralPoints = createVortexSpiral(center, radius, radius * 0.8, 3, rotation);
            for (Location loc : spiralPoints) {
                loc.getWorld().spawnParticle(Particle.CLOUD, loc, 1, 0, 0, 0, 0);
                
                // Register as spell particle component (like WaterPulse)
                SpellParticleComponent comp = new SpellParticleComponent(
                        WindVortex.this,
                        props,
                        caster,
                        wand,
                        SpellComponentType.OFFENSE,
                        loc,
                        0.5,  // collision radius
                        1     // lifetime (ticks)
                );
                SpellComponentHandler.register(comp);
            }
            
            // Rings at different heights (every 5 ticks)
            if (ticksElapsed % 5 == 0) {
                List<Location> ringPoints = createVortexRings(center, radius, radius * 0.6, 4, rotation);
                for (Location loc : ringPoints) {
                    loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 200, 200), 0.8f));
                }
            }

            // Ground ring (every 10 ticks)
            if (ticksElapsed % 10 == 0) {
                int particles = 40;
                for (int i = 0; i < particles; i++) {
                    double angle = ((double) i / particles) * Math.PI * 2 + rotation;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    
                    Location particleLoc = center.clone().add(x, 0.1, z);
                    particleLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 1);
                }
            }
        }

        /**
         * Creates a spiraling vortex effect
         */
        private List<Location> createVortexSpiral(Location center, double radius, double height, 
                                                 int density, double rotation) {
            List<Location> points = new ArrayList<>();
            int pointsPerSpiral = 50;
            
            for (int spiral = 0; spiral < density; spiral++) {
                double spiralOffset = (Math.PI * 2 * spiral) / density;
                
                for (int i = 0; i < pointsPerSpiral; i++) {
                    double progress = (double) i / pointsPerSpiral;
                    
                    // Height increases (cone shape)
                    double currentHeight = height * progress;
                    
                    // Radius decreases as we go up (cone shape)
                    double currentRadius = radius * (1 - progress * 0.7);
                    
                    // Angle includes rotation for animation
                    double angle = (progress * Math.PI * 4) + spiralOffset + rotation;
                    
                    // Calculate position
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    
                    points.add(center.clone().add(x, currentHeight, z));
                }
            }
            
            return points;
        }

        /**
         * Creates circular rings at different heights
         */
        private List<Location> createVortexRings(Location center, double radius, double height, 
                                                int rings, double rotation) {
            List<Location> points = new ArrayList<>();
            int pointsPerRing = 30;
            
            for (int ring = 0; ring < rings; ring++) {
                double ringProgress = (double) ring / rings;
                double ringHeight = height * ringProgress;
                double ringRadius = radius * (1 - ringProgress * 0.5);
                
                for (int i = 0; i < pointsPerRing; i++) {
                    double angle = ((double) i / pointsPerRing) * Math.PI * 2 + rotation;
                    
                    double x = Math.cos(angle) * ringRadius;
                    double z = Math.sin(angle) * ringRadius;
                    
                    points.add(center.clone().add(x, ringHeight, z));
                }
            }
            
            return points;
        }

        /**
         * Pulls entities toward the caste
         */
        private void pullEntities(Location center) {
            if (props.isCancelled() || props.isCountered()) {
                cleanup();
                cancel();
                return;
            }

            Collection<Entity> nearbyEntities = center.getWorld().getNearbyEntities(
                    center, radius, radius, radius
            );

            for (Entity entity : nearbyEntities) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (entity == caster) continue;
                if (entity instanceof Player && ((Player) entity).getGameMode() == GameMode.SPECTATOR) continue;

                double distance = entity.getLocation().distance(center);
                if (distance > radius) continue;

                // Calculate pull direction (toward caster's feet)
                Vector pullDirection = center.toVector()
                        .subtract(entity.getLocation().toVector())
                        .normalize();

                // Scale pull strength by distance (closer = stronger pull)
                double distanceFactor = 1.0 - (distance / radius);
                Vector pullVector = pullDirection.multiply(pullStrength * distanceFactor);

                // Apply velocity
                Vector currentVelocity = entity.getVelocity();
                entity.setVelocity(currentVelocity.add(pullVector));

                // Apply slight levitation to lift them off ground
                if (distance < radius * 0.5 && ticksElapsed % 40 == 0) {
                    living.addPotionEffect(new PotionEffect(
                            PotionEffectType.LEVITATION, 20, 0, false, false
                    ));
                }

                // Spawn particles around caught entities
                if (ticksElapsed % 3 == 0) {
                    spawnEntityWindEffect(entity.getLocation(), center);
                }
            }
        }

        /**
         * Spawns wind effect around entities
         */
        private void spawnEntityWindEffect(Location entityLoc, Location center) {
            int particles = 8;
            double entityRadius = 0.5;
            
            for (int i = 0; i < particles; i++) {
                double angle = ((double) i / particles) * Math.PI * 2 + rotation;
                double x = Math.cos(angle) * entityRadius;
                double z = Math.sin(angle) * entityRadius;
                
                Location particleLoc = entityLoc.clone().add(x, 1, z);
                particleLoc.getWorld().spawnParticle(Particle.CLOUD, particleLoc, 1, 0, 0, 0, 0);
            }
            
            // Pull line effect
            double distance = entityLoc.distance(center);
            if (distance > 2) {
                Vector direction = center.toVector().subtract(entityLoc.toVector());
                direction.normalize();
                
                int segments = 5;
                for (int i = 0; i < segments; i++) {
                    double progress = (double) i / segments;
                    Location lineLoc = entityLoc.clone().add(direction.clone().multiply(distance * progress));
                    lineLoc.getWorld().spawnParticle(Particle.REDSTONE, lineLoc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.WHITE, 0.5f));
                }
            }
        }

        /**
         * Cleanup when vortex ends
         */
        private void cleanup() {
            Location center = caster.getLocation();
            
            // Final particle burst
            center.getWorld().spawnParticle(Particle.CLOUD, center, 100, 
                    radius * 0.5, radius * 0.3, radius * 0.5, 0.1);
            center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 10, 
                    radius * 0.3, 0.5, radius * 0.3);
            
            // Sound effect
            center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 0.8f);
        }
    }
}
