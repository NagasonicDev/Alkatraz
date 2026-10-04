package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.components.SpellBlockComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.components.SpellComponentType;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.AttackType;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Circle 5 FIRE burst: a focused, piercing beam that travels the caster's exact look
 * direction, burning everything in its line at full power exactly once.
 *
 * <h2>Why one hit and not sixteen</h2>
 * The beam's scheduler runs every tick, but that is its <em>travel</em>, not a damage
 * cadence. Each run advances the front by {@code beam_speed} and tests only the slice the
 * front has not yet passed, and {@link AttackProperties#hasHit} retires each target for the
 * whole cast. A body sitting inside the beam for all sixteen ticks takes exactly one
 * {@link SpellDamageUtil#damageWithSpell}. That is what buys {@code burstBudget(5) = 22.1}
 * of throughput through everything in the line at the full 10.8 per body, and it is why
 * this spell declares no {@code hit_count} key at all.
 *
 * <h2>Colours</h2>
 * Sourced from {@link ElementPalette}'s FIRE palette rather than from per-class literals:
 * the palette pre-builds its dusts once per class instead of once per tick per particle,
 * and a hand-rolled RGB triple here would be the fourth competing shade of orange in the
 * roster. The lance is told apart from the other three new fire spells by its SHAPE - a
 * narrow travelling column - not by its hue.
 */
public class FlameLance extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.FIRE);

    /** The visible column. */
    private static final Particle.DustOptions BEAM_DUST = PALETTE.coreDust();
    /** The leading point, one shade brighter than the trail it drags. */
    private static final Particle.DustOptions BEAM_TIP = PALETTE.accentDust();

    /**
     * Offsets across the column, as fractions of {@code beam_radius}. Four lanes, not a
     * solid slab: a narrow lance reads as a lance, a wide one reads as a fireball.
     */
    private static final double[] LANE_OFFSETS = {-0.5, -1.0 / 6.0, 1.0 / 6.0, 0.5};

    /** Steps drawn along the newly travelled slice each tick. */
    private static final int SLICE_STEPS = 4;

    /** Smoke is drawn on every third tick rather than every tick, to keep the beam readable. */
    private static final int SMOKE_EVERY = 3;

    private double beamLength;
    private double beamSpeed;
    private double beamRadius;
    private double hitRadius;
    private double knockback;
    private double knockbackY;
    private int igniteTicks;

    public FlameLance(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/flame_lance.yml");
        Alkatraz.getInstance().saveConfig("spells/flame_lance_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/flame_lance.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.beamLength = spellConfig.getDouble("beam_length", 24.0);
        this.beamSpeed = spellConfig.getDouble("beam_speed", 1.5);
        this.beamRadius = spellConfig.getDouble("beam_radius", 1.25);
        this.hitRadius = spellConfig.getDouble("hit_radius", 1.75);
        this.igniteTicks = spellConfig.getInt("ignite_ticks", 40);
        this.knockback = spellConfig.getDouble("knockback", 0.45);
        this.knockbackY = spellConfig.getDouble("knockback_y", 0.28);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        double length = getModifiedStat(caster, "beam_length", beamLength);
        double width = getModifiedStat(caster, "hit_radius", hitRadius);
        Vector dir = Utils.safeNormalize(caster.getEyeLocation().getDirection());
        if (dir == null) {
            cancelCast(caster);
            return;
        }
        launchBeam(caster, wand, dir, length, width, getWandPower(wand, caster));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        Vector dir = Utils.safeNormalize(caster.getTarget().getEyeLocation().toVector()
                .subtract(caster.getEyeLocation().toVector()));
        if (dir == null) return;
        // Defaults only, never a player's selected option - the Fireball.java:195 rule.
        launchBeam(caster, wand, dir, beamLength, hitRadius, getWandPowerOrDefault(wand));
    }

    /**
     * The whole beam, shared by both cast paths. One {@link AttackProperties} for the entire
     * beam, so a single counter kills every body still ahead of the front.
     *
     * @param caster     the casting entity
     * @param wand       the casting wand, or null on a mob cast
     * @param dir        the already-normalised look direction
     * @param length     how far the front travels, in blocks
     * @param width      the perpendicular damage distance from the beam axis
     * @param wandPower  the wand scaling factor, applied once when the properties are seeded
     */
    private void launchBeam(LivingEntity caster, ItemStack wand, Vector dir, double length,
                            double width, double wandPower) {
        double speed = beamSpeed;
        if (speed <= 0.0 || length <= 0.0) return;

        // Seeded with RAW power. getPower is called exactly once per damaged target below,
        // never here - calling it at cast time as well would square the option and affinity terms.
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster),
                getBasePower() * wandPower, AttackType.MAGIC);

        int travelTicks = (int) Math.ceil(length / speed);
        Location origin = caster.getEyeLocation().clone();

        // ONE component for the whole beam, not one per tick, so a barrier eats the lance
        // as a single thing instead of sixteen separate ones.
        SpellBlockComponent component = new SpellBlockComponent(
                this, props, caster, wand, SpellComponentType.OFFENSE,
                origin.getBlock(), Math.max(0.5, width), travelTicks + 2);
        SpellComponentHandler.register(component);

        Vector right = dir.clone().rotateAroundY(Math.PI / 2);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (caster.isDead()) {
                    cancel();
                    return;
                }
                if (ticks >= travelTicks || props.isCountered() || props.isCancelled()) {
                    cancel();
                    return;
                }
                double travelled = Math.min(length, speed * ticks);
                Location front = origin.clone().add(dir.clone().multiply(travelled));
                if (front.getBlock().getType().isSolid()) {
                    cancel();       // the beam stops at the first solid block
                    return;
                }
                double fromTravel = ticks <= 0 ? 0.0 : Math.min(length, speed * (ticks - 1));
                drawSlice(caster, origin, dir, right, fromTravel, travelled, ticks);
                damageSlice(origin, front, props, caster, wand, width, dir);
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * Paints only the newly travelled slice, so the beam grows instead of repainting its
     * whole length on every one of the sixteen ticks.
     */
    private void drawSlice(LivingEntity caster, Location origin, Vector dir, Vector right,
                           double fromTravel, double toTravel, int tick) {
        World world = origin.getWorld();
        if (world == null) return;
        for (int s = 0; s <= SLICE_STEPS; s++) {
            double travelled = fromTravel + (toTravel - fromTravel) * (s / (double) SLICE_STEPS);
            Location mid = origin.clone().add(dir.clone().multiply(travelled));
            for (double offset : LANE_OFFSETS) {
                Location p = mid.clone().add(right.clone().multiply(offset * beamRadius));
                ParticleCaster.spawn(caster, world, Utils.DUST, p, 0, BEAM_DUST);
            }
            if (tick % SMOKE_EVERY == 0) {
                ParticleCaster.spawn(caster, world, Utils.LARGE_SMOKE, mid, 1, 0.1, 0.05, 0.1, 0.005);
            }
        }
        Location tip = origin.clone().add(dir.clone().multiply(toTravel));
        ParticleCaster.spawn(caster, world, Utils.FLAME, tip, 3, 0.12, 0.12, 0.12, 0);
        ParticleCaster.spawn(caster, world, Utils.DUST, tip, 0, BEAM_TIP);
    }

    /**
     * The load-bearing block. Tests only the slice {@code [a, b]} just drawn, so the nearby
     * entity scan around the front cannot re-test a segment the beam already passed, and
     * gates on {@code hasHit} so a body takes one hit for the entire cast.
     */
    private void damageSlice(Location a, Location b, AttackProperties props, LivingEntity caster,
                             ItemStack wand, double width, Vector dir) {
        Vector axis = b.toVector().subtract(a.toVector());
        double axisSq = axis.lengthSquared();
        if (axisSq <= 1.0E-6) return;
        for (LivingEntity target : Utils.getNearbyLivingEntities(b, width + 2.0)) {
            if (target.equals(caster) || target.isDead()) continue;
            // Mid-body, not the feet: a target on flat ground is 0.9 blocks above the axis.
            Vector to = target.getLocation().add(0, 0.9, 0).toVector().subtract(a.toVector());
            double along = to.dot(axis);
            if (along < 0.0 || along > axisSq) continue;
            double perpSq = to.lengthSquared() - along * along / axisSq;
            if (perpSq > width * width) continue;
            if (props.hasHit(target)) continue;                 // ONE hit per target, whole beam
            props.hit(target);

            SpellDamageUtil.damageWithSpell(target,
                    getPower(caster, target, props.getRemainingPower()),
                    caster, wand, FlameLance.this);
            target.setFireTicks(igniteTicks);                   // ONE discrete ignition
            target.setVelocity(dir.clone().multiply(knockback).setY(knockbackY));
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.FLAME, location, 15);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.LARGE_SMOKE, location, 30, 0.2, 0.2, 0.2, 0.02);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.flamelance.book_name"))
                .addCustomLoreLine(lang().get("spells.flamelance.lore1"))
                .addCustomLoreLine(lang().get("spells.flamelance.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 5))
                .build();
    }
}