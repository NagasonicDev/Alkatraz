package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
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
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Whirlpool extends AttackSpell {

    /**
     * The single WATER palette. This spell used to carry its own {@code (30,120,200)} in two
     * separate hoisted constants, plus a third copy built inline inside {@code circleAction} on
     * every tick. Nothing here may name an RGB value.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);

    /** Points on the contracting outer ring. */
    private static final int RING_POINTS = 32;
    /** Points on the descending inner spiral that gives the tread its direction. */
    private static final int INNER_SPIRAL_POINTS = 12;
    /** Ticks the contracting ring takes to fall from full radius to nothing. */
    private static final int CONTRACT_PERIOD = 40;
    /** Fraction of the field radius the inner spiral starts at. */
    private static final double INNER_START_FRACTION = 0.8;
    /** How high the inner spiral starts, and how far it descends, in blocks. */
    private static final double INNER_TOP = 2.5;
    private static final double INNER_BOTTOM = 0.2;
    /** Radians the whole formation turns per tick. The ring counter-turns against the spiral. */
    private static final double SPIN_PER_TICK = 0.4;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private double radius;
    private double duration;
    private double pullStrength;

    public Whirlpool(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/whirlpool_options.yml");
        Alkatraz.getInstance().save("spells/whirlpool.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/whirlpool.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.radius = spellConfig.getDouble("radius");
        this.duration = spellConfig.getDouble("duration");
        this.pullStrength = spellConfig.getDouble("pull_strength");
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        // RAW base power. `power` now REACHES damage - it used to be thrown into
        // AttackProperties and never used, while a separate `damage_per_tick` key
        // did the damaging. That is a dead `power:` key.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * getWandPower(wand, caster),
                AttackType.MAGIC
        );

        Location targetLoc = caster.getTargetBlock(null, 20).getLocation().add(0.5, 1, 0.5);
        double activeRadius = getModifiedStat(caster, "radius", radius);
        double activeDuration = getModifiedStat(caster, "duration", duration);
        double activePull = getModifiedStat(caster, "pull_strength", pullStrength);

        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_DROWNED_SWIM, 1.5f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;
            // SUSTAIN CADENCE LEDGER. NOT `long lastDamageTick`: that is a
            // per-FIELD gate and only one entity in the field would ever be
            // damaged. This is per-TARGET. See "SUSTAIN cadence: hasHit must not
            // gate the repeats" at the top of this wave.
            final Map<UUID, Long> nextStrikeTick = new HashMap<>();
            final Vector centerVec = targetLoc.toVector();

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || ticks >= activeDuration * 20) {
                    targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 0.6f);
                    cancel();
                    return;
                }

                drawVortex(caster, targetLoc, activeRadius, ticks, angle);

                SpellParticleComponent comp = new SpellParticleComponent(
                        Whirlpool.this,
                        props,
                        caster,
                        wand,
                        SpellComponentType.OFFENSE,
                        targetLoc,
                        activeRadius,
                        4
                );
                SpellComponentHandler.register(comp);

                if (ticks % 2 == 0) {
                    ParticleCaster.spawn(caster, targetLoc.getWorld(), Utils.BUBBLE_COLUMN_UP, targetLoc, 8, activeRadius, 2, activeRadius, 0);
                }

                if (ticks % 3 == 0) {
                    ParticleCaster.spawn(caster, targetLoc.getWorld(), Utils.SPLASH, targetLoc, 15, activeRadius, 0.5, activeRadius, 0);
                }

                for (Entity entity : targetLoc.getWorld().getNearbyEntities(targetLoc, activeRadius, activeRadius, activeRadius)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;

                    Vector pull = centerVec.subtract(le.getLocation().toVector());
                    double dist = pull.length();
                    if (dist < 0.5) continue;

                    pull = Utils.safeNormalize(pull).multiply(activePull * (1.0 - dist / activeRadius));
                    pull.setY(0.2);
                    Vector velocity = le.getVelocity();
                    le.setVelocity(velocity.add(pull));

                    if (dist < 1.5) {
                        // SUSTAIN CADENCE: a field damages a target at most once every
                        // SpellAttackContext.DAMAGE_INTERVAL_TICKS. The old code damaged
                        // every tick for 160 ticks = 160 damage. 8 hits x 2.75 = 22, which
                        // is exactly the circle-4 SUSTAIN budget.
                        Long nextStrike = nextStrikeTick.get(le.getUniqueId());
                        if (nextStrike == null || nextStrike <= ticks) {
                            nextStrikeTick.put(le.getUniqueId(), ticks + SpellAttackContext.DAMAGE_INTERVAL_TICKS);
                            // Still called, so one counter kills the whole field.
                            props.hit(le);
                            SpellDamageUtil.damageWithSpell(le, getPower(caster, le, props.getRemainingPower()),
                                    caster, wand, Whirlpool.this);
                            if (Math.random() < 0.3) {
                                ParticleCaster.spawn(caster, le.getWorld(), Utils.SPLASH, le.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
                            }
                        }
                    }
                }

                angle += SPIN_PER_TICK;
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;

        double wandp = getWandPowerOrDefault(wand);
        // RAW base power - `power` reaches damage, once per SUSTAIN interval.
        AttackProperties props = new AttackProperties(
                caster,
                Utils.castLocation(caster),
                getBasePower() * wandp,
                AttackType.MAGIC
        );

        if (caster.getTarget() == null) return;
        Location targetLoc = caster.getTarget().getLocation();

        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_DROWNED_SWIM, 1.5f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;
            // SUSTAIN CADENCE LEDGER, per-TARGET. See the player path above.
            final Map<UUID, Long> nextStrikeTick = new HashMap<>();
            final Vector centerVec = targetLoc.toVector();

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || ticks >= duration * 20) {
                    cancel();
                    return;
                }

                drawVortex(caster, targetLoc, radius, ticks, angle);

                for (Entity entity : targetLoc.getWorld().getNearbyEntities(targetLoc, radius, radius, radius)) {
                    if (entity.equals(caster)) continue;
                    if (!(entity instanceof LivingEntity le)) continue;

                    Vector pull = centerVec.subtract(le.getLocation().toVector());
                    double dist = pull.length();
                    if (dist < 0.5) continue;

                    pull = Utils.safeNormalize(pull).multiply(pullStrength * (1.0 - dist / radius));
                    pull.setY(0.2);
                    Vector velocity = le.getVelocity();
                    le.setVelocity(velocity.add(pull));

                    if (dist < 1.5) {
                        // SUSTAIN CADENCE: one hit per DAMAGE_INTERVAL_TICKS per target.
                        // The pull above still runs every tick - only damage is gated.
                        Long nextStrike = nextStrikeTick.get(le.getUniqueId());
                        if (nextStrike == null || nextStrike <= ticks) {
                            nextStrikeTick.put(le.getUniqueId(), ticks + SpellAttackContext.DAMAGE_INTERVAL_TICKS);
                            props.hit(le);
                            SpellDamageUtil.damageWithSpell(le, getPower(caster, le, props.getRemainingPower()),
                                    caster, wand, Whirlpool.this);
                        }
                    }
                }

                angle += SPIN_PER_TICK;
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * One frame of the vortex, shared by the player and mob paths so a magic mob's whirlpool is
     * the same picture as a player's rather than a sparser variant of it.
     *
     * <p><b>The shape is a CONTRACTING ring.</b> {@code water_pulse} draws the same builder with
     * a monotonically increasing radius; here the radius falls to zero over {@link #CONTRACT_PERIOD}
     * ticks and then snaps back out. The sign is the only thing that distinguishes the two spells,
     * so nothing below may turn it around.
     *
     * <p>A pure radius change still reads as a shrinking circle rather than as suction, so the
     * ring is paired with a descending inner spiral in {@code accent} that turns the <i>opposite</i>
     * way. That counter-rotation plus the descent is the tread: the eye follows something going
     * down and in, which is what tells the player they are being pulled rather than watched.
     *
     * @param caster      the casting entity, used for per-player density falloff
     * @param centre      the field's centre
     * @param fieldRadius the field's full radius in blocks
     * @param ticks       ticks elapsed, used for the contraction cycle and the spin
     * @param angle       accumulated rotation in radians
     */
    private void drawVortex(LivingEntity caster, Location centre, double fieldRadius, int ticks, double angle) {
        double ringRadius = fieldRadius * (1.0 - (ticks % CONTRACT_PERIOD) / (double) CONTRACT_PERIOD);

        // Shadow offset 0.35 lays a dark counter-turning ring on the ground under the tread.
        SpellVfx.ring(Whirlpool.this, caster, centre, ringRadius, RING_POINTS, angle, 0.35);

        World world = centre.getWorld();
        Location point = centre.clone();
        for (int i = 0; i < INNER_SPIRAL_POINTS; i++) {
            double t = i / (double) (INNER_SPIRAL_POINTS - 1);
            double reach = ringRadius * INNER_START_FRACTION * (1.0 - t);
            double y = INNER_TOP - (INNER_TOP - INNER_BOTTOM) * t;
            double a = -angle * 1.5 + t * Math.PI * 2.0;
            point.setX(centre.getX() + Math.cos(a) * reach);
            point.setY(centre.getY() + y);
            point.setZ(centre.getZ() + Math.sin(a) * reach);
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, PALETTE.accentDust());
        }
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.SPLASH, location, 30, 0.5, 0.5, 0.5, 0);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.SPLASH, location, 50, 1, 1, 1, 0.5);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.whirlpool.book_name"))
                .addCustomLoreLine(lang().get("spells.whirlpool.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }
}
