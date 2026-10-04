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
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Circle 7 WATER sustain. A still pool opens where the caster is looking and drinks: every
 * second it takes a bite out of every living thing inside it and hands the health to the
 * caster, capped at their maximum. This is water's third pillar - displacement, sustained
 * pressure, and <b>life transfer</b> - and the only spell in the roster where the caster's
 * own health bar is the scoreboard.
 *
 * <h2>Four windows, not a longer life</h2>
 * {@code power} stays on the section 6 ladder at 18.9 and the <b>budget fixes the
 * duration</b>: {@code sustainBudget(7) / power = 76 / 18.9 = 4.02}, so the pool opens
 * exactly four damage windows over exactly four seconds. That is {@code 18.9 x 4 = 75.6}
 * against a budget of 76. A fifth window would be 94.5 and would fail
 * {@code SpellBudgetTest}, which is why {@code hit_count} is 4 and not 5. Four drain
 * windows is still a SUSTAIN and not a burst - {@code whirlwind}'s circle-4 sibling
 * {@code whirlpool} has eight.
 *
 * <h2>The dedupe is the WINDOW, not {@code hasHit}</h2>
 * {@link AttackProperties#hasHit} is per-<em>cast</em>, which is the wrong scope for a field
 * that is <i>supposed</i> to re-hit the same body once a second. {@code hitThisWindow} is a
 * {@code Set<UUID>} cleared at the top of every window instead, and {@code props.hit(le)} is
 * still called on the first window so the cast registers against the shared properties and
 * one counter ends the pool.
 *
 * <p>The damage branch is gated on {@link SpellAttackContext#DAMAGE_INTERVAL_TICKS}, named
 * rather than written as a literal {@code 20}, so this spell's cadence cannot drift away
 * from the rest of the roster's.
 *
 * <h2>The heal is not a second {@code getPower}</h2>
 * The heal is derived from {@code drainedThisWindow} - the damage values already produced in
 * the same window - and is clamped three ways: by the caster's missing health, by the
 * 20-tick cadence, and by {@code heal_ratio}. Scaling the heal a second time would be
 * rule 1's exact failure mode.
 *
 * <p>No {@code TerrainEditor}, no block write of any kind, no {@code SpellBlockComponent}:
 * a life-drain is not a wall, so a barrier should not absorb it. The only state changes are
 * {@code Entity#setVelocity} on drained bodies and {@code LivingEntity#setHealth} on the
 * caster.
 */
public class Everdrain extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.WATER);

    /** The pool's outer ring. */
    private static final Particle.DustOptions DRAIN_DUST = PALETTE.coreDust();
    /** The counter-rotating inner ring, a shade back so the two read as two depths. */
    private static final Particle.DustOptions DRAIN_CORE = PALETTE.accentDust();
    /** The pool's floor shading. */
    private static final Particle.DustOptions DRAIN_FLOOR = PALETTE.deepDust();

    /** Points on the outer ring. */
    private static final int RING_POINTS = 24;
    /** Points on the inner ring. */
    private static final int INNER_POINTS = 16;
    /** Rising bubbles every fourth tick. */
    private static final int BUBBLE_EVERY = 4;
    /** Popping bubbles every second tick. */
    private static final int POP_EVERY = 2;

    private double durationSeconds;
    private double drainRadius;
    private double targetRange;
    private double pullStrength;
    private double lift;
    private double healRatio;

    public Everdrain(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /**
     * Circle 7 shape, read inline. No {@code saveConfig} call and no config record: this
     * spell's knobs are already its contract, and every one of them feeds directly into the
     * budget arithmetic {@code SpellBudgetTest} asserts.
     */
    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/everdrain.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/everdrain.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.durationSeconds = spellConfig.getDouble("duration_seconds", 4.0);
        this.drainRadius = spellConfig.getDouble("drain_radius", 6.0);
        this.targetRange = spellConfig.getDouble("target_range", 24.0);
        this.pullStrength = spellConfig.getDouble("pull_strength", 0.35);
        this.lift = spellConfig.getDouble("lift", 0.25);
        this.healRatio = spellConfig.getDouble("heal_ratio", 1.0);
    }

    /** The pool's whole life, in ticks. {@code hit_count} must equal {@code durationSeconds}. */
    private int lifetimeTicks() {
        return Math.max(1, (int) Math.round(durationSeconds * 20.0));
    }

    /**
     * Damage windows the pool opens at the shared cadence. This is what {@code hit_count}
     * declares, and it is enforced rather than merely documented: the drain branch is
     * bounded by it, so raising {@code duration_seconds} in an admin's yml without raising
     * {@code hit_count} cannot quietly push the spell past the budget
     * {@code SpellBudgetTest} asserts.
     */
    private int damageWindows() {
        return Math.max(0, lifetimeTicks() / (int) SpellAttackContext.DAMAGE_INTERVAL_TICKS);
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        double radius = getModifiedStat(caster, "drain_radius", drainRadius);
        Location centre = Utils.resolveTarget(caster,
                (int) getModifiedStat(caster, "target_range", targetRange));

        openPool(centre, caster, wand, radius, getWandPower(wand, caster));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead() || caster.getTarget() == null) return;
        // A mob's pool is narrower and its leash is its target: the same mechanic, scaled.
        openPool(caster.getTarget().getLocation(), caster, wand,
                Math.min(drainRadius, 4.5), getWandPowerOrDefault(wand));
    }

    /**
     * The pool, shared by both cast paths. ONE {@link AttackProperties} lives for the whole
     * four-second life, so a single counter ends the field outright.
     *
     * @param centre    where the pool opens
     * @param caster    the casting entity
     * @param wand      the casting wand, or null on a mob cast
     * @param radius    the pool's radius in blocks
     * @param wandPower the wand scaling factor, applied once when the properties are seeded
     */
    private void openPool(Location centre, LivingEntity caster, ItemStack wand,
                          double radius, double wandPower) {
        AttackProperties props = new AttackProperties(
                caster, Utils.castLocation(caster), getBasePower() * wandPower, AttackType.MAGIC);

        centre.getWorld().playSound(centre, Sound.BLOCK_WATER_AMBIENT, 1.0f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            final Set<UUID> hitThisWindow = new HashSet<>();

            @Override
            public void run() {
                if (props.isCancelled() || props.isCountered() || ticks >= lifetimeTicks()) {
                    centre.getWorld().playSound(centre, Sound.ENTITY_DROWNED_SWIM, 0.8f, 0.4f);
                    cancel();
                    return;
                }

                drawDrain(centre, caster, radius, ticks);

                if (ticks % SpellAttackContext.DAMAGE_INTERVAL_TICKS == 0
                        && ticks / SpellAttackContext.DAMAGE_INTERVAL_TICKS < damageWindows()) {
                    hitThisWindow.clear();
                    drainOnce(centre, caster, wand, radius, props, hitThisWindow);
                }
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * One twenty-tick window: drain everything in the pool, then hand the total to the
     * caster. This method and {@link #drawDrain} are the only two places damage or healing
     * can originate, which is what makes {@code power x hit_count} the spell's real total.
     *
     * @param hitThisWindow cleared by the caller at the top of every window
     */
    private void drainOnce(Location centre, LivingEntity caster, ItemStack wand, double radius,
                           AttackProperties props, Set<UUID> hitThisWindow) {
        double drainedThisWindow = 0.0;

        for (LivingEntity le : Utils.getNearbyLivingEntities(centre, radius, radius, radius)) {
            if (le.equals(caster) || le.isDead() || !le.isValid()) continue;
            if (!hitThisWindow.add(le.getUniqueId())) continue;

            // The region gate is checked ONCE, before both halves of the transfer. Without
            // this a protected target would still heal the caster for damage that never landed.
            if (!Protection.damage(le, caster, le.getLocation())) continue;

            props.hit(le);
            double damage = getPower(caster, le, props.getInitialPower());   // ONE getPower
            SpellDamageUtil.damageWithSpell(le, damage, caster, wand, this);
            drainedThisWindow += damage;

            // A velocity nudge toward the pool, never a teleport: nothing moves, so this is
            // not a block-adjacent effect and needs no block gate.
            Vector toward = Utils.safeNormalize(
                    centre.toVector().subtract(le.getLocation().toVector()).setY(0.0));
            if (toward != null) {
                le.setVelocity(le.getVelocity()
                        .add(toward.multiply(pullStrength).setY(lift)));
            }
            ParticleCaster.spawn(caster, le.getWorld(), Utils.CRIT,
                    le.getLocation().add(0, 1.0, 0), 8, 0.3, 0.4, 0.3, 0.1);
        }

        // Life transfer, resolved once per window after every target has been drained.
        if (drainedThisWindow > 0.0) healCaster(caster, drainedThisWindow);
    }

    /**
     * The other half of the spell. Clamped at the caster's maximum health, so this is a
     * lifeline and not a health engine.
     */
    private void healCaster(LivingEntity caster, double drainedThisWindow) {
        double maxHealth = maxHealth(caster);
        if (caster.getHealth() >= maxHealth) return;
        double missing = maxHealth - caster.getHealth();
        double heal = Math.min(drainedThisWindow * healRatio, missing);
        if (heal <= 0.0) return;
        caster.setHealth(Math.min(maxHealth, caster.getHealth() + heal));
    }

    /**
     * {@code Heal.java:73-75}'s clamp idiom. Mobs carry {@code GENERIC_MAX_HEALTH} too, so
     * this is a single code path with no player branch anywhere in the heal.
     */
    private static double maxHealth(LivingEntity entity) {
        return Objects.requireNonNull(
                entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();
    }

    /**
     * One frame of the pool: an outer ring, a counter-rotating inner ring, and bubbles.
     * Particles and one sound only - this deals nothing.
     */
    private void drawDrain(Location centre, LivingEntity caster, double radius, int ticks) {
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = 2.0 * Math.PI * i / RING_POINTS + ticks * 0.08;
            Location point = ringPoint(centre, radius, angle, 0.25);
            ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point, 0, DRAIN_DUST);
        }
        for (int i = 0; i < INNER_POINTS; i++) {
            double angle = 2.0 * Math.PI * i / INNER_POINTS - ticks * 0.12;
            Location point = ringPoint(centre, radius * 0.6, angle, 0.45);
            ParticleCaster.spawn(caster, point.getWorld(), Utils.DUST, point, 0, DRAIN_CORE);
        }
        if (ticks % BUBBLE_EVERY == 0) {
            ParticleCaster.spawn(caster, centre.getWorld(), Utils.BUBBLE_COLUMN_UP,
                    centre, 6, radius, 0, radius, 0);
            ParticleCaster.spawn(caster, centre.getWorld(), Utils.DUST, centre, 8,
                    radius * 0.5, 0.02, radius * 0.5, DRAIN_FLOOR);
        }
        if (ticks % POP_EVERY == 0) {
            ParticleCaster.spawn(caster, centre.getWorld(), Utils.BUBBLE_POP,
                    centre, 12, radius * 0.7, 0.1, radius * 0.7, 0);
        }
        if (ticks % SpellAttackContext.DAMAGE_INTERVAL_TICKS == 0) {
            ParticleCaster.spawn(caster, centre.getWorld(), Utils.SPLASH,
                    centre.clone().add(0, 0.2, 0), 20, radius, 0.2, radius, 0);
            centre.getWorld().playSound(centre, Sound.ENTITY_DROWNED_SWIM, 0.9f, 0.4f);
        }
    }

    private static Location ringPoint(Location centre, double radius, double angle, double height) {
        return centre.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 4.0, 0);
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
                .setDisplayName(lang().get("spells.everdrain.book_name"))
                .addCustomLoreLine(lang().get("spells.everdrain.lore1"))
                .addCustomLoreLine(lang().get("spells.everdrain.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }
}