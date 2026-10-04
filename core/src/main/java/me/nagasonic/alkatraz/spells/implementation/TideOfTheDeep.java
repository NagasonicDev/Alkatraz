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
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
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
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Circle 9 WATER capstone. The area the caster chooses becomes a tide: it heals <b>them</b>,
 * and only them, while it crushes everything else inside it - and both halves climb across
 * the ten seconds it lasts.
 *
 * <h2>Why {@code power} is 14.4 and not the ladder's 28.6</h2>
 * Three things are mandated at once and cannot all hold: {@code power} comes off the
 * section 6 ladder, which puts circle 9 at 28.6; {@code hit_count} is 10 because the ramp
 * runs for ten seconds at one window per second; and {@code power x hit_count} must sit
 * inside {@code sustainBudget(9)} = 144.
 *
 * <pre>
 * 28.6 x 10 = 286.0  &gt;  144    the ladder value does not fit
 * 144 / 10  = 14.4               the only per-hit value that does
 * 14.4 x 10 = 144.0  ==  144     exactly on the budget
 * </pre>
 *
 * This is the same shape as {@code inferno_pilgrimage}'s documented 3 x 16 = 48: a
 * deliberate, budget-checked departure with the arithmetic shown, landing <i>on</i> the
 * budget. {@code hit_count} is the free variable here; {@code power} is the value every
 * damage instance scales from, so dividing it is the honest way to spend the budget.
 *
 * <h2>The ramp climbs, and the real total is under the declared one</h2>
 * {@code tide_ramp[i]} is the fraction of {@code power} dealt on window {@code i}. The ten
 * values sum to 9.40, so a body standing through the whole tide takes
 * {@code 14.4 x 9.40 = 135.36} - comfortably inside 144, and up from a first window of 5.76
 * to a last of 21.31, a 3.7x climb the player can read on their own health bars.
 *
 * <h2>The dedupe is the WINDOW, not {@code hasHit}</h2>
 * {@link AttackProperties#hasHit} is per-<em>cast</em>, which is the wrong scope for a field
 * that is <i>supposed</i> to re-hit the same body once a second. {@code hitThisWindow} is a
 * {@code Set<UUID>} cleared at the top of every window instead; {@code props.hit(le)} is still
 * called so one counter ends the whole tide. The damage branch is gated on
 * {@link SpellAttackContext#DAMAGE_INTERVAL_TICKS}, named rather than written as a literal
 * {@code 20}.
 *
 * <h2>The heal is capped three ways, and is not a second {@code getPower}</h2>
 * By the caster's missing health, by the 20-tick cadence, and by
 * {@code power x ramp[i] x heal_ratio} <i>per window regardless of how many bodies were
 * drained</i> - so walking five mobs into the tide cannot turn the heal into a health
 * engine. It is derived from the damage values already produced in the same window; scaling
 * it again would be rule 1's exact failure mode.
 *
 * <p>No {@code TerrainEditor}, no block write of any kind, no {@code SpellBlockComponent}:
 * a tide is a sphere of pressure, not a wall, so a barrier should not absorb it. The only
 * state changes are {@code Entity#setVelocity} on drowned bodies and
 * {@code LivingEntity#setHealth} on the caster.
 */
public class TideOfTheDeep extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);

    /** The outer ring: the tide's leading edge. */
    private static final Particle.DustOptions SURGE_DUST = PALETTE.coreDust();
    /** The middle ring, the tide's body. */
    private static final Particle.DustOptions TIDE_DUST = PALETTE.accentDust();
    /** The inner ring and the rising axis: the caster's own half of the effect. */
    private static final Particle.DustOptions MENDER_DUST = PALETTE.deepDust();

    /** Points on the outer ring. */
    private static final int OUTER_POINTS = 24;
    /** Points on the middle ring. */
    private static final int MIDDLE_POINTS = 16;
    /** Points on the inner ring. */
    private static final int INNER_POINTS = 10;
    /** Points on the vertical axis climbing out of the tide. */
    private static final int AXIS_POINTS = 12;
    /** How high that axis climbs, in blocks. */
    private static final double AXIS_HEIGHT = 4.0;
    /** Splash cadence. */
    private static final int SPLASH_EVERY = 4;
    /** Bubble column cadence. */
    private static final int BUBBLE_EVERY = 8;
    /** The per-window punctuation: a burst of pops and a rising note. */
    private static final int WINDOW_EVERY = (int) SpellAttackContext.DAMAGE_INTERVAL_TICKS;
    /** The tide's smallest radius, as a fraction of the full radius, at the start of the ramp. */
    private static final double RAMP_START = 0.35;

    /**
     * The ramp this tide climbs when the yml's is missing, empty, or contains a non-number.
     * The ten values sum to 9.40, which is what holds the real total at 135.36 against a
     * budget of 144.
     */
    private static final double[] DEFAULT_RAMP =
            {0.40, 0.52, 0.64, 0.76, 0.88, 1.00, 1.12, 1.24, 1.36, 1.48};

    private double durationSeconds;
    private double tideRadius;
    private double targetRange;
    private double healRatio;
    private double pullStrength;
    private double lift;
    private double[] ramp;

    public TideOfTheDeep(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /**
     * Circle 9 shape, read inline. No {@code saveConfig} call and no config record: the tide's
     * radius, duration and ramp <i>are</i> its budget, so there is no key that could be sold
     * as an option without {@code SpellBudgetTest} asserting a number the player can
     * invalidate from a menu.
     */
    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/tide_of_the_deep.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/tide_of_the_deep.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.durationSeconds = spellConfig.getDouble("duration_seconds", 10.0);
        this.tideRadius = spellConfig.getDouble("tide_radius", 8.0);
        this.targetRange = spellConfig.getDouble("target_range", 20.0);
        this.healRatio = spellConfig.getDouble("heal_ratio", 0.5);
        this.pullStrength = spellConfig.getDouble("pull_strength", 0.2);
        this.lift = spellConfig.getDouble("lift", 0.15);
        this.ramp = doublesOf(spellConfig.get("tide_ramp"), DEFAULT_RAMP);
    }

    /** Falls back to {@code def} whenever the list is absent, empty, or holds a non-number. */
    private static double[] doublesOf(Object raw, double[] def) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return def;
        double[] out = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Number n)) return def;
            out[i] = n.doubleValue();
        }
        return out;
    }

    /** The tide's whole life, in ticks. {@code hit_count} must equal {@code durationSeconds}. */
    private int lifetimeTicks() {
        return Math.max(1, (int) Math.round(durationSeconds * 20.0));
    }

    /**
     * Damage windows the tide opens at the shared cadence - the yml's {@code hit_count}.
     * Enforced rather than merely documented, so raising {@code duration_seconds} in an
     * admin's copy without raising {@code hit_count} cannot push the spell past 144.0.
     */
    private int damageWindows() {
        return Math.max(0, lifetimeTicks() / (int) SpellAttackContext.DAMAGE_INTERVAL_TICKS);
    }

    /** Index-safe ramp read - a short list clamps to its last entry rather than throwing. */
    private double rampAt(int window) {
        if (ramp.length == 0) return 1.0;
        return ramp[Math.min(Math.max(window, 0), ramp.length - 1)];
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        double radius = getModifiedStat(caster, "tide_radius", tideRadius);
        Location centre = Utils.resolveTarget(caster,
                (int) getModifiedStat(caster, "target_range", targetRange));

        raiseTide(centre, caster, wand, radius, getWandPower(wand, caster));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        // A mob's tide is narrower and centred on its target: the same mechanic, scaled.
        raiseTide(caster.getTarget().getLocation(), caster, wand,
                Math.min(tideRadius, 5.0), getWandPowerOrDefault(wand));
    }

    /**
     * The tide, shared by both cast paths. ONE {@link AttackProperties} lives for the whole
     * two-hundred-tick life, so a single counter ends it outright.
     *
     * @param centre    where the tide rises
     * @param caster    the casting entity, and the only thing it heals
     * @param wand      the casting wand, or null on a mob cast
     * @param radius    the tide's radius in blocks
     * @param wandPower the wand scaling factor, applied once when the properties are seeded
     */
    private void raiseTide(Location centre, LivingEntity caster, ItemStack wand,
                           double radius, double wandPower) {
        AttackProperties props = new AttackProperties(
                caster, Utils.castLocation(caster), getBasePower() * wandPower, AttackType.MAGIC);

        centre.getWorld().playSound(centre, Sound.BLOCK_WATER_AMBIENT, 1.4f, 0.4f);
        centre.getWorld().playSound(centre, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.3f);

        new BukkitRunnable() {
            int ticks = 0;
            int window = -1;
            final Set<UUID> hitThisWindow = new HashSet<>();

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || ticks >= lifetimeTicks()) {
                    centre.getWorld().playSound(centre, Sound.ENTITY_DROWNED_SWIM, 1.0f, 0.5f);
                    cancel();
                    return;
                }

                drawTide(centre, caster, radius, ticks, window);

                if (ticks % SpellAttackContext.DAMAGE_INTERVAL_TICKS == 0
                        && ticks / SpellAttackContext.DAMAGE_INTERVAL_TICKS < damageWindows()) {
                    hitThisWindow.clear();
                    tideOnce(centre, caster, wand, radius, props, hitThisWindow, window);
                    window++;
                }
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * One twenty-tick window: drown everything in the tide, then mend the caster if they are
     * standing in it. This method and {@link #drawTide} are the only two places damage or
     * healing can originate.
     *
     * @param hitThisWindow cleared by the caller at the top of every window
     * @param window        the zero-based index of this window, which selects the ramp entry
     */
    private void tideOnce(Location centre, LivingEntity caster, ItemStack wand, double radius,
                          AttackProperties props, Set<UUID> hitThisWindow, int window) {
        double multiplier = rampAt(window);
        double drainedThisWindow = 0.0;

        for (LivingEntity le : Utils.getNearbyLivingEntities(centre, radius, radius, radius)) {
            if (le.equals(caster) || le.isDead() || !le.isValid()) continue;
            if (!hitThisWindow.add(le.getUniqueId())) continue;

            // The region gate is checked ONCE, before anything at all. Without this a
            // protected body would still be counted into the caster's heal.
            if (!Protection.damage(le, caster, le.getLocation())) continue;

            props.hit(le);
            double damage = getPower(caster, le, props.getInitialPower() * multiplier);   // ONE getPower
            SpellDamageUtil.damageWithSpell(le, damage, caster, wand, this);
            drainedThisWindow += damage;

            Vector toCentre = Utils.safeNormalize(
                    centre.toVector().subtract(le.getLocation().toVector()).setY(0.0));
            if (toCentre != null) {
                le.setVelocity(le.getVelocity().add(toCentre.multiply(pullStrength).setY(lift)));
            }
            ParticleCaster.spawn(caster, le.getWorld(), Utils.SPLASH,
                    le.getLocation().add(0, 0.3, 0), 10, 0.3, 0.4, 0.3, 0);
        }

        // The tide heals the caster and nobody else, and only while they stand in it.
        if (caster.getLocation().distanceSquared(centre) <= radius * radius) {
            mendCaster(caster, drainedThisWindow, multiplier);
            ParticleCaster.spawn(caster, caster.getWorld(), Utils.HEART,
                    caster.getEyeLocation().add(0, 0.5, 0), 4, 0.3, 0.3, 0.3, 0);
        }
    }

    /**
     * The caster's half of the capstone. Capped at ONE window's worth of ramped power however
     * many bodies were drained, and at the caster's maximum health - so the heal is a reward
     * for standing in your own tide, not a health engine.
     *
     * <p>Derived from the damage already produced in this window, never re-scaled: calling
     * {@code getPower} a second time here would square both the option multipliers and the
     * affinity term.
     */
    private void mendCaster(LivingEntity caster, double drainedThisWindow, double multiplier) {
        double cap = getBasePower() * multiplier * healRatio;
        double heal = Math.min(drainedThisWindow * healRatio, cap);
        if (heal <= 0.0) return;

        double maxHealth = Objects.requireNonNull(
                caster.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();
        if (caster.getHealth() >= maxHealth) return;
        caster.setHealth(Math.min(maxHealth, caster.getHealth() + heal));
    }

    /**
     * One frame of the tide: three concentric rings widening across the life, an axis
     * climbing out of the centre, and a per-window punctuation of spray, bubbles and a note
     * that rises with the ramp. Particles and one sound only - this deals nothing.
     *
     * @param window the index of the current damage window, or -1 before the first one
     */
    private void drawTide(Location centre, LivingEntity caster, double radius, int ticks, int window) {
        World world = centre.getWorld();
        double progress = Math.min(1.0, (double) ticks / lifetimeTicks());
        double ringRadius = radius * (RAMP_START + (1.0 - RAMP_START) * progress);

        drawRing(world, caster, centre, ringRadius, OUTER_POINTS, ticks * 0.05, SURGE_DUST);
        drawRing(world, caster, centre, ringRadius * 0.72, MIDDLE_POINTS, -ticks * 0.08, TIDE_DUST);
        drawRing(world, caster, centre, ringRadius * 0.44, INNER_POINTS, ticks * 0.13, MENDER_DUST);

        for (int i = 0; i < AXIS_POINTS; i++) {
            double angle = ticks * 0.2 + 2.0 * Math.PI * i / AXIS_POINTS;
            Location point = centre.clone().add(
                    Math.cos(angle) * 0.4, AXIS_HEIGHT * i / (AXIS_POINTS - 1.0), Math.sin(angle) * 0.4);
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, TIDE_DUST);
        }

        if (ticks % SPLASH_EVERY == 0) {
            ParticleCaster.spawn(caster, world, Utils.SPLASH,
                    centre.clone().add(0, 0.2, 0), 20, ringRadius, 0.2, ringRadius, 0);
        }
        if (ticks % BUBBLE_EVERY == 0) {
            ParticleCaster.spawn(caster, world, Utils.BUBBLE_COLUMN_UP, centre, 12,
                    ringRadius, 0.4, ringRadius, 0);
        }
        if (ticks % WINDOW_EVERY == 0) {
            ParticleCaster.spawn(caster, world, Utils.BUBBLE_POP,
                    centre.clone().add(0, 1.5, 0), 24, ringRadius * 0.8, 0.3, ringRadius * 0.8, 0);
            centre.getWorld().playSound(centre, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4f,
                    (float) (0.3 + 0.03 * Math.max(0, window)));
        }
    }

    private static void drawRing(World world, LivingEntity caster, Location centre, double ringRadius,
                                 int points, double spin, Particle.DustOptions dust) {
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points + spin;
            Location point = centre.clone().add(
                    Math.cos(angle) * ringRadius,
                    0.25 + Math.sin(spin + i) * 0.35,
                    Math.sin(angle) * ringRadius);
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 0, dust);
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        // Size 4, matching the other circle 7-9 casts in the roster.
        return CastCircle.start(this, caster, e, 4.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.SPLASH, location, 40, 0.5, 0.5, 0.5, 0.2);
        ParticleCaster.spawn(caster, location.getWorld(), Utils.BUBBLE_POP, location, 20, 0.5, 0.5, 0.5, 0);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.SPLASH, location, 60, 1, 1, 1, 0.5);
        ParticleCaster.spawn(null, location.getWorld(), Utils.BUBBLE_COLUMN_UP, location, 30, 1, 1, 1, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.tideofthedeep.book_name"))
                .addCustomLoreLine(lang().get("spells.tideofthedeep.lore1"))
                .addCustomLoreLine(lang().get("spells.tideofthedeep.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}