package me.nagasonic.alkatraz.spells.implementation;

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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Circle 6 AIR burst: one concussive blast at the loudest single-hit volume
 * circle 6 has. It charges in the caster's hands for three seconds, detonates
 * once, and shoves everything nearby radially outward and slightly up.
 *
 * <h2>One application, and only one</h2>
 * The damage branch lives inside a loop that runs on the clap tick and on no
 * other tick. The charge phase and the afterglow contain no damage branch at all,
 * so a target that walks out of the five-block radius and back in before the
 * runnable ends is <em>not</em> damaged again. That is why the yml declares no
 * {@code hit_count}: an absent key means one damage application per cast, which
 * is exactly what is true here. This is the {@code flame_lance} deviation shape -
 * the full circle-6 ladder power spent through one line, still one hit per
 * target, so {@code hasHit} applies and no hit count is needed.
 *
 * <h2>Damage contract</h2>
 * <ul>
 *   <li><b>One getPower per damage instance.</b> {@link AttackProperties} is seeded
 *       with RAW base power; the two-arg overload is never called. The three-arg
 *       form runs once per struck target.</li>
 *   <li><b>Deduped per target per cast.</b> {@code props.hasHit(le)} /
 *       {@code props.hit(le)}, inside a runnable that spans twelve seconds.</li>
 *   <li><b>power reaches damage.</b> {@code props.getRemainingPower()} is the raw
 *       {@code power} key and goes straight to
 *       {@link SpellDamageUtil#damageWithSpell}. There is no separate damage key.</li>
 *   <li><b>One shared AttackProperties per cast.</b> Created once in
 *       {@link #launch}, captured by the single runnable.</li>
 * </ul>
 *
 * <h2>Radial, never along the crosshair</h2>
 * The displacement is computed from the blast centre outward, so a target is
 * always pushed away from the caster rather than shoved down the caster's aim.
 * That horizontal shove is {@code air_burst}'s job at circle 1 and
 * {@code updraft}'s vertical counterpart at circle 3; thunderclap takes neither
 * axis and instead spends its whole reach on the radius.
 *
 * <h2>No terrain, ever</h2>
 * AIR is forbidden from TERRAIN (spec 5.2). This class contains no
 * {@code TerrainEditor} call, no {@code setType(} and no {@code Material}
 * reference.
 */
public class Thunderclap extends AttackSpell {

    /** Gold. No other air spell uses a warm hue - updraft is ice, cyclone_nest azure. */
    private static final Color CLAP_TINT = Color.fromRGB(255, 235, 150);
    private static final Color CLAP_CORE = Color.fromRGB(255, 250, 225);
    private static final Particle.DustOptions CLAP_DUST = new Particle.DustOptions(CLAP_TINT, 1.0F);
    private static final Particle.DustOptions CLAP_CORE_DUST = new Particle.DustOptions(CLAP_CORE, 1.3F);
    private static final Particle.DustOptions CLAP_FADE = new Particle.DustOptions(CLAP_TINT, 0.5F);

    /** Charge-phase dust points scale from this to {@code CLAP_CHARGE_POINTS_MAX}. */
    private static final int CLAP_CHARGE_POINTS_MIN = 8;
    private static final int CLAP_CHARGE_POINTS_MAX = 20;
    /** The charge glow's radius grows from this fraction of one block to the full three. */
    private static final double CLAP_CHARGE_RADIUS_BASE = 0.4;
    private static final double CLAP_CHARGE_RADIUS_GROWTH = 2.6;
    /** How often the rising charge whine repeats, in ticks. */
    private static final int CLAP_CHARGE_SOUND_EVERY = 15;
    /** Points on the blast body's fibonacci sphere. */
    private static final int CLAP_BODY_POINTS = 40;
    /** The blast body is drawn at this fraction of the blast radius. */
    private static final double CLAP_BODY_FRACTION = 0.8;
    /** Cloud count on the ground shockwave ring. */
    private static final int CLAP_RING_CLOUD_COUNT = 4;
    /** Afterglow smoke is drawn every this many ticks. */
    private static final int CLAP_AFTERGLOW_EVERY = 4;
    /** Afterglow smoke count. */
    private static final int CLAP_AFTERGLOW_COUNT = 6;
    /** Tick from which the afterglow switches to the dimmer {@link #CLAP_FADE} dust. */
    private static final int CLAP_FADE_FROM_TICK = 120;
    /** A target this close to the blast centre has no defined radial direction, so it is only lifted. */
    private static final double CLAP_DEAD_CENTRE_SQUARED = 0.01;
    /** Barrier/counter component lifetime, in ticks. */
    private static final int CLAP_COMPONENT_TICKS = 4;

    private double chargeTicks;
    private double clapTicks;
    private double clapRadius;
    private double clapHeight;
    private double clapPush;
    private double clapLift;

    public Thunderclap(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/thunderclap.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/thunderclap.yml").get();
        loadCommonConfig(spellConfig);
        // No options file: the only axis a player would want to tune is the blast
        // radius, which is exactly the axis that would buy damage area for free.
        loadOptions();
        this.chargeTicks = spellConfig.getDouble("charge_ticks", 3.0);
        this.clapTicks = spellConfig.getDouble("clap_ticks", 12.0);
        this.clapRadius = spellConfig.getDouble("clap_radius", 5.0);
        this.clapHeight = spellConfig.getDouble("clap_height", 3.5);
        this.clapPush = spellConfig.getDouble("clap_push", 1.8);
        this.clapLift = spellConfig.getDouble("clap_lift", 0.45);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        launch(caster, wand, getWandPower(wand, caster));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;
        launch(caster, wand, getWandPowerOrDefault(wand));
    }

    /**
     * Charge, clap, afterglow - one runnable, three phases, one damage tick.
     * Shared by both cast paths so a magic mob's thunderclap is the same picture.
     */
    private void launch(LivingEntity caster, ItemStack wand, double wandPower) {
        final Location centre = Utils.castLocation(caster);
        final World world = centre.getWorld();
        if (world == null) return;

        final AttackProperties props = new AttackProperties(caster, centre,
                getBasePower() * wandPower, AttackType.MAGIC);

        final double radius = clapRadius;
        final double height = clapHeight;
        final double push = clapPush;
        final double lift = clapLift;
        final int chargeEnd = Math.max(1, (int) Math.round(chargeTicks * 20.0));
        final int ticks = Math.max(chargeEnd + 1, (int) Math.round(clapTicks * 20.0));

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                // Abort and expiry are checked at the TOP of every tick, before any
                // phase work, so a countered cast never reaches the clap.
                if (props.isCountered() || props.isCancelled() || t >= ticks) {
                    afterglow(caster, world, centre, radius, t, true);
                    cancel();
                    return;
                }
                if (caster.isDead() || (caster instanceof Player player && !player.isOnline())) {
                    afterglow(caster, world, centre, radius, t, true);
                    cancel();
                    return;
                }

                if (t < chargeEnd) {
                    charge(caster, world, centre, t, chargeEnd);
                } else if (t == chargeEnd) {
                    clap(caster, wand, props, world, centre, radius, height, push, lift);
                } else {
                    afterglow(caster, world, centre, radius, t, false);
                }
                t++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * Phase A. A dust shell closing on the caster's hands and a witch-puff at the
     * centre, rising in size and brightness. <b>No damage and no velocity here</b>
     * - the charge is the tell, the clap is the payload.
     */
    private void charge(LivingEntity caster, World world, Location centre, int tick, int chargeEnd) {
        double progress = tick / (double) chargeEnd;
        double shellRadius = CLAP_CHARGE_RADIUS_BASE + CLAP_CHARGE_RADIUS_GROWTH * progress;
        int points = CLAP_CHARGE_POINTS_MIN
                + (int) (progress * (CLAP_CHARGE_POINTS_MAX - CLAP_CHARGE_POINTS_MIN));
        for (Location point : ParticleUtils.sphere(centre, shellRadius, points)) {
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, CLAP_DUST);
        }
        ParticleCaster.spawn(caster, world, Utils.WITCH, centre, 3, 0.2, 0.2, 0.2, 0.0);
        if (tick % CLAP_CHARGE_SOUND_EVERY == 0) {
            // There is no Sound.ENTITY_WITHER_CHARGE: Bukkit names the wither's
            // charge-up sound ENTITY_WITHER_SPAWN (vanilla entity.wither.spawn),
            // which is the same sound the original constant was reaching for.
            // Do not "correct" this back - it does not compile.
            world.playSound(centre, Sound.ENTITY_WITHER_SPAWN, 0.4f, 0.6f + 0.6f * (float) progress);
        }
    }

    /**
     * Phase B, and the ONLY damage branch in the class. It runs on exactly one tick
     * of the runnable, which is what makes a single application per target a
     * structural property rather than a claim.
     */
    private void clap(LivingEntity caster, ItemStack wand, AttackProperties props, World world,
                      Location centre, double radius, double height, double push, double lift) {
        ParticleCaster.spawn(caster, world, Utils.EXPLOSION_EMITTER, centre, 1);
        world.playSound(centre, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.6f);

        // The visible shockwave ring on the ground.
        for (Location point : ParticleUtils.circle(centre, radius, 1, centre.getYaw(), 0)) {
            ParticleCaster.spawn(caster, world, Utils.CLOUD, point, CLAP_RING_CLOUD_COUNT,
                    radius, 0.15, radius, 0.05);
        }
        // The blast body.
        for (Location point : ParticleUtils.fibonacciSphere(centre, radius * CLAP_BODY_FRACTION, CLAP_BODY_POINTS)) {
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, CLAP_CORE_DUST);
        }

        SpellComponentHandler.register(new SpellParticleComponent(
                Thunderclap.this, props, caster, wand, SpellComponentType.OFFENSE,
                centre.clone(), radius, CLAP_COMPONENT_TICKS));

        for (LivingEntity le : Utils.getNearbyLivingEntities(centre, radius, height, radius)) {
            if (le.equals(caster) || le.isDead()) continue;
            if (props.hasHit(le)) continue;
            props.hit(le);

            // RADIAL displacement: outward from the blast, never toward or along
            // the crosshair. A target standing exactly on the centre has no
            // defined radial direction, so it only gets the lift.
            Vector outward = le.getLocation().toVector().subtract(centre.toVector());
            outward.setY(0.0);
            Vector radial = outward.lengthSquared() < CLAP_DEAD_CENTRE_SQUARED
                    ? new Vector(0.0, 0.0, 0.0)
                    : Utils.safeNormalize(outward);
            le.setVelocity(new Vector(radial.getX() * push, lift, radial.getZ() * push));

            SpellDamageUtil.damageWithSpell(le,
                    getPower(caster, le, props.getRemainingPower()),
                    caster, wand, Thunderclap.this);
        }
    }

    /**
     * Phase C. Particles and a fading dust shell. No damage, no velocity - which is
     * why a target re-entering the radius during the afterglow takes nothing. The
     * shell switches to the dimmer {@link #CLAP_FADE} halfway through the window.
     */
    private void afterglow(LivingEntity caster, World world, Location centre, double radius,
                           int tick, boolean ending) {
        ParticleCaster.spawn(caster, world, Utils.DUST, centre, 0,
                ending || tick >= CLAP_FADE_FROM_TICK ? CLAP_FADE : CLAP_DUST);
        if (ending) return;
        if (tick % CLAP_AFTERGLOW_EVERY != 0) return;
        ParticleCaster.spawn(caster, world, Utils.SMOKE, centre, CLAP_AFTERGLOW_COUNT,
                radius * 0.5, 0.6, radius * 0.5, 0.02);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.EXPLOSION, location, 15);
        ParticleCaster.spawn(caster, location.getWorld(), Utils.DUST, location, 0, CLAP_CORE_DUST);
        location.getWorld().playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.EXPLOSION_EMITTER, location, 1);
        ParticleCaster.spawn(null, location.getWorld(), Utils.DUST, location, 40, 0.6, 0.6, 0.6, 0.12, CLAP_DUST);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.7f);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.thunderclap.book_name"))
                .addCustomLoreLine(lang().get("spells.thunderclap.lore1"))
                .addCustomLoreLine(lang().get("spells.thunderclap.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}