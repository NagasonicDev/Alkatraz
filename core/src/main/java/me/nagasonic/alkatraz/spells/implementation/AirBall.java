package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellEntityComponent;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.ManaCostImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Air Ball - Creates a rideable sphere of air like in Avatar: The Last Airbender
 * 
 * Behavior:
 * - Spawns invisible horse that player rides
 * - Player can move with WASD and jump
 * - Jump while riding boosts upward with air burst
 * - Drains mana over time
 * - Disperses if horse takes damage (including fall damage)
 * - Prevents fall damage to player
 * - Sphere of particle effects around player
 */
public class AirBall extends Spell implements Listener {
    
    // Configuration values
    private double movementSpeed;
    private double jumpBoostStrength;
    private double manaDrainPerSecond;
    private double basePower;

    // Active air balls tracking
    private static final Map<UUID, AirBallInstance> activeAirBalls = new HashMap<>();

    public AirBall(String type) {
        super(type);
    }

    @Override
    protected void setupOptions() {
        // Speed Option
        SpellOption speedOption = new SpellOption(this, "speed",
                "Adjust air ball movement speed", Material.FEATHER, 1);

        OptionValue<Double> slowSpeed = new OptionValue<>(
                "slow", "Stable Glide", "Slower, easier control",
                Material.SOUL_SAND, 0.2
        );
        slowSpeed.addImpact(new StatModifierImpact(this, "movement_speed", 0.2, StatModifierImpact.ModifierType.SET));
        slowSpeed.addImpact(new ManaCostImpact(this, -10));
        speedOption.addValue(slowSpeed);

        OptionValue<Double> normalSpeed = new OptionValue<>(
                "normal", "Balanced Glide", "Standard speed",
                Material.FEATHER, 0.3
        );
        normalSpeed.addImpact(new StatModifierImpact(this, "movement_speed", 0.3, StatModifierImpact.ModifierType.SET));
        speedOption.addValue(normalSpeed);

        OptionValue<Double> fastSpeed = new OptionValue<>(
                "fast", "Rapid Glide", "Fast movement, harder control",
                Material.SUGAR, 0.5
        );
        fastSpeed.addRequirement(new NumberStatRequirement<>("circleLevel", 5, "Requires Circle Level 5"));
        fastSpeed.addImpact(new StatModifierImpact(this, "movement_speed", 0.5, StatModifierImpact.ModifierType.SET));
        fastSpeed.addImpact(new ManaCostImpact(this, 15));
        speedOption.addValue(fastSpeed);

        addOption(speedOption);

        // Jump Power Option
        SpellOption jumpOption = new SpellOption(this, "jump_power",
                "Adjust jump boost strength", Material.RABBIT_FOOT, 1);

        OptionValue<Double> normalJump = new OptionValue<>(
                "normal", "Standard Jump", "Normal jump boost",
                Material.RABBIT_FOOT, 1.0
        );
        normalJump.addImpact(new StatModifierImpact(this, "jump_boost", 1.0, StatModifierImpact.ModifierType.SET));
        jumpOption.addValue(normalJump);

        OptionValue<Double> strongJump = new OptionValue<>(
                "powerful", "Powerful Jump", "Strong upward boost",
                Material.GOLDEN_CARROT, 1.5
        );
        strongJump.addRequirement(new NumberStatRequirement<>("circleLevel", 5, "Requires Circle Level 5"));
        strongJump.addImpact(new StatModifierImpact(this, "jump_boost", 1.5, StatModifierImpact.ModifierType.SET));
        strongJump.addImpact(new ManaCostImpact(this, 10));
        jumpOption.addValue(strongJump);

        addOption(jumpOption);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/air_ball.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/air_ball.yml").get();
        loadCommonConfig(spellConfig);
        
        // Load air ball specific config
        this.movementSpeed = spellConfig.getDouble("movement_speed");
        this.jumpBoostStrength = spellConfig.getDouble("jump_boost_strength");
        this.manaDrainPerSecond = spellConfig.getDouble("mana_drain_per_second");
        this.basePower = spellConfig.getDouble("power");
        
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        // Check if player already has an air ball
        if (activeAirBalls.containsKey(caster.getUniqueId())) {
            caster.sendMessage(ChatColor.RED + "You are already riding an air ball!");
            return;
        }

        // Get modified stats
        double speed = getModifiedStat(caster, "movement_speed", movementSpeed);
        double jumpPower = getModifiedStat(caster, "jump_boost", jumpBoostStrength);

        // Create attack properties
        double power = basePower * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        AttackProperties props = new AttackProperties(
                caster,
                caster.getLocation(),
                power,
                AttackType.MAGIC
        );

        // Spawn invisible horse for riding
        Location spawnLoc = caster.getLocation();
        Horse horse = (Horse) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.HORSE);
        
        // Configure horse
        horse.setAdult();
        horse.setTamed(true);
        horse.setOwner(caster);
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        horse.setInvisible(true);
        horse.setInvulnerable(false); // Can be damaged
        horse.setSilent(true);
        horse.setAI(false); // No AI
        horse.setGravity(true);
        horse.setCustomName("§fAir Ball");
        horse.setCustomNameVisible(false);
        
        // Set horse speed based on spell option
        AttributeInstance speedAttr = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speed);
        }
        
        // Set horse jump strength based on spell option
        AttributeInstance jumpAttr = horse.getAttribute(Attribute.HORSE_JUMP_STRENGTH);
        if (jumpAttr != null) {
            jumpAttr.setBaseValue(jumpPower);
        }
        
        // Store data in horse
        NBT.modifyPersistentData(horse, nbt -> {
            nbt.setString("air_ball_owner", caster.getUniqueId().toString());
            nbt.setString("spell_type", "air_ball");
        });

        // Register spell component
        SpellEntityComponent entityComp = new SpellEntityComponent(
                this, props, caster, wand, SpellComponentType.NEUTRAL, horse
        );
        entityComp.setCollisionRadius(1.5);
        SpellComponentHandler.register(entityComp);

        // Make player ride the horse
        horse.addPassenger(caster);

        // Create and start air ball behavior
        AirBallInstance instance = new AirBallInstance(
                caster, horse, entityComp, speed, jumpPower, props
        );
        activeAirBalls.put(caster.getUniqueId(), instance);
        instance.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);

        // Sound effect
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.5f);
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

            for (int i = 0; i < 100; i++) {
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, 
                            new Particle.DustOptions(Color.fromRGB(240, 240, 240), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }

    /**
     * Handle player dismounting
     */
    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Horse horse)) return;
        if (!(event.getExited() instanceof Player player)) return;
        
        String spellType = NBT.getPersistentData(horse, nbt -> nbt.getString("spell_type"));
        if (!"air_ball".equals(spellType)) return;
        
        AirBallInstance instance = activeAirBalls.get(player.getUniqueId());
        if (instance != null) {
            // Player dismounted - end the spell
            Bukkit.getScheduler().runTask(Alkatraz.getInstance(), () -> {
                instance.disperseAirBall(false);
            });
        }
    }

    /**
     * Handle air ball taking damage
     */
    @EventHandler
    public void onAirBallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) return;
        
        String spellType = NBT.getPersistentData(horse, nbt -> nbt.getString("spell_type"));
        if (!"air_ball".equals(spellType)) return;
        
        String ownerUUID = NBT.getPersistentData(horse, nbt -> nbt.getString("air_ball_owner"));
        if (ownerUUID == null) return;
        
        UUID playerUUID = UUID.fromString(ownerUUID);
        AirBallInstance instance = activeAirBalls.get(playerUUID);
        
        if (instance != null) {
            // Cancel the damage but disperse the air ball
            event.setCancelled(true);
            instance.disperseAirBall(true);
        }
    }

    /**
     * Air ball instance behavior
     */
    private class AirBallInstance extends BukkitRunnable {
        private final Player rider;
        private final Horse horse;
        private final SpellEntityComponent component;
        private final double speed;
        private final double jumpPower;
        private final AttackProperties props;
        
        private int ticksElapsed = 0;
        private double rotation = 0;
        private boolean wasOnGround = false;
        private long lastJumpTime = 0;

        public AirBallInstance(Player rider, Horse horse, SpellEntityComponent component,
                              double speed, double jumpPower, AttackProperties props) {
            this.rider = rider;
            this.horse = horse;
            this.component = component;
            this.speed = speed;
            this.jumpPower = jumpPower;
            this.props = props;
        }

        @Override
        public void run() {
            // Check if should end
            if (!rider.isOnline() || rider.isDead() || !horse.isValid() || 
                props.isCancelled() || props.isCountered()) {
                disperseAirBall(false);
                cancel();
                return;
            }

            // Check if player is still riding
            if (!horse.getPassengers().contains(rider)) {
                disperseAirBall(false);
                cancel();
                return;
            }

            // Check duration
            ticksElapsed++;

            // Drain mana every 20 ticks (1 second)
            if (ticksElapsed % 20 == 0) {
                MagicProfile profile = ProfileManager.getProfile(rider, MagicProfile.class);
                double currentMana = profile.getMana();
                
                if (currentMana < manaDrainPerSecond) {
                    rider.sendMessage(ChatColor.RED + "Not enough mana to maintain the air ball!");
                    disperseAirBall(false);
                    cancel();
                    return;
                }
                
                profile.setMana(currentMana - manaDrainPerSecond);
            }

            // Update rotation for particles
            rotation += 0.3;
            if (rotation >= Math.PI * 2) rotation -= Math.PI * 2;

            // Handle jump boost detection
            handleJumpBoost();

            // Spawn particles
            spawnAirBallParticles();

            // Sound effects every 2 seconds
            if (ticksElapsed % 40 == 0) {
                horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.3f, 1.5f);
            }

            // Prevent fall damage
            rider.setFallDistance(0);
            horse.setFallDistance(0);
        }

        /**
         * Detects jump and applies extra boost
         */
        private void handleJumpBoost() {
            boolean onGround = horse.isOnGround();
            
            // Detect when horse leaves ground (jump)
            if (wasOnGround && !onGround) {
                long now = System.currentTimeMillis();
                if (now - lastJumpTime > 500) { // 500ms cooldown
                    // Apply extra upward boost
                    Vector velocity = horse.getVelocity();
                    velocity.setY(velocity.getY() + (jumpPower * 0.3)); // Add extra boost
                    horse.setVelocity(velocity);
                    
                    // Effects
                    horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.8f);
                    spawnJumpParticles();
                    
                    lastJumpTime = now;
                }
            }
            
            wasOnGround = onGround;
        }

        /**
         * Spawns the sphere of particles around the air ball
         */
        private void spawnAirBallParticles() {
            Location center = horse.getLocation().add(0, 1, 0);
            
            // Create sphere of particles
            int particles = 30;
            double radius = 1.5;
            
            for (int i = 0; i < particles; i++) {
                double theta = Math.random() * Math.PI * 2;
                double phi = Math.random() * Math.PI;
                
                double x = radius * Math.sin(phi) * Math.cos(theta);
                double y = radius * Math.sin(phi) * Math.sin(theta);
                double z = radius * Math.cos(phi);
                
                Location particleLoc = center.clone().add(x, y, z);
                particleLoc.getWorld().spawnParticle(Particle.CLOUD, particleLoc, 1, 0, 0, 0, 0);
            }
            
            // Horizontal rings (every 3 ticks)
            if (ticksElapsed % 3 == 0) {
                for (int ring = 0; ring < 3; ring++) {
                    double ringHeight = (ring - 1) * 0.7;
                    double ringRadius = Math.sqrt(radius * radius - ringHeight * ringHeight);
                    if (Double.isNaN(ringRadius)) ringRadius = radius * 0.5;
                    
                    int ringParticles = 20;
                    for (int i = 0; i < ringParticles; i++) {
                        double angle = ((double) i / ringParticles) * Math.PI * 2 + rotation;
                        double x = Math.cos(angle) * ringRadius;
                        double z = Math.sin(angle) * ringRadius;
                        
                        Location particleLoc = center.clone().add(x, ringHeight, z);
                        particleLoc.getWorld().spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(230, 230, 230), 0.8f));
                    }
                }
            }
            
            // Spiral effect
            if (ticksElapsed % 2 == 0) {
                int spiralPoints = 15;
                for (int i = 0; i < spiralPoints; i++) {
                    double progress = (double) i / spiralPoints;
                    double angle = progress * Math.PI * 4 + rotation;
                    double currentRadius = radius * Math.sin(progress * Math.PI);
                    double height = (progress - 0.5) * 2 * radius;
                    
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    
                    Location particleLoc = center.clone().add(x, height, z);
                    particleLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 1);
                }
            }
        }

        /**
         * Spawns particles when player jumps
         */
        private void spawnJumpParticles() {
            Location loc = horse.getLocation();
            loc.getWorld().spawnParticle(Particle.CLOUD, loc, 15, 0.5, 0.2, 0.5, 0.1);
            loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 5, 0.5, 0.2, 0.5);
        }

        /**
         * Disperses the air ball
         */
        public void disperseAirBall(boolean wasDamaged) {
            Location loc = horse.getLocation();
            
            // Eject passenger safely
            horse.removePassenger(rider);
            
            // Apply safe landing
            rider.setVelocity(new Vector(0, 0, 0));
            rider.setFallDistance(0);
            
            // Give brief slow falling
            rider.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW_FALLING, 40, 0, false, false
            ));
            
            // Particle burst
            loc.getWorld().spawnParticle(Particle.CLOUD, loc, 50, 1, 1, 1, 0.2);
            loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 20, 1, 0.5, 1);
            loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 3);
            
            // Sound effect
            if (wasDamaged) {
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                rider.sendMessage(ChatColor.YELLOW + "Your air ball was disrupted!");
            } else {
                loc.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_DEATH, 0.8f, 1.5f);
            }
            
            // Remove horse
            horse.remove();
            
            // Unregister component
            SpellComponentHandler.remove(component.getComponentID());
            
            // Remove from active tracking
            activeAirBalls.remove(rider.getUniqueId());
        }
    }

    /**
     * Utility method to get active air ball for a player
     */
    public static AirBallInstance getActiveAirBall(UUID playerUUID) {
        return activeAirBalls.get(playerUUID);
    }

    /**
     * Cleanup method called on plugin disable
     */
    public static void cleanupAllAirBalls() {
        for (AirBallInstance instance : new ArrayList<>(activeAirBalls.values())) {
            instance.disperseAirBall(false);
            instance.cancel();
        }
        activeAirBalls.clear();
    }
}
