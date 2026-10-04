package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
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

/**
 * Circle 6 WATER burst. A riptide forms where the caster is looking and hauls one enemy
 * back along it.
 *
 * <h2>The pull is the mechanic, so the damage is scaled rather than counted</h2>
 * This is the only spell in the roster whose damage is a function of a measurement rather
 * than a count. The declared per-cast total is
 * {@code min(drag_damage_per_block * max_drag_distance, drag_damage_cap)}
 * = {@code min(4.0 * 12.0, 34.1)} = <b>34.1</b>, exactly {@code burstBudget(6)}. There is
 * deliberately no {@code hit_count} key: a target is damaged once per cast, so a hit count
 * would understate nothing and mislead about what the spell does.
 *
 * <p><b>{@code power} is the FLOOR of that range, not a replacement for it.</b> A target
 * dragged a tenth of a block takes 14.8 anyway, which is what keeps {@code power} live
 * config rather than dead: if the scaled value replaced it outright, a target that fights
 * the pull would take almost nothing and {@code power} would never reach
 * {@code damageWithSpell}. The scaled total then climbs to 34.1 across the full twelve
 * blocks, and both ends sit inside the circle-6 budget.
 *
 * <p>The reachability invariant {@code drag_step_per_tick * drag_duration_ticks ==
 * max_drag_distance} (0.6 x 20 = 12.0) is load-bearing, not cosmetic: if the step and the
 * tick budget could not cover {@code max_drag_distance}, the declared 48.0 ceiling would be
 * unreachable and the yml would be lying about the spell.
 *
 * <h2>Why {@code Entity#teleport} and not {@code setVelocity}</h2>
 * Momentum-based dragging makes the displacement inexact, and this spell's damage is a
 * function of that displacement. Teleporting makes {@code dragged} in this class the same
 * number the player sees on screen, so the damage formula is verifiable in play.
 *
 * <h2>Water moves; water never reshapes</h2>
 * No {@code TerrainEditor}, no {@code Block#setType}, no fake blocks, no
 * {@code SpellBlockComponent}. Every visual is a particle. The only state changes in the
 * whole spell are {@code Entity#teleport} and {@code Entity#setVelocity} on the target, both
 * of which sit behind {@link Protection#teleport} for players.
 */
public class Undertow extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);

    /** The travelling crest that closes on the caster. */
    private static final Particle.DustOptions UNDERTOW_DUST = PALETTE.coreDust();
    /** The veil left along the target's path, a shade back so it never hides the crest. */
    private static final Particle.DustOptions UNDERTOW_TRAIL = PALETTE.accentDust();
    /** The undertow's own outline. */
    private static final Particle.DustOptions UNDERTOW_EDGE = PALETTE.deepDust();

    /** Points around the travelling crest. */
    private static final int CREST_POINTS = 12;
    /** Points along the veil drawn behind the target. */
    private static final int VEIL_POINTS = 10;
    /** Every fourth crest point also throws spray, so the front reads as breaking water. */
    private static final int CREST_SPRAY_EVERY = 4;
    /** The veil is repainted on every second tick; the crest is drawn every tick. */
    private static final int VEIL_EVERY = 2;
    /** Splash cadence on the target: every fifth tick, rising in pitch as the pull lands. */
    private static final int SPLASH_EVERY = 5;

    /**
     * Mobs get a shorter leash than a player's 24 blocks. A magic mob that could grab
     * something twenty-four blocks away would be a siege engine rather than a spell.
     */
    private static final double MOB_DRAG_RANGE = 12.0;

    private double dragRange;
    private int dragDurationTicks;
    private double dragStepPerTick;
    private double dragStopDistance;
    private double dragLift;
    private double dragDamagePerBlock;
    private double maxDragDistance;
    private double dragDamageCap;

    public Undertow(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /**
     * Circle 6 shape, read inline. No {@code saveConfig} call: this spell has no options
     * file, and {@code loadOptions()} falls through to the no-op {@code setupOptions()}.
     */
    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/undertow.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/undertow.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.dragRange = spellConfig.getDouble("drag_range", 24.0);
        this.dragDurationTicks = Math.max(1, spellConfig.getInt("drag_duration_ticks", 20));
        this.dragStepPerTick = spellConfig.getDouble("drag_step_per_tick", 0.6);
        this.dragStopDistance = spellConfig.getDouble("drag_stop_distance", 2.0);
        this.dragLift = spellConfig.getDouble("drag_lift", 0.35);
        this.dragDamagePerBlock = spellConfig.getDouble("drag_damage_per_block", 4.0);
        this.maxDragDistance = spellConfig.getDouble("max_drag_distance", 12.0);
        this.dragDamageCap = spellConfig.getDouble("drag_damage_cap", 34.1);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        int range = (int) getModifiedStat(caster, "drag_range", dragRange);
        Entity picked = Utils.getTargetEntity(caster, range);
        if (!(picked instanceof LivingEntity target) || target.equals(caster)) {
            cancelCast(caster);
            return;
        }

        launchDrag(target, caster, wand,
                getBasePower() * getWandPower(wand, caster),
                Utils.resolveTarget(caster, range));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        if (caster.getTarget().equals(caster)) return;

        launchDrag(caster.getTarget(), caster, wand,
                getBasePower() * getWandPowerOrDefault(wand),
                caster.getTarget().getLocation());
    }

    /**
     * The whole spell, shared by both cast paths so a mob's undertow is the same mechanic
     * as a player's rather than a cheaper imitation of it.
     *
     * <p>ONE {@link AttackProperties} is built here and captured for the whole drag, so a
     * single counter kills the entire pull - and with it the damage, since a countered drag
     * deals nothing at all.
     *
     * @param target    the single victim being hauled in
     * @param caster    the casting entity
     * @param wand      the casting wand, or null on a mob cast
     * @param power     the RAW base power; {@code getPower} is applied once, at the end
     * @param origin    where the riptide is born - the block the caster is looking at
     */
    private void launchDrag(LivingEntity target, LivingEntity caster, ItemStack wand,
                            double power, Location origin) {
        AttackProperties props = new AttackProperties(
                caster, Utils.castLocation(caster), power, AttackType.MAGIC);

        origin.getWorld().playSound(origin, Sound.BLOCK_WATER_AMBIENT, 0.6f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;
            double dragged = 0.0;

            @Override
            public void run() {
                // A counter kills the drag outright, and with no damage: the caster spent
                // their mana, and the target walks away untouched.
                if (props.isCancelled() || props.isCountered()) {
                    spawnUndertowBreak(origin);
                    cancel();
                    return;
                }

                boolean finished = target.isDead() || !target.isValid()
                        || caster.isDead()
                        || (caster instanceof Player player && !player.isOnline());

                Vector flat = caster.getLocation().toVector()
                        .subtract(target.getLocation().toVector()).setY(0.0);
                if (flat.length() <= dragStopDistance) finished = true;

                if (!finished && ticks < dragDurationTicks) {
                    dragged += step(target, flat, dragged);
                }

                drawUndertow(origin, caster, dragged, target, ticks);

                if (ticks % SPLASH_EVERY == 0) {
                    float pitch = (float) (0.6 + 0.2 * Math.min(1.0, dragged / maxDragDistance));
                    target.getWorld().playSound(target.getLocation(),
                            Sound.ENTITY_GENERIC_SPLASH, 0.8f, pitch);
                }

                ticks++;

                if (finished || ticks >= dragDurationTicks) {
                    dealUndertowDamage(caster, wand, target, props, dragged);
                    cancel();
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * One tick of the pull. The step is the smallest of three things: the configured
     * per-tick rate, whatever headroom is left under {@code max_drag_distance}, and the
     * distance still to cover before reaching the caster. The target is never moved past
     * the caster's own stop distance.
     *
     * <p>A player inside a protected region simply is not dragged, and therefore deals no
     * damage - the region gate is checked before the teleport, not after.
     *
     * @return how far the target was ACTUALLY moved, which is what {@code dragged} and the
     *         damage formula are computed from. Returning 0 rather than the intended step
     *         is what keeps a refused drag from inflating the damage it never earned.
     */
    private double step(LivingEntity target, Vector flat, double dragged) {
        double headroom = maxDragDistance - dragged;
        double step = Math.min(Math.min(dragStepPerTick, headroom),
                flat.length() - dragStopDistance);
        if (step <= 0.0) return 0.0;

        Vector unit = Utils.safeNormalize(flat);
        if (unit == null) return 0.0;

        Location next = target.getLocation().clone().add(unit.multiply(step));
        if (target instanceof Player player && !Protection.teleport(player, next)) return 0.0;

        target.teleport(next);
        target.setFallDistance(0.0f);
        target.setVelocity(target.getVelocity().setY(dragLift));
        return step;
    }

    /**
     * The single damage instance of the whole cast. {@code getPower} is called exactly
     * once, here, in its three-argument form - the two-argument overload is never used in
     * this class, so nothing is scaled twice.
     */
    private void dealUndertowDamage(LivingEntity caster, ItemStack wand, LivingEntity target,
                                    AttackProperties props, double dragged) {
        if (props.hasHit(target)) return;
        props.hit(target);

        double scaled = dragged * dragDamagePerBlock;
        double raw = Math.min(scaled, dragDamageCap);
        if (raw < getBasePower()) raw = getBasePower();      // power is the FLOOR of the range
        SpellDamageUtil.damageWithSpell(target, getPower(caster, target, raw), caster, wand, this);
    }

    /**
     * One frame of the riptide: a crest travelling from the origin toward the caster with a
     * veil strung along the target's own path. Particles only - this deals nothing.
     *
     * @param origin   where the riptide was born
     * @param caster   the casting entity
     * @param dragged  how far the target has actually been hauled, in blocks
     * @param target   the victim
     * @param ticks    ticks elapsed, so the veil repaints on alternate ticks
     */
    private void drawUndertow(Location origin, LivingEntity caster, double dragged,
                              LivingEntity target, int ticks) {
        World world = origin.getWorld();
        double progress = Math.max(0.0, Math.min(1.0, dragged / maxDragDistance));
        // Location has no lerp on the spigot-api this plugin compiles against, so the crest
        // travels the long way round: a + (b - a) * t. Every step in that chain is a Location
        // this method owns - origin.clone() and the fresh getEyeLocation() - so neither the
        // spell's origin nor the caster is mutated, exactly as before.
        Location front = origin.clone()
                .add(caster.getEyeLocation().subtract(origin).multiply(progress));
        double crestRadius = 1.6 - 0.8 * progress;

        for (int i = 0; i < CREST_POINTS; i++) {
            double angle = 2.0 * Math.PI * i / CREST_POINTS + progress * 2.1;
            Location point = front.clone().add(
                    Math.cos(angle) * crestRadius,
                    0.4 + Math.sin(progress * 4.0 + i) * 0.6,
                    Math.sin(angle) * crestRadius);
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, UNDERTOW_DUST);
            if (i % CREST_SPRAY_EVERY == 0) {
                ParticleCaster.spawn(caster, world, Utils.SPLASH, point, 3, 0.4, 0.2, 0.4, 0);
            }
        }

        if (progress >= 1.0) {
            ParticleCaster.spawn(caster, world, Utils.DUST, front, 0, UNDERTOW_EDGE);
        }

        if (dragged > 0.0 && ticks % VEIL_EVERY == 0) {
            drawVeil(caster, target);
        }
    }

    /**
     * The veil: a short line of dust strung between where the target was a block ago and
     * where it is now, so the pull reads as something dragging it rather than something
     * teleporting it.
     */
    private void drawVeil(LivingEntity caster, LivingEntity target) {
        Location head = target.getLocation();
        Location tail = head.clone().subtract(head.getDirection().normalize());
        for (int i = 0; i < VEIL_POINTS; i++) {
            // Same a + (b - a) * t as the crest. head is cloned into the chain because the
            // bubble column below reuses it, and tail is cloned because the chain mutates
            // its receiver. point is then lifted in place, which is safe only because the
            // interpolation yields a fresh Location rather than one of the two operands.
            Location point = tail.clone()
                    .add(head.clone().subtract(tail).multiply(i / (double) (VEIL_POINTS - 1)));
            point.add(0, 0.3, 0);
            ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point, 0, UNDERTOW_TRAIL);
        }
        ParticleCaster.spawn(caster, target.getWorld(), Utils.BUBBLE_COLUMN_UP,
                head.clone().add(0, 0.2, 0), 4, 0.4, 0.0, 0.4, 0);
    }

    /**
     * A drag that never landed: the crest collapses where it was born. No damage, ever -
     * this runs on the countered branch of the runnable.
     */
    private void spawnUndertowBreak(Location origin) {
        ParticleCaster.spawn(null, origin.getWorld(), Utils.SPLASH, origin, 50, 1, 1, 1, 0.5);
        ParticleCaster.spawn(null, origin.getWorld(), Utils.BUBBLE_COLUMN_UP, origin, 30, 1, 1, 1, 0);
        origin.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.SPLASH, location, 30, 0.5, 0.5, 0.5, 0.2);
        location.getWorld().playSound(location, Sound.BLOCK_WATER_AMBIENT, 0.8f, 0.7f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.SPLASH, location, 50, 1, 1, 1, 0.5);
        ParticleCaster.spawn(null, location.getWorld(), Utils.BUBBLE_COLUMN_UP, location, 30, 1, 1, 1, 0);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.undertow.book_name"))
                .addCustomLoreLine(lang().get("spells.undertow.lore1"))
                .addCustomLoreLine(lang().get("spells.undertow.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}