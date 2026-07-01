package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.components.*;
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
import org.bukkit.entity.*;
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
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
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
        Alkatraz.getInstance().saveConfig("spells/dark_tendrils_options.yml");
        Alkatraz.getInstance().save("spells/dark_tendrils.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/dark_tendrils.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        
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
        int count = (int) getModifiedStat(caster, "tendril_count", 1);

        // Create attack properties
        double power = getPower(caster, getBasePower()) * NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                power,
                AttackType.MAGIC
        );

        for (int i = 1; i <= count; i++){
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
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;

        // Get modified stats
        double speed = tendrilSpeed;
        double lockSpeed = lockOnSpeed;
        int duration = tendrilDuration;

        // Create attack properties
        double wandp = wand == null ? 1 : NBT.get(wand, nbt -> (Double) nbt.getDouble("magic_power"));
        double power = getPower(caster, getBasePower())
                * wandp;
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                power,
                AttackType.MAGIC
        );

        for (int i = 1; i <= 2; i++){
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
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = caster.getEyeLocation();
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
    private void applyDebuffs(LivingEntity target, LivingEntity caster) {
        int duration;
        if (caster instanceof Player p) {
            duration = (int) getModifiedStat(p, "effect_duration", effectDuration);
        }else{
            duration = effectDuration;
        }
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
        private final LivingEntity caster;
        private final double wanderSpeed;
        private final double lockSpeed;
        private final double lockRange;
        private final int maxLifetime;
        private final SpellEntityComponent component;
        
        private LivingEntity target = null;
        private Vector wanderDirection;
        private int lifetime = 0;
        private int particleTick = 0;

        public TendrilAI(ArmorStand tendril, LivingEntity caster, double wanderSpeed,
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
                if (entity == caster) continue;
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
            if (tendril.getLocation().distance(target.getLocation()) < 2) {
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
