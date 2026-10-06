package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
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
import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Circle 9 LIGHT, the element's DENIAL capstone: a wall of light that denies passage and
 * executes anything it touches below 30% health.
 *
 * <p><b>No blocks are ever placed, changed or restored.</b> A wall of light is not terrain
 * and light never reshapes land, which is what the deviations table records. The wall is
 * particles, a radial push and an execution. There is no {@code Block.setType}, no
 * {@code FallingBlock}, no {@code Protection.blockEdit} and no {@code SpellBlockComponent}
 * anywhere in this class, and the yml carries no {@code terrain_budget} key.
 *
 * <h2>Why the push is load-bearing</h2>
 * Without it an enemy walks straight through a wall, which is the entire point of a wall.
 * It is {@code Barrier.java}'s radial push with its literal {@code 0.3} replaced by
 * {@code push_strength}, run per segment over the band.
 *
 * <h2>Damage contract</h2>
 * One {@link SpellAttackContext} for the whole wall, created at cast time and never scaled
 * there. {@code context.power(target)} inside {@link #damageInBand} is the only scaling
 * call, once per target per cast. {@code AttackProperties.hasHit} is per-cast and
 * {@code context.tick()} never clears it, so one application per target per cast is the hard
 * ceiling - one of the wall's eighteen overlapping segment scans finding a body is what the
 * guard exists to stop.
 *
 * <p>{@code denial_damage_cap} caps the spell's <em>total</em> incidental damage per cast
 * across however many entities walk into the wall, not one target's share of it, so the
 * accumulator is cast-level. Fifteen passes at 6.0 is the theoretical 90.0; the clip is
 * applied after {@code getPower} by {@code Math.min}, which is legal and does not
 * double-apply - only the resulting number is clipped, and clipping before {@code getPower}
 * would under-scale the option modifiers. The execution is not damage and is not priced.
 */
public class CrownOfDawn extends AttackSpell {

    /** Pure white - the brightest thing in the element. */
    private static final Color CROWN_COLOR = Color.fromRGB(255, 255, 255);
    private static final Particle.DustOptions CROWN_DUST = new Particle.DustOptions(CROWN_COLOR, 1.0F);
    private static final Particle.DustOptions CROWN_EDGE = new Particle.DustOptions(CROWN_COLOR, 0.6F);

    /** The only non-light colour in all five light spells, and only ever used on a break. */
    private static final Particle.DustOptions BREAK_DUST =
            new Particle.DustOptions(Color.fromRGB(120, 90, 60), 0.5F);

    /** Extra blocks either side of {@code wall_half_thickness} that the push and the damage scan share. */
    private static final double BAND_MARGIN = 0.6;
    /** Vertical step the wall's particles climb on, in blocks. */
    private static final double RISE_STEP = 0.5;
    /** Lifetime of each per-segment DEFENSE component. 2 keeps a steady overlap at period 1. */
    private static final int COMPONENT_LIFETIME = 2;
    /** The mob path's wall is half the player path's length. */
    private static final double MOB_LENGTH_FACTOR = 0.5;

    private CrownOfDawnConfig config = CrownOfDawnConfig.fromConfig(null);

    public CrownOfDawn(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /**
     * One cast's wall geometry, resolved once so the runnable does not re-derive it every tick.
     *
     * @param segments   the grounded foot of each wall segment, in order along the aim
     * @param height     how far the wall reaches upward, in blocks
     * @param push       horizontal velocity added to everything in the band each tick
     * @param threshold  the health fraction below which an entity is executed
     * @param thickness  half the wall's thickness in blocks, which is also the component radius
     */
    private record WallPlan(List<Location> segments, double height, double push,
                            double threshold, double thickness) {
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/crown_of_dawn.yml");
        Alkatraz.getInstance().saveConfig("spells/crown_of_dawn_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/crown_of_dawn.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = CrownOfDawnConfig.fromConfig(spellConfig.getValues(false));
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        // ONE context for the whole wall. The wall's incidental damage shares it, exactly as a
        // volley shares one context. No getPower at cast time.
        SpellAttackContext context = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);

        double length = getModifiedStat(caster, "wall_length", config.wallLength());
        double height = getModifiedStat(caster, "wall_height", config.wallHeight());
        Vector direction = Utils.safeNormalize(caster.getEyeLocation().getDirection());
        if (direction == null) return;

        WallPlan plan = buildPlan(caster, direction, length, height);
        raiseWall(caster, wand, context, plan);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        AttackProperties props = new AttackProperties(
                caster, Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand),
                AttackType.MAGIC);
        SpellAttackContext context = SpellAttackContext.of(this, props, caster, wand);
        Vector direction = Utils.safeNormalize(
                caster.getTarget().getLocation().toVector().subtract(caster.getLocation().toVector()));
        if (direction == null) return;

        WallPlan plan = buildPlan(caster, direction,
                config.wallLength() * MOB_LENGTH_FACTOR, config.wallHeight());
        raiseWall(caster, wand, context, plan);
    }

    /**
     * Resolves the wall's feet once, before the runnable starts.
     *
     * <p>The un-grounded fallback matters: over water or a void the wall still rises rather
     * than silently vanishing, which is what a terrain spell would be for and this is not.
     *
     * @param caster    the casting entity
     * @param direction the already-normalised aim
     * @param length    how far the wall spans along the aim
     * @param height    how far the wall reaches upward
     * @return the resolved plan, or null when nothing could be resolved at all
     */
    private WallPlan buildPlan(LivingEntity caster, Vector direction, double length, double height) {
        int segmentCount = Math.max(1, (int) Math.round(length / Math.max(0.1, config.segmentSpacing())));
        List<Location> segments = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            Location probe = caster.getEyeLocation().add(direction.clone().multiply(i * config.segmentSpacing()));
            Location grounded = Utils.findTopSolid(probe.clone().add(0, 1, 0), 12);
            segments.add(grounded != null ? grounded : probe.add(0, -1.2, 0));
        }
        if (segments.isEmpty()) return null;
        return new WallPlan(segments, height, config.pushStrength(),
                config.executionThreshold(), config.wallHalfThickness());
    }

    /**
     * The wall itself: one {@link BukkitRunnable} at period 1 for the wall's whole life.
     *
     * <p>Every exit removes the BossBar, so there is no path that leaves one on screen:
     * broken, natural expiry, or caster dead/offline.
     *
     * @param caster  the casting entity, for per-player particle density falloff
     * @param wand    the casting wand, or null on a mob cast
     * @param context the single per-cast handle
     * @param plan    the resolved wall geometry
     */
    private void raiseWall(LivingEntity caster, ItemStack wand, SpellAttackContext context, WallPlan plan) {
        // EXACTLY ONE properties object for the whole cast. BarrierProperties' constructor
        // calls Bukkit.createBossBar, so a per-tick or per-segment construction would leak one
        // BossBar each. It also means attackProps.getCollided().contains(barrierProps) dedupes
        // the wall correctly against a single attacking volley.
        CrownOfDawnBarrierProperties properties = new CrownOfDawnBarrierProperties(
                caster,
                plan.segments().get(plan.segments().size() / 2),
                config.barrierHitpoints(),
                BarrierType.PHYSICAL,
                plan.thickness());
        if (caster instanceof Player owner) properties.getHealthBar().addPlayer(owner);

        // Per-cast holders, so nothing leaks across casts.
        Set<UUID> executed = new HashSet<>();
        double[] damageSpent = {0.0};

        new BukkitRunnable() {
            int elapsed = 0;
            long lastDamage = -SpellAttackContext.DAMAGE_INTERVAL_TICKS;

            @Override
            public void run() {
                context.tick();
                elapsed++;

                if (properties.isBroken()) {
                    onWallBreak(caster, properties);
                    properties.getHealthBar().removeAll();
                    cancel();
                    return;
                }
                if (elapsed >= config.durationTicks()) {
                    onWallFade(caster, plan);
                    properties.getHealthBar().removeAll();
                    cancel();
                    return;
                }
                if (caster.isDead() || (caster instanceof Player offline && !offline.isOnline())) {
                    properties.getHealthBar().removeAll();
                    cancel();
                    return;
                }

                double rise = Math.min(1.0, elapsed / (double) config.riseTickCount());
                double band = plan.thickness() + BAND_MARGIN;

                for (Location segment : plan.segments()) {
                    World world = segment.getWorld();
                    if (world == null) continue;

                    // The rise. Particles only - no block is placed, changed or restored.
                    drawSegment(caster, world, segment, plan.height() * rise);

                    // DEFENSE registration, every tick, short lifetime. Required, not optional:
                    // the projectile deflection in SpellComponentHandler walks only REGISTERED
                    // components, so a wall that stopped registering would stop deflecting arrows.
                    Location mid = segment.clone().add(0, plan.height() * rise / 2.0, 0);
                    SpellParticleComponent component = new SpellParticleComponent(
                            CrownOfDawn.this, properties, caster, wand,
                            SpellComponentType.DEFENSE, mid, plan.thickness(), COMPONENT_LIFETIME);
                    SpellComponentHandler.register(component);

                    for (Entity entity : world.getNearbyEntities(segment, band, plan.height() / 2.0, band)) {
                        if (!(entity instanceof LivingEntity living)) continue;
                        if (living.equals(caster)) continue;
                        if (living.isDead() || !living.isValid()) continue;
                        if (NBT.getPersistentData(living, nbt -> nbt.getBoolean("summoned_zombie"))) continue;

                        // THE PUSH. Without it an enemy walks straight through the wall, which
                        // is the entire point of a wall.
                        Vector push = living.getLocation().toVector().subtract(mid.toVector());
                        push.setY(0);
                        if (push.lengthSquared() >= 0.01) {
                            push = Utils.safeNormalize(push).multiply(plan.push());
                            living.setVelocity(living.getVelocity().add(push));
                        }

                        // THE DENIAL. Strictly below the threshold, and add() returns false for
                        // an already-executed entity, so the burst and sound fire once per
                        // entity per cast. setHealth(0.0) is not a damage event, which is why it
                        // is not priced against denial_damage_cap.
                        if (living.getMaxHealth() > 0.0
                                && living.getHealth() / living.getMaxHealth() < plan.threshold()
                                && executed.add(living.getUniqueId())) {
                            onExecute(caster, living);
                            living.setHealth(0.0);
                        }
                    }
                }

                // The incidental damage passes. With lastDamage starting at -20 and elapsed
                // incremented first, they run at elapsed 1, 21, 41, ... 281 - exactly 15 over the
                // 300-tick life, which is what hit_count declares.
                if (elapsed - lastDamage >= SpellAttackContext.DAMAGE_INTERVAL_TICKS) {
                    lastDamage = elapsed;
                    damageInBand(caster, wand, context, plan, damageSpent, executed);
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * One capped, deduped, once-per-pass damage sweep over the wall's band.
     *
     * @param caster       the casting entity
     * @param wand         the casting wand
     * @param context      the single per-cast handle
     * @param plan         the resolved wall geometry
     * @param damageSpent  a one-element cast-level accumulator; the ceiling is the whole cast's
     * @param executed     entities already executed this cast, which are not burned as well
     */
    private void damageInBand(LivingEntity caster, ItemStack wand, SpellAttackContext context,
                              WallPlan plan, double[] damageSpent, Set<UUID> executed) {
        double band = plan.thickness() + BAND_MARGIN;
        double cap = config.denialDamageCap();
        for (Location segment : plan.segments()) {
            World world = segment.getWorld();
            if (world == null) continue;
            for (Entity entity : world.getNearbyEntities(segment, band, plan.height() / 2.0, band)) {
                // Once the ceiling is spent, no entity anywhere in the band can be damaged
                // again this cast - so return, do not continue.
                if (damageSpent[0] >= cap) return;
                if (!(entity instanceof LivingEntity target)) continue;
                if (target.equals(caster)) continue;
                if (target.isDead() || !target.isValid()) continue;
                if (executed.contains(target.getUniqueId())) continue;
                if (target.getMaxHealth() > 0.0
                        && target.getHealth() / target.getMaxHealth() < plan.threshold()) {
                    continue;                       // it is being executed, not burned
                }
                // Rule 2: ONE application per target per cast. A false here must continue the
                // scan, never damage again.
                if (!context.hit(target)) continue;

                double scaled = context.power(target);               // ONE getPower
                double amount = Math.min(scaled, cap - damageSpent[0]);
                if (amount <= 0.0) return;
                SpellDamageUtil.damageWithSpell(target, amount, caster, wand, CrownOfDawn.this);
                damageSpent[0] += amount;
            }
        }
    }

    /** Paints one segment's column. Visual only. */
    private void drawSegment(LivingEntity caster, World world, Location segment, double currentHeight) {
        if (currentHeight <= 0.0) return;
        int steps = (int) Math.ceil(currentHeight / RISE_STEP);
        for (int i = 0; i < steps; i++) {
            boolean crest = i == steps - 1;
            Location point = segment.clone().add(0, i * RISE_STEP, 0);
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 1, crest ? CROWN_DUST : CROWN_EDGE);
            if (crest) {
                ParticleCaster.spawn(caster, world, Utils.ENTITY_EFFECT, point, 1, 0.0, 0.0, 0.0, 0.05, CROWN_COLOR);
            }
        }
    }

    private void onExecute(LivingEntity caster, LivingEntity victim) {
        Location at = victim.getLocation().add(0, 1, 0);
        World world = at.getWorld();
        if (world == null) return;
        ParticleCaster.spawn(caster, world, Utils.ENTITY_EFFECT, at, 60, 0.4, 0.6, 0.4, 0.0, CROWN_COLOR);
        ParticleCaster.spawn(caster, world, Utils.DUST, at, 40, 0.3, 0.3, 0.3, 0.0, CROWN_DUST);
        world.playSound(at, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.8f);
    }

    private void onWallBreak(LivingEntity caster, CrownOfDawnBarrierProperties properties) {
        Location mid = properties.getCastLocation();
        World world = mid.getWorld();
        if (world == null) return;
        for (Location point : ParticleUtils.sphere(mid, properties.getRadius() + 0.8, 24)) {
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 1, BREAK_DUST);
        }
        world.playSound(mid, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
        if (caster instanceof Player owner) {
            Utils.sendActionBar(owner, lang().get("spells.crownofdawn.broken"));
        }
    }

    private void onWallFade(LivingEntity caster, WallPlan plan) {
        for (Location segment : plan.segments()) {
            World world = segment.getWorld();
            if (world == null) continue;
            Location centre = segment.clone().add(0, plan.height() / 2.0, 0);
            for (Location point : ParticleUtils.sphere(centre, 0.7, 8)) {
                ParticleCaster.spawn(caster, world, Utils.DUST, point, 1, CROWN_EDGE);
            }
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 4.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.ENTITY_EFFECT, location, 30, CROWN_COLOR);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.8f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.ENTITY_EFFECT, location, 60, CROWN_COLOR);
        ParticleCaster.spawn(null, location.getWorld(), Utils.DUST, location, 40, 0.3, 0.3, 0.3, 0.0, CROWN_DUST);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.6f);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.crownofdawn.book_name"))
                .addCustomLoreLine(lang().get("spells.crownofdawn.lore1"))
                .addCustomLoreLine(lang().get("spells.crownofdawn.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}
