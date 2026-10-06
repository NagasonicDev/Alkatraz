package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
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

/**
 * Circle 6 LIGHT: brand one target, and the mark <b>detonates on expiry</b>.
 *
 * <p>Nothing lands at cast time, so burst becomes a timing skill - the damage is worth
 * {@code power} only if the branded target is still alive and still in the fight when the
 * mark goes out. The detonation fires exactly once regardless of range: the brand binds the
 * target, it does not tether them to the caster, so a mark that walked to the other side of
 * the map still goes off.
 *
 * <h2>Damage contract</h2>
 * One {@link SpellAttackContext} for the whole brand, created at cast time and read by the
 * detonation. No {@code getPower} at cast time. {@code context.power(target)} is called once
 * for the branded target and once per splash bystander, each after a final
 * {@code context.tick()}. Worst case per cast to the branded target: {@code 14.8},
 * against {@code burstBudget(6) = 34.1}. A splash bystander takes {@code splash_fraction} of
 * that, which is a second application to a <em>different</em> entity - not a second hit on
 * the branded target - so no {@code hit_count} key is declared.
 *
 * <p>This class contains zero references to the undead roster and must stay that way: the
 * full x2.0 against undead arrives for free inside {@code AttackSpell.getPower}.
 */
public class RadiantBrand extends AttackSpell {

    /** Amber-gold, distinct on screen from the other four light spells. */
    private static final Color BRAND_COLOR = Color.fromRGB(255, 170, 40);
    private static final Particle.DustOptions BRAND_DUST = new Particle.DustOptions(BRAND_COLOR, 0.6F);
    private static final Particle.DustOptions BRAND_CORE = new Particle.DustOptions(BRAND_COLOR, 1.0F);

    /** Radius of the ring drawn around the branded target. */
    private static final double MARK_RING_RADIUS = 1.1;
    /** Radians the mark ring turns per drawn frame, so it reads as spinning. */
    private static final double MARK_SPIN_STEP = 0.25;
    /** How often the beacon tone repeats while the mark holds, in ticks. */
    private static final int MARK_SOUND_INTERVAL = 20;

    private double brandDuration;
    private double brandRange;
    private double detonationRadius;
    private double splashFraction;
    private int particleInterval;

    public RadiantBrand(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/radiant_brand.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/radiant_brand.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.brandDuration = spellConfig.getDouble("brand_duration", 4.0);
        this.brandRange = spellConfig.getDouble("brand_range", 32.0);
        this.detonationRadius = spellConfig.getDouble("detonation_radius", 2.5);
        this.splashFraction = spellConfig.getDouble("splash_fraction", 0.5);
        this.particleInterval = Math.max(1, spellConfig.getInt("particle_interval", 2));
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double range = getModifiedStat(caster, "brand_range", brandRange);
        Entity aimed = Utils.getTargetEntity(caster, (int) range);
        if (!(aimed instanceof LivingEntity target) || target.isDead() || !target.isValid()
                || target.equals(caster)) {
            Utils.sendActionBar(caster, lang().get("spells.radiantbrand.no_target"));
            cancelCast(caster);
            return;
        }

        // ONE context for the whole brand: the mark and its detonation share it, so a single
        // counter denies both. No getPower here - the detonation is where scaling happens.
        SpellAttackContext context = SpellAttackContext.of(this, caster, wand, AttackType.MAGIC);
        int durationTicks = Math.max(1, (int) Math.round(brandDuration * 20.0));

        brand(caster, wand, context, target, durationTicks, detonationRadius, splashFraction);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        AttackProperties props = new AttackProperties(
                caster, Utils.castLocation(caster),
                getBasePower() * getWandPowerOrDefault(wand),
                AttackType.MAGIC);
        SpellAttackContext context = SpellAttackContext.of(this, props, caster, wand);
        brand(caster, wand, context, caster.getTarget(),
                Math.max(1, (int) Math.round(brandDuration * 20.0)), detonationRadius, splashFraction);
    }

    /**
     * The mark: one {@link BukkitRunnable} at period 1 that draws the brand and detonates once
     * on expiry.
     *
     * @param caster      the casting entity, for per-player particle density falloff
     * @param wand        the casting wand, or null on a mob cast
     * @param context     the single per-cast handle shared with the detonation
     * @param target      the branded entity
     * @param durationTicks how long the mark survives before it detonates
     * @param radius      the detonation's splash radius, in blocks
     * @param splash      the fraction of {@code power} each bystander takes
     */
    private void brand(LivingEntity caster, ItemStack wand, SpellAttackContext context,
                       LivingEntity target, int durationTicks, double radius, double splash) {
        new BukkitRunnable() {
            int elapsed = 0;
            double spin = 0.0;

            @Override
            public void run() {
                context.tick();

                if (caster.isDead() || (caster instanceof Player offline && !offline.isOnline())) {
                    cancel();
                    return;
                }
                if (context.isSpent()) {
                    cancel();
                    return;
                }

                elapsed++;

                // EXPIRY. Exactly once, and regardless of range: the brand is bound to the
                // target, not tethered to the caster.
                if (elapsed >= durationTicks) {
                    detonate(caster, wand, context, target, radius, splash);
                    cancel();
                    return;
                }

                // FIZZLE. A brand with nothing left to detonate into deals nothing at all.
                if (target.isDead() || !target.isValid()
                        || (target instanceof Player marked && !marked.isOnline())) {
                    if (caster instanceof Player watching) {
                        Utils.sendActionBar(watching, lang().get("spells.radiantbrand.faded"));
                    }
                    cancel();
                    return;
                }

                if (elapsed % particleInterval == 0) {
                    spin += MARK_SPIN_STEP;
                    Location center = target.getLocation().add(0, 0.4, 0);
                    World world = center.getWorld();
                    if (world == null) return;
                    for (Location point : ParticleUtils.circle(center, MARK_RING_RADIUS, 1.0f, (float) spin, 0f)) {
                        ParticleCaster.spawn(caster, world, Utils.DUST, point, 1, BRAND_DUST);
                    }
                    ParticleCaster.spawn(caster, world, Utils.ENTITY_EFFECT,
                            target.getLocation().add(0, 1.0, 0), 1, 0.1, 0.1, 0.1, 0.0, BRAND_COLOR);
                }

                if (elapsed % MARK_SOUND_INTERVAL == 0) {
                    caster.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.4f,
                            1.0f + 0.5f * (elapsed / (float) durationTicks));
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * The single detonation.
     *
     * <p>The branded target always takes the full {@code power}; nearby bystanders take
     * {@code splash} of it. Each of those is a separate application to a separate entity,
     * guarded by {@code context.hit}, and each calls {@code context.power(...)} exactly once
     * before scaling the result - never twice and then divided.
     *
     * @param caster  the casting entity
     * @param wand    the casting wand
     * @param context the single per-cast handle
     * @param target  the branded target
     * @param radius  the splash radius, in blocks
     * @param splash  the fraction of {@code power} each bystander takes
     */
    private void detonate(LivingEntity caster, ItemStack wand, SpellAttackContext context,
                          LivingEntity target, double radius, double splash) {
        Location at = target.getLocation().add(0, 1, 0);
        World world = at.getWorld();
        if (world == null) return;

        ParticleCaster.spawn(caster, world, Utils.DUST, at, 40, 0.4, 0.4, 0.4, 0.0, BRAND_CORE);
        ParticleCaster.spawn(caster, world, Utils.ENTITY_EFFECT, at, 30, 0.5, 0.5, 0.5, 0.0, BRAND_COLOR);
        ParticleCaster.spawn(caster, world, Utils.EXPLOSION_EMITTER, at, 4, 0.3, 0.3, 0.3, 0.0);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.6f);

        // The detonation is its own scheduler tick, so power() is legal once per target here.
        context.tick();

        if (context.hit(target)) {
            SpellDamageUtil.damageWithSpell(target, context.power(target), caster, wand, RadiantBrand.this);
        }
        for (Entity entity : world.getNearbyEntities(at, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity bystander)) continue;
            if (bystander.equals(target) || bystander.equals(caster)) continue;
            if (bystander.isDead() || !bystander.isValid()) continue;
            if (!context.hit(bystander)) continue;
            double scaled = context.power(bystander) * splash;
            SpellDamageUtil.damageWithSpell(bystander, scaled, caster, wand, RadiantBrand.this);
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.ENTITY_EFFECT, location, 20, BRAND_COLOR);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.4f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.DUST, location, 30, 0.3, 0.3, 0.3, 0.0, BRAND_CORE);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.7f);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.radiantbrand.book_name"))
                .addCustomLoreLine(lang().get("spells.radiantbrand.lore1"))
                .addCustomLoreLine(lang().get("spells.radiantbrand.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
