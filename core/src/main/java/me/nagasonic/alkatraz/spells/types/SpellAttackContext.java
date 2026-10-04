package me.nagasonic.alkatraz.spells.types;

import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.types.properties.implementation.AttackProperties;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The per-cast handle that enforces three of the five damage-contract rules
 * (spec section 4) mechanically rather than by convention:
 *
 * <ol>
 *   <li><b>One getPower per damage instance.</b> Calling {@link #power} twice for
 *       the same target in one tick, or mixing the untargeted overload with a
 *       targeted one, throws {@link IllegalStateException}. Eight spells
 *       currently scale at cast time AND at impact, which squares both the option
 *       multipliers and the affinity term - and squaring is not linear, so 2.24
 *       affinity becomes 3.24^2 = 10.5x rather than 4.48x.</li>
 *   <li><b>Deduped per target per cast.</b> {@link #hit} returns false the second
 *       time, delegating to {@link AttackProperties#hit}. A SUSTAIN field still
 *       re-damages every {@link #DAMAGE_INTERVAL_TICKS}; it just cannot double-hit
 *       inside one tick.</li>
 *   <li><b>One shared AttackProperties per cast,</b> even across N projectiles, so
 *       a single counter kills the whole volley.</li>
 * </ol>
 *
 * The tick counter is internal rather than read from Bukkit because
 * {@code Bukkit.getCurrentTick()} does not exist across the 1.19.1 to 1.26 range
 * this plugin shims. The owning runnable calls {@link #tick()} once per
 * scheduler run.
 */
public final class SpellAttackContext {

    /** A SUSTAIN field damages a target at most once every second. */
    public static final long DAMAGE_INTERVAL_TICKS = 20L;

    private final AttackSpell spell;
    private final AttackProperties props;
    private final LivingEntity caster;
    private final ItemStack wand;

    private long tick;
    private boolean untargetedScaledThisTick;
    private final Set<UUID> scaledThisTick = new HashSet<>();

    private SpellAttackContext(AttackSpell spell, AttackProperties props, LivingEntity caster, ItemStack wand) {
        this.spell = spell;
        this.props = props;
        this.caster = caster;
        this.wand = wand;
    }

    /**
     * @param power the RAW base power - {@code getBasePower() * getWandPower(wand, caster)},
     *              never a pre-scaled value. Pre-scaling here is the bug this class exists to catch.
     */
    public static SpellAttackContext of(AttackSpell spell, LivingEntity caster, ItemStack wand, AttackType type) {
        // getWandPower's two-arg overload takes a Player (Spell.java:570 after T1),
        // not a LivingEntity. Passing caster straight through does not compile.
        // A mob cast has no profile, so it takes the no-player overload - which is
        // exactly what it did before, via getWandPowerOrDefault.
        double base = spell.getBasePower()
                * Spell.getWandPower(wand, caster instanceof Player p ? p : null);
        AttackProperties props = new AttackProperties(caster, Utils.castLocation(caster), base, type);
        return new SpellAttackContext(spell, props, caster, wand);
    }

    /** Build around properties the spell already owns, for casts that set up state first. */
    public static SpellAttackContext of(AttackSpell spell, AttackProperties props, LivingEntity caster, ItemStack wand) {
        return new SpellAttackContext(spell, props, caster, wand);
    }

    /** Advance the internal tick. Call once per scheduler run, before any damage. */
    public void tick() {
        tick++;
        untargetedScaledThisTick = false;
        scaledThisTick.clear();
    }

    public long currentTick() {
        return tick;
    }

    public AttackProperties properties() {
        return props;
    }

    /** True once the cast has been cancelled or countered - every runnable must poll this. */
    public boolean isSpent() {
        return props.isCancelled() || props.isCountered();
    }

    public LivingEntity caster() {
        return caster;
    }

    public ItemStack wand() {
        return wand;
    }

    /**
     * Records a hit against {@code target} for this cast.
     * <p>
     * Note the shape: {@code AttackProperties.hit(Entity)} returns **void** - it is
     * {@code AttackProperties.hasHit(Entity)} that returns boolean
     * ({@code AttackProperties.java:58} and {@code :62}). So the guard-then-record
     * form below is the only correct one; {@code hit} cannot be used as a boolean
     * test and a call written that way does not compile.
     *
     * @return true the first time, false on every repeat. Callers should
     *         {@code continue} the scan on false rather than damaging again.
     */
    public boolean hit(LivingEntity target) {
        if (target == null) return false;
        if (props.hasHit(target)) return false;
        props.hit(target);
        return true;
    }

    /**
     * Applies {@code getPower} exactly once for {@code target} this tick.
     *
     * @throws IllegalStateException if this target was already scaled this tick,
     *         or if the untargeted overload was used this tick.
     */
    public double power(LivingEntity target) {
        if (untargetedScaledThisTick) {
            throw new IllegalStateException(
                    "Spell " + spell.getId() + ": getPower was already applied untargeted this tick. "
                            + "The 2-arg cast-time call and the 3-arg impact call must not both run - "
                            + "that squares the option multipliers and the affinity term.");
        }
        UUID key = target == null ? null : target.getUniqueId();
        if (key != null && !scaledThisTick.add(key)) {
            throw new IllegalStateException(
                    "Spell " + spell.getId() + ": getPower applied twice to the same target in one tick.");
        }
        return spell.getPower(caster, target, props.getRemainingPower());
    }

    /**
     * Applies the untargeted overload. Only for payloads with no living target -
     * barriers, ground zones. Mutually exclusive with {@link #power(LivingEntity)}
     * within a tick.
     */
    public double power() {
        if (untargetedScaledThisTick || !scaledThisTick.isEmpty()) {
            throw new IllegalStateException(
                    "Spell " + spell.getId() + ": the untargeted getPower overload must not share a tick "
                            + "with a targeted one.");
        }
        untargetedScaledThisTick = true;
        return spell.getPower(caster, props.getRemainingPower());
    }
}