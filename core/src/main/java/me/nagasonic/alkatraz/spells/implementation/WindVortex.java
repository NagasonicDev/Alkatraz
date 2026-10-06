package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.ManaCostImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.effect.EffectApplicator;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
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

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `AIR` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(234, 246, 255);

    /** The AIR palette, resolved once so a per-tick vortex allocates no colour objects. */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.AIR);

    /**
     * Vortex geometry. The descending rings are drawn as a stack of {@link SpellVfx#orbit}
     * bands rather than one flat ring, so the field reads as a funnel: the innermost body of
     * each band sits at {@link #RING_INNER_FRACTION} of the radius and the outermost at the
     * full radius, and each band is lifted by its own fraction of {@link #RING_SPAN}.
     */
    private static final int RING_COUNT = 4;
    private static final int RING_POINTS = 30;
    private static final double RING_SPAN = 0.6;
    private static final double RING_INNER_FRACTION = 0.5;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    // Configuration values
    private int vortexDuration;
    private double vortexRadius;
    private double pullStrength;
    private int tickRate;

    public WindVortex(String type) {
        super(type);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        // Wind disperses on barrier
        ParticleCaster.spawn(caster, location.getWorld(), Utils.CLOUD, location, 30, 1, 1, 1, 0.1);
        ParticleCaster.spawn(caster, location.getWorld(), Utils.SWEEP_ATTACK, location, 5);
    }

    @Override
    public void onCountered(Location location) {
        // Vortex disperses
        ParticleCaster.spawn(null, location.getWorld(), Utils.CLOUD, location, 50, 2, 2, 2, 0.2);
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION, location, 1);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/wind_vortex.yml");
        Alkatraz.getInstance().saveConfig("spells/wind_vortex_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/wind_vortex.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        
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

        // RAW base power. getPower is applied once per hit, in pullEntities.
        AttackProperties props = new AttackProperties(
                caster,
                caster.getLocation(),
                getBasePower() * getWandPower(wand, caster),
                AttackType.MAGIC
        );
        SpellAttackContext context = SpellAttackContext.of(this, props, caster, wand);

        // Start vortex behavior
        VortexBehavior task = new VortexBehavior(caster, wand, props, context, radius, pull, duration);
        task.runTaskTimer(Alkatraz.getInstance(), 0L, tickRate);

        // Initial sound effect
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;

        // Get modified stats
        double radius = vortexRadius;
        double pull = pullStrength;
        int duration = vortexDuration;

        // Create attack properties
        double wandp = getWandPowerOrDefault(wand);
        // RAW base power - see the player branch.
        AttackProperties props = new AttackProperties(
                caster,
                caster.getLocation(),
                getBasePower() * wandp,
                AttackType.MAGIC
        );
        SpellAttackContext context = SpellAttackContext.of(this, props, caster, wand);

        // Start vortex behavior
        VortexBehavior task = new VortexBehavior(caster, wand, props, context, radius, pull, duration);
        task.runTaskTimer(Alkatraz.getInstance(), 0L, tickRate);

        // Initial sound effect
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 2.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.windvortex.book_name"))
                .addCustomLoreLine(lang().get("spells.windvortex.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }

    /**
     * Vortex behavior - pulls entities and deals damage
     */
    private class VortexBehavior extends BukkitRunnable {
        private final LivingEntity caster;
        private final ItemStack wand;
        private final AttackProperties props;
        private final SpellAttackContext context;
        private final double radius;
        private final double pullStrength;
        private final int maxDuration;

        private int ticksElapsed = 0;
        private double rotation = 0;

        // SUSTAIN CADENCE LEDGER - per TARGET. AttackProperties' hit Set is never
        // cleared for the life of the cast, so gating the repeats on props.hasHit
        // would allow ONE hit for the whole 5 second field instead of five. The
        // once-per-interval clock is runnable-local; props.hit() is still called so
        // a single counter still kills the whole vortex.
        private final Map<UUID, Long> nextStrikeTick = new HashMap<>();

        public VortexBehavior(LivingEntity caster, ItemStack wand, AttackProperties props,
                             SpellAttackContext context, double radius,
                             double pullStrength, int duration) {
            this.caster = caster;
            this.wand = wand;
            this.props = props;
            // The old constructor took a damagePerTick parameter and NEVER STORED
            // IT. wind_vortex's power was silently dropped and the field dealt no
            // damage at all. Scaling now happens per hit through the context.
            this.context = context;
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
            if (caster.isDead()) {
                cleanup();
                cancel();
                return;
            }

            // Check duration
            ticksElapsed++;
            // Once per scheduler run, before any damage: SpellAttackContext.power()
            // throws if it is asked to scale the same target twice inside one tick.
            context.tick();
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
            // Main spiral - a single component covers the whole vortex body
            List<Location> spiralPoints = createVortexSpiral(center, radius, radius * 0.8, 3, rotation);
            for (Location loc : spiralPoints) {
                ParticleCaster.spawn(caster, loc.getWorld(), Utils.CLOUD, loc, 1, 0, 0, 0, 0);
            }

            SpellParticleComponent comp = new SpellParticleComponent(
                    WindVortex.this,
                    props,
                    caster,
                    wand,
                    SpellComponentType.OFFENSE,
                    center,
                    radius,
                    Math.max(1, tickRate)
            );
            SpellComponentHandler.register(comp);

            // Rings at different heights (every 5 ticks)
            if (ticksElapsed % 5 == 0) {
                spawnVortexRings(center);
            }

            // Ground ring (every 10 ticks)
            if (ticksElapsed % 10 == 0) {
                int particles = 40;
                for (int i = 0; i < particles; i++) {
                    double angle = ((double) i / particles) * Math.PI * 2 + rotation;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    Location particleLoc = center.clone().add(x, 0.1, z);
                    ParticleCaster.spawn(caster, particleLoc.getWorld(), Utils.SWEEP_ATTACK, particleLoc, 1);
                }
            }
        }

        /**
         * Creates a spiraling vortex effect
         */
        private List<Location> createVortexSpiral(Location center, double radius, double height, 
                                                 int density, double rotation) {
            List<Location> points = new ArrayList<>();
            int pointsPerSpiral = 12;
            
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
         * Draws the funnel's descending rings as a stack of {@link SpellVfx#orbit} bands.
         *
         * <p>Each band is a flat orbit lifted to its own height, with bodies spread between
         * {@link #RING_INNER_FRACTION} of the radius and the full radius, so the stack widens as
         * it rises and reads as the mouth of a funnel rather than as four unrelated hoops.
         * {@code orbit} alternates {@code core} and {@code accent} bodies by index, which gives
         * the ring visible internal rhythm that a flat ring of one colour does not have.
         *
         * @param centre the vortex centre, on the ground under the caster
         */
        private void spawnVortexRings(Location centre) {
            double inner = radius * RING_INNER_FRACTION;
            double bandRadius = radius - inner;
            for (int ring = 0; ring < RING_COUNT; ring++) {
                double ringProgress = (double) ring / RING_COUNT;
                Location band = centre.clone().add(0.0, RING_SPAN * radius * ringProgress, 0.0);
                SpellVfx.orbit(CAST_COLOR, caster, band, inner, inner + bandRadius,
                        RING_POINTS, rotation + ringProgress * Math.PI);
            }
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
            Vector centerVec = center.toVector();

            for (Entity entity : nearbyEntities) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (entity == caster) continue;
                if (entity instanceof Player && ((Player) entity).getGameMode() == GameMode.SPECTATOR) continue;

                Location eLoc = entity.getLocation();
                double distance = eLoc.distance(center);
                if (distance > radius) continue;

                // Calculate pull direction (toward caster's feet)
                Vector pullDirection = Utils.safeNormalize(centerVec.subtract(eLoc.toVector()));

                // Scale pull strength by distance (closer = stronger pull)
                double distanceFactor = 1.0 - (distance / radius);
                Vector pullVector = pullDirection.multiply(pullStrength * distanceFactor);

                // Apply velocity
                Vector currentVelocity = entity.getVelocity();
                entity.setVelocity(currentVelocity.add(pullVector));

                // SUSTAIN CADENCE: one hit per DAMAGE_INTERVAL_TICKS, per target.
                // 5 seconds at one hit per second per target is 5 hits. Gating this
                // on props.hasHit would allow ONE hit for the whole cast instead -
                // AttackProperties' hit Set is never cleared. See "SUSTAIN cadence:
                // hasHit must not gate the repeats" at the top of this wave.
                Long nextStrike = nextStrikeTick.get(living.getUniqueId());
                if (nextStrike == null || nextStrike <= ticksElapsed) {
                    nextStrikeTick.put(living.getUniqueId(), ticksElapsed + SpellAttackContext.DAMAGE_INTERVAL_TICKS);
                    props.hit(living);
                    SpellDamageUtil.damageWithSpell(living, context.power(living),
                            caster, wand, WindVortex.this);
                }

                // Apply slight levitation to lift them off ground
                if (distance < radius * 0.5 && ticksElapsed % 40 == 0) {
                    EffectApplicator.applyPotion(caster, living, new PotionEffect(
                            PotionEffectType.LEVITATION, 20, 0, false, false
                    ), WindVortex.this);
                }

                // Spawn particles around caught entities
                if (ticksElapsed % 3 == 0) {
                    spawnEntityWindEffect(eLoc, centerVec, center);
                }
            }
        }

        /**
         * Spawns wind effect around entities
         */
        private void spawnEntityWindEffect(Location entityLoc, Vector centerVec, Location center) {
            int particles = 8;
            double entityRadius = 0.5;
            
            for (int i = 0; i < particles; i++) {
                double angle = ((double) i / particles) * Math.PI * 2 + rotation;
                double x = Math.cos(angle) * entityRadius;
                double z = Math.sin(angle) * entityRadius;
                
                Location particleLoc = entityLoc.clone().add(x, 1, z);
                ParticleCaster.spawn(caster, particleLoc.getWorld(), Utils.CLOUD, particleLoc, 1, 0, 0, 0, 0);
            }

            // Pull line effect
            double distance = entityLoc.distance(center);
            if (distance > 2) {
                Vector direction = Utils.safeNormalize(centerVec.subtract(entityLoc.toVector()));

                int segments = 5;
                for (int i = 0; i < segments; i++) {
                    double progress = (double) i / segments;
                    Location lineLoc = entityLoc.clone().add(direction.clone().multiply(distance * progress));
                    ParticleCaster.spawn(caster, lineLoc.getWorld(), Utils.DUST, lineLoc, 1, 0, 0, 0, 0, PALETTE.accentDust());
                }
            }
        }

        /**
         * Cleanup when vortex ends
         */
        private void cleanup() {
            Location center = caster.getLocation();
            
            // Final particle burst
            ParticleCaster.spawn(caster, center.getWorld(), Utils.CLOUD, center, 100,
                    radius * 0.5, radius * 0.3, radius * 0.5, 0.1);
            ParticleCaster.spawn(caster, center.getWorld(), Utils.SWEEP_ATTACK, center, 10,
                    radius * 0.3, 0.5, radius * 0.3);
            ParticleCaster.spawn(caster, center.getWorld(), Utils.DUST, center, 0, PALETTE.coreDust());
            
            // Sound effect
            center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 0.8f);
        }
    }
}
