package me.nagasonic.alkatraz.spells.implementation;

import java.util.Random;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.components.SpellParticleComponent;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Circle 1 LIGHT: the element's gap filler and its starter, a plain basic projectile that
 * teaches light's entire identity from the very first circle.
 *
 * <h2>Why this class mentions nothing about the undead</h2>
 * {@code element: 'LIGHT'} makes {@link AttackSpell#getPower}'s light amplification run
 * automatically - a flat x2.0 against anything on the undead roster, or up to x2.5 on the
 * target's DARK/LIGHT affinity gap, taking the stronger of the two. That is the whole
 * mechanism, it lives in one place, and there is deliberately no {@code undead_multiplier}
 * key to configure: adding a manual x2.0 here would produce x4.0 and blow the circle-1
 * budget of 3.9 on the first skeleton a new player shoots.
 *
 * <h2>Damage contract</h2>
 * No {@code getPower} at cast time - the context is seeded with RAW
 * {@code getBasePower() * getWandPower(...)} and {@code context.power(target)} is the only
 * scaling call, at the moment of impact. One context per cast, so one counter kills the
 * bolt. Worst case per cast: {@code 3.0 x 1 = 3.0}, against {@code burstBudget(1) = 3.9}.
 */
public class LightBolt extends AttackSpell {

    /**
     * This spell's colour, handed to every particle it draws.
     *
     * <p>Pale warm white, unique to this class across the five light spells. It is a value you
     * own: edit this one line to re-tint the bolt, its dust and its cast circle together.
     */
    private static final Color CAST_COLOR = Color.fromRGB(255, 250, 200);
    private static final Particle.DustOptions BOLT_DUST = new Particle.DustOptions(CAST_COLOR, 0.7F);

    /** Dust points drawn per travel tick. Three jittered samples read as a bolt, not a dotted line. */
    private static final int BODY_DUST = 3;

    /** Random offset, in blocks, applied to each body dust sample so the bolt has thickness. */
    private static final double BODY_SPREAD = 0.06;

    /** Dust drawn by one {@link #burst}, at scale 1. */
    private static final int BURST_DUST = 12;

    /**
     * A hard ten-second ceiling on the travel runnable. At the defaults the bolt stops on
     * {@code projectile_range} after roughly 32 ticks, so this is unreachable - it exists so a
     * wand-modified {@code projectile_speed} cannot keep the task alive indefinitely.
     */
    private static final int MAX_TRAVEL_TICKS = 200;

    /** Collision radius registered with the counter system, in blocks. */
    private static final double COMPONENT_RADIUS = 0.5;

    /** Component lifetime, in ticks - long enough to be seen by a projectile fired the same tick. */
    private static final int COMPONENT_LIFETIME = 1;

    private double projectileSpeed;
    private double projectileRange;
    private double hitRadius;

    public LightBolt(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/light_bolt.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/light_bolt.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.projectileSpeed = spellConfig.getDouble("projectile_speed", 0.6);
        this.projectileRange = spellConfig.getDouble("projectile_range", 24.0);
        this.hitRadius = spellConfig.getDouble("projectile_hit_radius", 0.6);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        // ONE context for the whole cast. It carries the shared AttackProperties, so the single
        // SpellParticleComponent below is counterable and a counter ends the bolt outright.
        SpellAttackContext context = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);
        double speed = getModifiedStat(caster, "projectile_speed", projectileSpeed);
        Vector direction = Utils.safeNormalize(caster.getEyeLocation().getDirection());
        if (direction == null) return;

        fireBolt(caster, wand, context, Utils.castLocation(caster), direction,
                speed, projectileRange, hitRadius);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        // The caster-typed overload needs a Player for the wand multiplier, so a mob cast builds
        // its own properties from the no-player wand power and uses the properties-owning overload.
        AttackProperties props = new AttackProperties(
                caster, Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand),
                AttackType.MAGIC);
        SpellAttackContext context = SpellAttackContext.of(this, props, caster, wand);
        Vector direction = Utils.safeNormalize(
                caster.getTarget().getLocation().toVector().subtract(caster.getLocation().toVector()));
        if (direction == null) return;

        fireBolt(caster, wand, context, Utils.castLocation(caster), direction,
                projectileSpeed, projectileRange, hitRadius);
    }

    /**
     * The bolt itself: one {@link BukkitRunnable} at period 1, advancing a single {@link Location}.
     *
     * <p>Shared verbatim by both cast paths. There is no {@code getPower} anywhere in here; the
     * only scaling call is {@code context.power(target)}, reached once because the bolt ends on
     * its first hit.
     *
     * @param caster      the casting entity, for per-player particle density falloff
     * @param wand        the casting wand, or null on a mob cast
     * @param context     the single per-cast handle; supplies the shared AttackProperties
     * @param origin      where the bolt starts, normally the caster's casting hand
     * @param direction   the already-normalised travel direction
     * @param speed       blocks travelled per tick, after wand and option modifiers
     * @param range       blocks travelled before the bolt dissipates
     * @param scanRadius  the damage scan radius around the bolt's position, in blocks
     */
    private void fireBolt(LivingEntity caster, ItemStack wand, SpellAttackContext context,
                          Location origin, Vector direction, double speed, double range,
                          double scanRadius) {
        Location position = origin.clone();

        // A degenerate speed or range still has to be visible. Previously this returned before
        // any emission at all, so a wand that zeroed projectile_speed produced a silent cast.
        if (speed <= 0.0 || range <= 0.0) {
            burst(caster, position, 1.0F);
            return;
        }

        new BukkitRunnable() {
            double travelled = 0.0;
            int ticks = 0;

            @Override
            public void run() {
                // Once per scheduler run, before any damage - this is what makes power() legal
                // exactly once per target per tick.
                context.tick();

                if (context.isSpent()) {
                    burst(caster, position, 1.0F);
                    cancel();
                    return;
                }

                position.add(direction.clone().multiply(speed));
                travelled += speed;

                if (travelled >= range) {
                    burst(caster, position, 1.0F);
                    cancel();
                    return;
                }
                Block block = position.getBlock();
                if (block.isLiquid() || (!block.isPassable() && block.getType().isOccluding())) {
                    burst(caster, position, 1.0F);
                    cancel();
                    return;
                }
                if (ticks++ >= MAX_TRAVEL_TICKS) {
                    burst(caster, position, 1.0F);
                    cancel();
                    return;
                }

                World world = position.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                // Emitted before the collision scan so the bolt is on screen on the tick it lands.
                Random random = Utils.getRandom();
                for (int i = 0; i < BODY_DUST; i++) {
                    ParticleCaster.spawn(caster, world, Utils.DUST, position.clone().add(new Vector(
                            (random.nextDouble() - 0.5) * BODY_SPREAD,
                            (random.nextDouble() - 0.5) * BODY_SPREAD,
                            (random.nextDouble() - 0.5) * BODY_SPREAD)), 1, BOLT_DUST);
                }
                ParticleCaster.spawn(caster, world, Utils.ENTITY_EFFECT,
                        position, 1, 0.05, 0.05, 0.05, 0.0, CAST_COLOR);

                // The SHARED properties, so countering the bolt is a single call on one object.
                SpellParticleComponent component = new SpellParticleComponent(
                        LightBolt.this,
                        context.properties(),
                        caster,
                        wand,
                        SpellComponentType.OFFENSE,
                        position.clone(),
                        COMPONENT_RADIUS,
                        COMPONENT_LIFETIME);
                SpellComponentHandler.register(component);

                for (Entity entity : world.getNearbyEntities(position, scanRadius, scanRadius, scanRadius)) {
                    if (!(entity instanceof LivingEntity target)) continue;
                    if (entity.equals(caster)) continue;
                    if (target.isDead() || !target.isValid()) continue;
                    // Rule 2: hasHit is per-cast and never cleared. A false here must continue the
                    // scan, never damage again.
                    if (!context.hit(target)) continue;

                    SpellDamageUtil.damageWithSpell(target, context.power(target), caster, wand, LightBolt.this);
                    burst(caster, position, 1.0F);
                    world.playSound(position, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.4f);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * The bolt's own dissipation burst.
     *
     * <p>The caster is passed through so the caster always sees it at full density - the burst
     * is how the cast reads as "it hit something", so culling it by distance defeats the point.
     *
     * @param caster the casting entity, for per-player particle density falloff
     * @param loc    where the bolt ended
     * @param scale  multiplier on the dust count, so a hit and a wall-stop can read differently
     */
    private void burst(LivingEntity caster, Location loc, float scale) {
        World world = loc.getWorld();
        if (world == null) return;
        ParticleCaster.spawn(caster, world, Utils.EXPLOSION, loc, 1);
        ParticleCaster.spawn(caster, world, Utils.DUST, loc,
                Math.max(1, Math.round(BURST_DUST * scale)), 0.2, 0.2, 0.2, 0.0, BOLT_DUST);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, CAST_COLOR, caster, e, 2.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.ENTITY_EFFECT, location, 20, CAST_COLOR);
        location.getWorld().playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.4f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.ENTITY_EFFECT, location, 40, CAST_COLOR);
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION, location, 1);
        location.getWorld().playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.8f);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.lightbolt.book_name"))
                .addCustomLoreLine(lang().get("spells.lightbolt.lore1"))
                .addCustomLoreLine(lang().get("spells.lightbolt.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 1))
                .build();
    }
}
