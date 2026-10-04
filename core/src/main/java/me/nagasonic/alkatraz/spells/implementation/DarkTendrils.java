package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.components.*;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.util.ParticleCaster;
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

    /** Orbiting tendril bodies while the tendril is still wandering, in blocks. */
    private static final double WANDER_INNER_RADIUS = 0.35D;
    private static final double WANDER_OUTER_RADIUS = 0.9D;
    /** Orbiting tendril bodies once a target is locked, in blocks. */
    private static final double LOCK_INNER_RADIUS = 0.5D;
    private static final double LOCK_OUTER_RADIUS = 1.6D;
    /** Bodies in either orbit. */
    private static final int ORBIT_BODIES = 6;
    /** Radians the orbit turns per tick. */
    private static final double ORBIT_SPIN_PER_TICK = 0.32D;
    /** Ribbon steps and widths for the tether drawn from a locked tendril to its target. */
    private static final int TETHER_STEPS = 10;
    private static final double TETHER_START_WIDTH = 0.35D;
    private static final double TETHER_END_WIDTH = 0.05D;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }
    
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
        ParticleCaster.spawn(caster, location.getWorld(), Utils.SQUID_INK, location, 20);
        location.getWorld().playSound(location, Sound.ENTITY_PHANTOM_HURT, 1.0f, 0.5f);
    }

    @Override
    public void onCountered(Location location) {
        // Explode into dark particles when countered
        ParticleCaster.spawn(null, location.getWorld(), Utils.SQUID_INK, location, 50);
        ParticleCaster.spawn(null, location.getWorld(), Utils.SOUL, location, 30);
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

        // Create attack properties.
        // RAW base power. getPower is applied once, on impact, in the
        // EntityDamageByEntityEvent handler.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster),
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

        // Create attack properties. RAW base power - getPower is applied once, on
        // impact, in the EntityDamageByEntityEvent handler.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand),
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
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.darktendrils.book_name"))
                .addCustomLoreLine(lang().get("spells.darktendrils.lore1"))
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

        // Dedup per target per cast. Every tendril of a volley shares one
        // AttackProperties, and a single damage event can fire more than once
        // while the tendril is being removed - without this guard the target is
        // scaled by getPower repeatedly inside one cast.
        if (props.hasHit(target)) return;
        props.hit(target);

        // Calculate final damage - the ONE getPower application for this impact.
        double finalDamage = getPower(comp.getCaster(), target, props.getRemainingPower());
        e.setDamage(finalDamage);

        applyDebuffs(target, comp.getCaster());

        // Remove tendril after hit
        tendril.remove();
        SpellComponentHandler.remove(ecomp.getComponentID());

        Location hitLoc = target.getLocation().add(0, 1, 0);
        ParticleCaster.spawn(comp.getCaster(), hitLoc.getWorld(), Utils.SQUID_INK, hitLoc, 30, 0.5, 0.5, 0.5);
        ParticleCaster.spawn(comp.getCaster(), hitLoc.getWorld(), Utils.SOUL, hitLoc, 15, 0.3, 0.3, 0.3);
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
        private int lastGroundChunkX = Integer.MIN_VALUE;
        private int lastGroundChunkZ = Integer.MIN_VALUE;
        private int groundY = 0;
        private int groundYAge = 0;

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
            if (caster instanceof Player cp && (!cp.isOnline() || cp.isDead())) {
                tendril.remove();
                SpellComponentHandler.remove(component.getComponentID());
                cancel();
                return;
            }

            // Check if tendril is still valid
            if (tendril.isDead() || !tendril.isValid()) {
                cancel();
                SpellComponentHandler.remove(component.getComponentID());
                return;
            }

            Location tLoc = tendril.getLocation();

            // Check lifetime
            lifetime++;
            if (lifetime >= maxLifetime) {
                onCountered(tLoc);
                tendril.remove();
                SpellComponentHandler.remove(component.getComponentID());
                cancel();
                return;
            }

            // Spawn particles
            if (particleTick++ % 2 == 0) {
                spawnTendrilParticles(tLoc);
            }

            // Check for nearby targets if not locked on
            if ((target == null || target.isDead() || !target.isValid()) && lifetime % 10 == 0) {
                findNearestTarget();
            }

            // Move tendril
            if (target != null && target.getLocation().distance(tLoc) <= lockRange) {
                // Locked on - move quickly toward target
                moveTowardTarget(tLoc);
            } else {
                // Wander randomly
                wander(tLoc);
            }
        }

        /**
         * Finds the nearest valid target within range
         */
        private void findNearestTarget() {
            target = null;
            double closestDistance = lockRange;
            Location tLoc = tendril.getLocation();
            MagicProfile cp = ProfileManager.getProfile(caster.getUniqueId(), MagicProfile.class);

            for (LivingEntity entity : me.nagasonic.alkatraz.util.Utils.getNearbyLivingEntities(tLoc, lockRange)) {
                // Skip invalid targets
                if (entity == caster) continue;
                if (entity instanceof ArmorStand) continue;
                if (entity instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;
                if (entity instanceof Player p && p.getGameMode() == GameMode.CREATIVE) continue;
                if (entity instanceof Player p){
                    MagicProfile op = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
                    if (op.isStealth() && op.getCircleLevel() > cp.getCircleLevel()) continue;
                }

                double distance = entity.getLocation().distance(tLoc);
                if (distance < closestDistance) {
                    target = entity;
                    closestDistance = distance;
                }
            }
        }

        /**
         * Moves tendril toward locked target
         */
        private void moveTowardTarget(Location tLoc) {
            Vector direction = Utils.safeNormalize(target.getEyeLocation()
                    .toVector()
                    .subtract(tLoc.toVector()));

            if (lifetime % 3 == 0) {
                tendril.teleport(tLoc.clone().add(direction.clone().multiply(lockSpeed * 3)));
            } else {
                tendril.setVelocity(direction.multiply(lockSpeed));
            }

            // Check if close enough to hit
            if (tendril.getLocation().distance(target.getLocation()) < 2) {
                // Deal damage through entity damage event
                target.damage(0.1, tendril); // Small damage to trigger event
            }
        }

        /**
         * Random wandering movement
         */
        private void wander(Location tLoc) {
            // Change direction occasionally
            if (lifetime % 40 == 0) {
                wanderDirection = new Vector(
                        Math.random() - 0.5,
                        Math.random() * 0.3 - 0.3, // Less vertical movement
                        Math.random() - 0.5
                ).normalize();
            }

            Location newLoc = tLoc.add(wanderDirection.clone().multiply(wanderSpeed));

            // Keep above ground
            if (newLoc.getY() < groundHeight(newLoc) + 2) {
                wanderDirection.setY(Math.abs(wanderDirection.getY()));
            }

            // Don't go too high
            if (newLoc.getY() > tendril.getWorld().getMaxHeight() - 10) {
                wanderDirection.setY(-Math.abs(wanderDirection.getY()));
            }

            tendril.teleport(newLoc);
        }

        private int groundHeight(Location loc) {
            int chunkX = loc.getBlockX() / 16;
            int chunkZ = loc.getBlockZ() / 16;
            if (chunkX != lastGroundChunkX || chunkZ != lastGroundChunkZ || groundYAge >= 20) {
                lastGroundChunkX = chunkX;
                lastGroundChunkZ = chunkZ;
                groundY = tendril.getWorld().getHighestBlockYAt(loc);
                groundYAge = 0;
            }
            groundYAge++;
            return groundY;
        }

        /**
         * Spawns visual particles for the tendril. Signature shape is an
         * <b>orbit</b>: the tendril bodies circle the tendril while it wanders, and
         * circle the target once it has locked on, closing in as it nears. The lock
         * also draws a tapering tether from the tendril to the target, so the lock
         * is readable before the hit lands. Every colour comes from the DARK palette
         * via {@link SpellVfx}.
         */
        private void spawnTendrilParticles(Location tLoc) {
            Location loc = tLoc.clone().add(0, 0.5, 0);

            ParticleCaster.spawn(caster, loc.getWorld(), Utils.SQUID_INK, loc, 3, 0.2, 0.2, 0.2, 0.01);

            double phase = lifetime * ORBIT_SPIN_PER_TICK;

            if (target == null) {
                // Wandering: the tendrils orbit the tendril itself.
                SpellVfx.orbit(DarkTendrils.this, caster, loc,
                        WANDER_INNER_RADIUS, WANDER_OUTER_RADIUS, ORBIT_BODIES, phase);
                return;
            }

            // Locked on: the orbit re-centres on the target and tightens as the
            // tendril closes, so the convergence is the tell that the hit is imminent.
            Location targetLoc = target.getLocation().add(0, 0.9, 0);
            double distance = tLoc.distance(targetLoc);
            double closeness = 1.0D - Math.min(1.0D, distance / Math.max(0.001D, lockRange));
            double inner = LOCK_INNER_RADIUS * (1.0D - 0.6D * closeness);
            double outer = LOCK_OUTER_RADIUS * (1.0D - 0.6D * closeness);

            SpellVfx.orbit(DarkTendrils.this, caster, targetLoc, inner, outer, ORBIT_BODIES, -phase);
            SpellVfx.beam(DarkTendrils.this, caster, loc, target.getEyeLocation(),
                    TETHER_STEPS, TETHER_START_WIDTH, TETHER_END_WIDTH);
        }
    }
}
