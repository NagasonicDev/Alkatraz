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
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
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
 * Circle 3 AIR burst: a travelling column of rising air that tears everything it
 * touches off the ground.
 *
 * <h2>Filling AIR's circle-3 gap</h2>
 * {@code wind_vortex} is a circle-2 SUSTAIN and {@code wind_barrier} a circle-4
 * DENIAL, so circle 3 had nothing. {@code updraft} is the element's first
 * <em>knock-up</em>: {@code air_burst} shoves along the caster's aim, this goes
 * straight up. The two cannot be confused.
 *
 * <h2>Why the lift and the damage are separate numbers</h2>
 * {@code lift_velocity} (1.35 blocks/tick) and {@code power} (5.5) are
 * deliberately unrelated constants. {@code air_burst} writes its <em>damage
 * number</em> into the target's velocity, which means every point of damage
 * scaling is silently also mobility scaling. Here the lift is a fixed constant,
 * so no amount of gear can turn updraft into a launcher, and no amount of
 * mobility tuning can inflate its damage.
 *
 * <h2>Damage contract</h2>
 * <ul>
 *   <li><b>One getPower per damage instance.</b> {@link AttackProperties} is seeded
 *       with RAW base power ({@code getBasePower() * getWandPower(wand, caster)});
 *       the two-arg {@code getPower} overload is never called. The three-arg form
 *       runs exactly once per struck target.</li>
 *   <li><b>Deduped per target per cast.</b> {@code props.hasHit(le)} /
 *       {@code props.hit(le)} guard the damage branch. The runnable spans
 *       {@code lift_duration} seconds at a one-tick period, so this guard is
 *       mandatory - without it a target standing in the column would take
 *       {@code power} two hundred times.</li>
 *   <li><b>power reaches damage.</b> {@code props.getRemainingPower()} is the raw
 *       {@code power} key and is handed straight to
 *       {@link SpellDamageUtil#damageWithSpell}. There is no
 *       {@code damage_per_tick} key anywhere in the yml.</li>
 *   <li><b>One shared AttackProperties per cast.</b> Created once in
 *       {@link #launch} and captured by the single runnable, so a counter kills
 *       the whole column.</li>
 * </ul>
 *
 * <h2>No terrain, ever</h2>
 * AIR is forbidden from TERRAIN (spec 5.2). This class contains no
 * {@code TerrainEditor} call, no {@code setType(} and no {@code Material}
 * reference: the only thing it changes is where entities are.
 */
public class Updraft extends AttackSpell {

    /**
     * Near-white ice blue, deliberately lighter and larger than {@code air_burst}'s
     * pure white at 0.4 or {@code air_blades}' (220, 235, 255) at 0.7, so a
     * circle-3 lift is unmistakable on screen against the circle-1 shove.
     */
    private static final Color LIFT_TINT = Color.fromRGB(190, 240, 255);
    private static final Particle.DustOptions LIFT_DUST = new Particle.DustOptions(LIFT_TINT, 0.9F);

    /** Rings drawn per tick at the head of the travelling column. */
    private static final int LIFT_LAYERS = 4;
    /** Points per ring. */
    private static final int LIFT_RING_POINTS = 12;
    /** Vertical spacing between the four rings, in blocks. */
    private static final double LIFT_LAYER_GAP = 0.6;
    /** How fast the rings spin, in radians per tick. */
    private static final double LIFT_SPIN = 0.35;
    /** How much of the hit radius each successive ring occupies. */
    private static final double LIFT_RING_SPREAD = 0.25;
    /** How much extra vertical reach the entity query gets above the column head. */
    private static final double LIFT_QUERY_HEADROOM = 1.5;
    /** Dust puff left behind when the column is cancelled or expires. */
    private static final int LIFT_DISPERSE_COUNT = 20;

    private double liftDuration;
    private double liftRange;
    private double liftRadius;
    private double liftVelocity;

    public Updraft(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/updraft.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/updraft.yml").get();
        loadCommonConfig(spellConfig);
        // updraft has no options file: every axis of it is fixed, and there is no
        // axis a player could tune that would not be free damage or free mobility.
        loadOptions();
        this.liftDuration = spellConfig.getDouble("lift_duration", 10.0);
        this.liftRange = spellConfig.getDouble("lift_range", 6.0);
        this.liftRadius = spellConfig.getDouble("lift_radius", 2.0);
        this.liftVelocity = spellConfig.getDouble("lift_velocity", 1.35);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        Vector dir = Utils.safeNormalize(caster.getEyeLocation().getDirection());
        launch(caster, wand, dir, getWandPower(wand, caster));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        Vector dir = Utils.safeNormalize(caster.getTarget().getLocation().toVector()
                .subtract(caster.getLocation().toVector()));
        launch(caster, wand, dir, getWandPowerOrDefault(wand));
    }

    /**
     * The whole column, shared by both cast paths so a magic mob's updraft is the
     * same picture as a player's. One {@link AttackProperties} for the whole run,
     * so countering kills the column and every lift still to come.
     *
     * @param caster    the casting entity, player or mob
     * @param wand      the wand the cast was made with
     * @param dir       the direction the column travels; the player path uses their
     *                  eye direction, the mob path the direction to its target
     * @param wandPower the resolved wand multiplier, RAW
     */
    private void launch(LivingEntity caster, ItemStack wand, Vector dir, double wandPower) {
        final Location origin = Utils.castLocation(caster);
        final World world = origin.getWorld();
        if (world == null) return;

        final AttackProperties props = new AttackProperties(caster, origin,
                getBasePower() * wandPower, AttackType.MAGIC);

        final double radius = liftRadius;
        final double velocity = liftVelocity;
        final double range = liftRange;
        final int ticks = Math.max(1, (int) Math.round(liftDuration * 20.0));
        // Dividing by (ticks - 1) puts the far end of the column exactly at
        // `range` on the last tick; a ticks of 1 would divide by zero, hence the max.
        final double travelDivisor = Math.max(1, ticks - 1);
        final Vector travel = dir.clone();

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (props.isCountered() || props.isCancelled() || t >= ticks) {
                    disperse(caster, origin);
                    cancel();
                    return;
                }
                if (caster.isDead() || (caster instanceof Player player && !player.isOnline())) {
                    disperse(caster, origin);
                    cancel();
                    return;
                }

                // The column starts in the caster's hands at t = 0 and reaches
                // `range` blocks along the aim on the last tick.
                Location column = origin.clone()
                        .add(travel.clone().multiply(range * t / travelDivisor));

                drawColumn(caster, world, column, radius, t);

                // One component per tick, so the field can be blocked and countered
                // while it is still travelling rather than only at its origin.
                SpellComponentHandler.register(new SpellParticleComponent(
                        Updraft.this, props, caster, wand, SpellComponentType.OFFENSE,
                        column.clone(), radius, 1));

                for (LivingEntity le : Utils.getNearbyLivingEntities(column, radius,
                        radius + LIFT_QUERY_HEADROOM, radius)) {
                    if (le.equals(caster) || le.isDead()) continue;

                    // DISPLACEMENT - every tick, unconditionally. This is what makes
                    // it a lift rather than a poke: a target that walks into the
                    // column is held up for as long as it stays there. The
                    // horizontal component is exactly 0.0, which is the whole
                    // mechanical difference from air_burst's crosshair shove.
                    le.setVelocity(new Vector(0.0, velocity, 0.0));

                    // DAMAGE - exactly once per target per cast.
                    if (props.hasHit(le)) continue;
                    props.hit(le);
                    SpellDamageUtil.damageWithSpell(le,
                            getPower(caster, le, props.getRemainingPower()),
                            caster, wand, Updraft.this);
                }
                t++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * Four rising rings at the head of the column, each wider than the one below,
     * plus a cloud puff on the axis. Visual only.
     */
    private void drawColumn(LivingEntity caster, World world, Location column, double radius, int tick) {
        for (int layer = 0; layer < LIFT_LAYERS; layer++) {
            double height = layer * LIFT_LAYER_GAP;
            double r = radius * (0.5 + LIFT_RING_SPREAD * layer);
            for (int i = 0; i < LIFT_RING_POINTS; i++) {
                double angle = 2 * Math.PI * i / LIFT_RING_POINTS + tick * LIFT_SPIN + layer * 0.5;
                Location point = column.clone().add(Math.cos(angle) * r, height, Math.sin(angle) * r);
                ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, LIFT_DUST);
            }
        }
        ParticleCaster.spawn(caster, world, Utils.CLOUD, column, 6, 0.3, 0.6, 0.3, 0.05);
    }

    /** The column collapsing: one cloud burst and a dust shell where the caster let it go. */
    private void disperse(LivingEntity caster, Location origin) {
        World world = origin.getWorld();
        if (world == null) return;
        ParticleCaster.spawn(caster, world, Utils.CLOUD, origin,
                LIFT_DISPERSE_COUNT, 0.6, 0.9, 0.6, 0.08);
        ParticleCaster.spawn(caster, world, Utils.DUST, origin, 0, LIFT_DUST);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.CLOUD, location, 15);
        ParticleCaster.spawn(caster, location.getWorld(), Utils.DUST, location, 0, LIFT_DUST);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.CLOUD, location, 30);
        ParticleCaster.spawn(null, location.getWorld(), Utils.DUST, location, 0, LIFT_DUST);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 2.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.updraft.book_name"))
                .addCustomLoreLine(lang().get("spells.updraft.lore1"))
                .addCustomLoreLine(lang().get("spells.updraft.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 3))
                .build();
    }
}