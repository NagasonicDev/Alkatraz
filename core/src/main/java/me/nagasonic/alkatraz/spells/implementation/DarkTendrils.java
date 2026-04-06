package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.components.*;
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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Dark Tendrils - Creates dark mana tendrils that hunt targets
 * 
 * Behavior:
 * - Wanders randomly for up to 30 seconds
 * - Locks onto nearby living entities
 * - Speeds up when locked on
 * - Applies debuffs on hit (Slowness, Blindness, Wither)
 * - Can be hit/destroyed by other spells via SpellComponents
 */
public class DarkTendrils extends AttackSpell implements Listener {
    
    // Configuration values
    private int tendrilDuration;
    private double tendrilSpeed;
    private double lockOnSpeed;
    private double lockOnRange;
    private int effectDuration;

    public DarkTendrils(String type) {
        super(type);
    }

    @Override
    protected void setupOptions() {
        // Speed Option
        SpellOption speedOption = new SpellOption(this, "speed",
                "Adjust tendril movement speed", Material.FEATHER, 1);

        OptionValue<Double> slowSpeed = new OptionValue<>(
                "slow", "Slow Hunt", "Slower tendrils, more damage",
                Material.SOUL_SAND, 0.2
        );
        slowSpeed.addRequirement(new NumberStatRequirement<>("mastery_" + getType().toLowerCase(), 50));
        slowSpeed.addImpact(new StatModifierImpact(this, "tendril_speed", 0.2, StatModifierImpact.ModifierType.SET));
        slowSpeed.addImpact(new StatModifierImpact(this, "lock_on_speed", 0.6, StatModifierImpact.ModifierType.SET));
        slowSpeed.addImpact(new StatModifierImpact(this, "damage", 1.3, StatModifierImpact.ModifierType.MULTIPLY));
        speedOption.addValue(slowSpeed);

        OptionValue<Double> normalSpeed = new OptionValue<>(
                "normal", "Normal Hunt", "Standard hunting speed",
                Material.FEATHER, 0.3
        );
        normalSpeed.addImpact(new StatModifierImpact(this, "tendril_speed", 0.3, StatModifierImpact.ModifierType.SET));
        normalSpeed.addImpact(new StatModifierImpact(this, "lock_on_speed", 0.8, StatModifierImpact.ModifierType.SET));
        normalSpeed.addImpact(new StatModifierImpact(this, "damage", 1.0, StatModifierImpact.ModifierType.MULTIPLY));
        speedOption.addValue(normalSpeed);

        OptionValue<Double> fastSpeed = new OptionValue<>(
                "fast", "Rapid Hunt", "Fast tendrils, less damage",
                Material.SUGAR, 0.5
        );
        fastSpeed.addRequirement(new NumberStatRequirement<>("mastery_" + getType().toLowerCase(), 100));
        fastSpeed.addRequirement(new NumberStatRequirement<>("circleLevel", 4, "Requires Circle Level 4"));
        fastSpeed.addImpact(new StatModifierImpact(this, "tendril_speed", 0.5, StatModifierImpact.ModifierType.SET));
        fastSpeed.addImpact(new StatModifierImpact(this, "lock_on_speed", 1.2, StatModifierImpact.ModifierType.SET));
        fastSpeed.addImpact(new StatModifierImpact(this, "damage", 0.7, StatModifierImpact.ModifierType.MULTIPLY));
        fastSpeed.addImpact(new ManaCostImpact(this, 10));
        speedOption.addValue(fastSpeed);

        addOption(speedOption);

        // Duration Option
        SpellOption durationOption = new SpellOption(this, "duration",
                "Adjust tendril lifetime", Material.CLOCK, 1);

        OptionValue<Integer> shortDuration = new OptionValue<>(
                "short", "Short Life", "15 second duration, reduced cost",
                Material.CLOCK, 15
        );
        shortDuration.addRequirement(new NumberStatRequirement<>("mastery_" + getType().toLowerCase(), 25));
        shortDuration.addImpact(new StatModifierImpact(this, "duration", 15, StatModifierImpact.ModifierType.SET));
        shortDuration.addImpact(new ManaCostImpact(this, -15));
        durationOption.addValue(shortDuration);

        OptionValue<Integer> normalDuration = new OptionValue<>(
                "normal", "Normal Life", "30 second duration",
                Material.CLOCK, 30
        );
        normalDuration.addImpact(new StatModifierImpact(this, "duration", 30, StatModifierImpact.ModifierType.SET));
        durationOption.addValue(normalDuration);

        OptionValue<Integer> longDuration = new OptionValue<>(
                "long", "Extended Life", "60 second duration, higher cost",
                Material.CLOCK, 60
        );
        longDuration.addRequirement(new NumberStatRequirement<>("mastery_" + getType().toLowerCase(), 100));
        longDuration.addRequirement(new NumberStatRequirement<>("circleLevel", 5, "Requires Circle Level 5"));
        longDuration.addImpact(new StatModifierImpact(this, "duration", 60, StatModifierImpact.ModifierType.SET));
        longDuration.addImpact(new ManaCostImpact(this, 25));
        durationOption.addValue(longDuration);

        addOption(durationOption);

        // Effect Potency Option
        SpellOption effectOption = new SpellOption(this, "effects",
                "Adjust debuff potency", Material.FERMENTED_SPIDER_EYE, 1);

        OptionValue<Integer> normalEffects = new OptionValue<>(
                "normal", "Normal Debuffs", "Standard effect duration (5s)",
                Material.SPIDER_EYE, 5
        );
        normalEffects.addImpact(new StatModifierImpact(this, "effect_duration", 5, StatModifierImpact.ModifierType.SET));
        effectOption.addValue(normalEffects);

        OptionValue<Integer> strongEffects = new OptionValue<>(
                "strong", "Strong Debuffs", "Extended effect duration (10s)",
                Material.FERMENTED_SPIDER_EYE, 10
        );
        slowSpeed.addRequirement(new NumberStatRequirement<>("mastery_" + getType().toLowerCase(), 80));
        strongEffects.addRequirement(new NumberStatRequirement<>("circleLevel", 4, "Requires Circle Level 4"));
        strongEffects.addImpact(new StatModifierImpact(this, "effect_duration", 10, StatModifierImpact.ModifierType.SET));
        strongEffects.addImpact(new ManaCostImpact(this, 20));
        effectOption.addValue(strongEffects);

        addOption(effectOption);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, Player caster) {
        // Spawn dark particles when hitting barrier
        location.getWorld().spawnParticle(Particle.SQUID_INK, location, 20);
        location.getWorld().playSound(location, Sound.ENTITY_PHANTOM_HURT, 1.0f, 0.5f);
    }

    @Override
    public void onCountered(Location location) {
        // Explode into dark particles when countered
        location.getWorld().spawnParticle(Particle.SQUID_INK, location, 50);
        location.getWorld().spawnParticle(Particle.SOUL, location, 30);
        location.getWorld().playSound(location, Sound.ENTITY_PHANTOM_DEATH, 1.0f, 0.8f);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/dark_tendrils.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/dark_tendrils.yml").get();
        loadCommonConfig(spellConfig);
        
        // Load tendril-specific config
        this.tendrilDuration = spellConfig.getInt("tendril_duration");
        this.tendrilSpeed = spellConfig.getDouble("tendril_speed");
        this.lockOnSpeed = spellConfig.getDouble("lock_on_speed");
        this.lockOnRange = spellConfig.getDouble("lock_on_range");
        this.effectDuration = spellConfig.getInt("effect_duration");
        
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        // Get modified stats
        double speed = getModifiedStat(caster, "tendril_speed", tendrilSpeed);
        double lockSpeed = getModifiedStat(caster, "lock_on_speed", lockOnSpeed);
        int duration = (int) getModifiedStat(caster, "duration", tendrilDuration);

        // Create attack properties
        double power = getPower(caster, getBasePower()) * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                power,
                AttackType.MAGIC
        );

        for (int i = 1; i <= 3; i++){
            // Spawn tendril armor stand (invisible, small, no gravity)
            Location spawnLoc = caster.getEyeLocation().add(caster.getLocation().getDirection().multiply(2));
            ArmorStand tendril = (ArmorStand) spawnLoc.getWorld().spawnEntity(
                    spawnLoc,
                    EntityType.ARMOR_STAND
            );

            tendril.setVisible(false);
            tendril.setSmall(true);
            tendril.setGravity(false);
            tendril.setMarker(true);
            tendril.setInvulnerable(false); // Can be hit by spells
            tendril.setCustomName("§5Dark Tendril");
            tendril.setCustomNameVisible(false);

            // Create spell component for the tendril
            SpellEntityComponent entityComp = new SpellEntityComponent(
                    this, props, caster, wand, SpellComponentType.OFFENSE, tendril
            );
            entityComp.setCollisionRadius(1.0);

            // Store component ID in tendril
            NBT.modifyPersistentData(tendril, nbt -> {
                nbt.setString("component_id", entityComp.getComponentID().toString());
                nbt.setString("caster_uuid", caster.getUniqueId().toString());
            });

            SpellComponentHandler.register(entityComp);

            // Start tendril AI behavior
            new TendrilAI(tendril, caster, speed, lockSpeed, lockOnRange, duration, entityComp).runTaskTimer(
                    Alkatraz.getInstance(), 0L, 1L
            );

        }

        // Sound effect
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
    }

    @Override
    public int circleAction(Player p, PlayerSpellPrepareEvent e) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = p.getEyeLocation();
            float yaw = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();

            Vector forward = playerLoc.getDirection().normalize().multiply(1.5);
            List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 3, 0);

            for (int i = 0; i < 100; i++) {
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, 
                            new Particle.DustOptions(Color.fromRGB(75, 0, 130), 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&8Grimoire of Shadows &oChapter 1")
                .addCustomLoreLine("&8&oDarkness is the light of the dusk.")
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 3))
                .build();
    }

    @EventHandler
    private void onTendrilHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof ArmorStand tendril)) return;
        
        String componentId = NBT.getPersistentData(tendril, nbt -> nbt.getString("component_id"));
        if (componentId == null) return;
        
        SpellComponent comp = SpellComponentHandler.getActiveComponent(UUID.fromString(componentId));
        if (comp == null || !(comp instanceof SpellEntityComponent ecomp)) return;
        if (!(ecomp.getProperties() instanceof AttackProperties props)) return;
        if (comp.getSpell().getClass() != DarkTendrils.class) return;

        if (!(e.getEntity() instanceof LivingEntity target)) return;

        // Calculate final damage
        double finalDamage = getPower(comp.getCaster(), target, props.getRemainingPower());
        e.setDamage(finalDamage);

        applyDebuffs(target, comp.getCaster());

        // Remove tendril after hit
        tendril.remove();
        SpellComponentHandler.remove(ecomp.getComponentID());

        Location hitLoc = target.getLocation().add(0, 1, 0);
        hitLoc.getWorld().spawnParticle(Particle.SQUID_INK, hitLoc, 30, 0.5, 0.5, 0.5);
        hitLoc.getWorld().spawnParticle(Particle.SOUL, hitLoc, 15, 0.3, 0.3, 0.3);
        hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_PHANTOM_BITE, 1.0f, 0.8f);
    }

    /**
     * Applies debuff effects to the target
     */
    private void applyDebuffs(LivingEntity target, Player caster) {
        int duration = (int) getModifiedStat(caster, "effect_duration", effectDuration);
        int durationTicks = duration * 20;

        // Slowness II
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW, 
                durationTicks, 
                1, // Level II
                false, 
                true
        ));

        // Blindness I
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS, 
                durationTicks, 
                0, // Level I
                false, 
                true
        ));

        // Wither I
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.WITHER, 
                durationTicks, 
                0, // Level I
                false, 
                true
        ));
    }

    /**
     * AI behavior for the dark tendril
     */
    private class TendrilAI extends BukkitRunnable {
        private final ArmorStand tendril;
        private final Player caster;
        private final double wanderSpeed;
        private final double lockSpeed;
        private final double lockRange;
        private final int maxLifetime;
        private final SpellEntityComponent component;
        
        private LivingEntity target = null;
        private Vector wanderDirection;
        private int lifetime = 0;
        private int particleTick = 0;

        public TendrilAI(ArmorStand tendril, Player caster, double wanderSpeed, 
                        double lockSpeed, double lockRange, int maxLifetime,
                        SpellEntityComponent component) {
            this.tendril = tendril;
            this.caster = caster;
            this.wanderSpeed = wanderSpeed;
            this.lockSpeed = lockSpeed;
            this.lockRange = lockRange;
            this.maxLifetime = maxLifetime * 20; // Convert to ticks
            this.component = component;
            this.wanderDirection = new Vector(
                    Math.random() - 0.5, 
                    Math.random() - 1,
                    Math.random() - 0.5
            ).normalize();
        }

        @Override
        public void run() {
            // Check if tendril is still valid
            if (tendril.isDead() || !tendril.isValid()) {
                cancel();
                SpellComponentHandler.remove(component.getComponentID());
                return;
            }

            // Check lifetime
            lifetime++;
            if (lifetime >= maxLifetime) {
                onCountered(tendril.getLocation());
                tendril.remove();
                SpellComponentHandler.remove(component.getComponentID());
                cancel();
                return;
            }

            // Spawn particles
            if (particleTick++ % 2 == 0) {
                spawnTendrilParticles();
            }

            // Check for nearby targets if not locked on
            if (target == null || target.isDead() || !target.isValid()) {
                findNearestTarget();
            }

            // Move tendril
            if (target != null && target.getLocation().distance(tendril.getLocation()) <= lockRange) {
                // Locked on - move quickly toward target
                moveTowardTarget();
            } else {
                // Wander randomly
                wander();
            }
        }

        /**
         * Finds the nearest valid target within range
         */
        private void findNearestTarget() {
            target = null;
            double closestDistance = lockRange;

            for (LivingEntity entity : tendril.getLocation().getNearbyLivingEntities(lockRange)) {
                // Skip invalid targets
                //if (entity == caster) continue;
                if (entity instanceof ArmorStand) continue;
                if (entity instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;
                if (entity instanceof Player p && p.getGameMode() == GameMode.CREATIVE) continue;
                if (entity instanceof Player p){
                    MagicProfile cp = ProfileManager.getProfile(caster.getUniqueId(), MagicProfile.class);
                    MagicProfile op = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
                    if (op.isStealth() && op.getCircleLevel() > cp.getCircleLevel()) continue;
                }

                double distance = entity.getLocation().distance(tendril.getLocation());
                if (distance < closestDistance) {
                    target = entity;
                    closestDistance = distance;
                }
            }
        }

        /**
         * Moves tendril toward locked target
         */
        private void moveTowardTarget() {
            Vector direction = target.getEyeLocation()
                    .toVector()
                    .subtract(tendril.getLocation().toVector())
                    .normalize()
                    .multiply(lockSpeed);

            tendril.teleport(tendril.getLocation().add(direction));

            // Check if close enough to hit
            if (tendril.getLocation().distance(target.getLocation()) < 1.5) {
                // Deal damage through entity damage event
                target.damage(0.1, tendril); // Small damage to trigger event
            }
        }

        /**
         * Random wandering movement
         */
        private void wander() {
            // Change direction occasionally
            if (lifetime % 40 == 0) {
                wanderDirection = new Vector(
                        Math.random() - 0.5, 
                        Math.random() * 0.3 - 0.3, // Less vertical movement
                        Math.random() - 0.5
                ).normalize();
            }

            Location newLoc = tendril.getLocation().add(wanderDirection.clone().multiply(wanderSpeed));
            
            // Keep above ground
            if (newLoc.getY() < tendril.getWorld().getHighestBlockYAt(newLoc) + 2) {
                wanderDirection.setY(Math.abs(wanderDirection.getY()));
            }
            
            // Don't go too high
            if (newLoc.getY() > tendril.getWorld().getMaxHeight() - 10) {
                wanderDirection.setY(-Math.abs(wanderDirection.getY()));
            }

            tendril.teleport(newLoc);
        }

        /**
         * Spawns visual particles for the tendril
         */
        private void spawnTendrilParticles() {
            Location loc = tendril.getLocation().add(0, 0.5, 0);
            
            // Dark swirling particles
            loc.getWorld().spawnParticle(
                    Particle.SQUID_INK, 
                    loc, 
                    3, 
                    0.2, 0.2, 0.2, 
                    0.01
            );
            
            // Soul particles for locked on state
            if (target != null) {
                loc.getWorld().spawnParticle(
                        Particle.SOUL, 
                        loc, 
                        2, 
                        0.1, 0.1, 0.1, 
                        0.02
                );
                
                // Draw a line toward target
                Vector toTarget = target.getEyeLocation().toVector().subtract(loc.toVector());
                double distance = toTarget.length();
                toTarget.normalize().multiply(0.5);
                
                for (int i = 0; i < Math.min(distance * 2, 10); i++) {
                    Location particleLoc = loc.clone().add(toTarget.clone().multiply(i));
                    particleLoc.getWorld().spawnParticle(
                            Particle.SPELL_WITCH, 
                            particleLoc, 
                            1, 
                            0, 0, 0, 
                            0
                    );
                }
            }
        }
    }
}
