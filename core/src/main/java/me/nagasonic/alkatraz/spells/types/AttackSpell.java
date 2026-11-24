package me.nagasonic.alkatraz.spells.types;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.util.LightAmplification;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public abstract class AttackSpell extends Spell {

    /**
     * The published circle-9 affinity ceiling (a x3.15 multiplier). Mastery stat
     * points share the profile pool with circle points, so a raw profile value can
     * exceed it; the damage path clamps here so the published budget still holds.
     */
    public static final double MAX_AFFINITY = SpellBudgets.affinityFor(9);

    protected double basePower;


    public AttackSpell(String type) {
        super(type);
    }

    @Override
    public void loadCommonConfig(YamlConfiguration spellConfig) {
        super.loadCommonConfig(spellConfig);
        this.basePower = spellConfig.getDouble("power");
    }

    /**
     * Raw spell power before modifiers
     */
    public double getBasePower() {
        return basePower;
    }

    /**
     * The single scaling path for a damage instance. Applies, in order:
     * <ol>
     *   <li>the {@code damage} spell-option modifiers</li>
     *   <li>the affinity/resistance spread against the target, each side clamped
     *       to {@link #MAX_AFFINITY} so the published x3.15 budget holds</li>
     *   <li>light's anti-dark amplification, when this spell is LIGHT</li>
     * </ol>
     * <p>
     * Call this EXACTLY ONCE per damage instance. Calling it at cast time and
     * again at impact squares both terms, which is not linear: a 2.24 affinity
     * term becomes 3.24^2 = 10.5x rather than 4.48x.
     * <p>
     * The mob branch applies the same option modifiers as the player branch -
     * it previously skipped them, so mob-cast and player-cast damage diverged.
     *
     * @param caster the casting entity, player or mob
     * @param target the entity being damaged; null is treated as zero resistance
     * @param power  the base power from the spell's {@code power} config
     * @return the scaled damage to hand to {@code SpellDamageUtil.damageWithSpell}
     */
    public double getPower(LivingEntity caster, LivingEntity target, double power) {
        double casterAffinity = clampAffinity(casterAffinityOf(caster));
        double targetResistance = clampAffinity(targetResistanceOf(target));
        double modifiedPower = modifiedDamage(caster, power);
        double scaled = modifiedPower * (1 + ((casterAffinity - targetResistance) / 100));
        return applyLightAmplification(caster, target, scaled);
    }

    /** Caps a profile affinity/resistance at the circle-9 published budget. */
    static double clampAffinity(double value) {
        return Math.min(MAX_AFFINITY, value);
    }

    /**
     * The player half of a damage instance. Applying {@code pvpFactor} to the base
     * BEFORE {@link #getPower} lets affinity scale it back up; capping the resolved
     * damage here keeps PvP inside the spell's published player budget instead.
     *
     * @param damage the full {@code getPower} result for this hit
     * @param base   the spell's raw power for this hit
     * @param player whether the target is a player
     */
    static double capPvpDamage(double damage, double base, double pvpFactor, boolean player) {
        return player ? Math.min(damage, base * pvpFactor) : damage;
    }

    /**
     * Resolves one hit: full {@link #getPower} for mobs, a hard post-affinity cap of
     * {@code base * pvpFactor} for players. Callers must pass the spell's raw power,
     * not an already-reduced value.
     */
    protected double damageFor(LivingEntity caster, LivingEntity target, double base, double pvpFactor) {
        return capPvpDamage(getPower(caster, target, base), base, pvpFactor, target instanceof Player);
    }

    /**
     * Same resolution as {@link #damageFor} but with an absolute per-hit player cap,
     * which is the form published in the spells' {@code pvp_damage_cap} YAML keys.
     */
    protected double damageForCap(LivingEntity caster, LivingEntity target, double base, double playerCap) {
        double damage = getPower(caster, target, base);
        return target instanceof Player ? Math.min(damage, Math.max(0.0, playerCap)) : damage;
    }

    /** Default per-hit player cap for offensive spells without a published YAML budget. */
    public static final double DEFAULT_PVP_CAP = 2.0;

    /**
     * The one ally-filtered, PvP-capped hit path for area and projectile spells.
     * Mobs still take the full affinity-scaled hit.
     */
    public void damageArea(LivingEntity caster, LivingEntity target, double base,
                           double playerCap, ItemStack wand) {
        if (caster == null || target == null || target.isDead()) return;
        if (SpellTargeting.isAlly(caster, target)) return;
        SpellDamageUtil.damageWithSpell(
                target, damageForCap(caster, target, base, playerCap), caster, wand, this);
    }

    /**
     * Overload for non-entity targets (barriers, constructs, ground zones).
     * Applies option modifiers and caster affinity only - there is no target to
     * take a resistance or an amplification from. Prefer the three-arg form for
     * anything that damages a living entity.
     *
     * @param caster the casting entity, player or mob
     * @param power  the base power from the spell's {@code power} config
     * @return the scaled damage for a target with no affinity of its own
     */
    public double getPower(LivingEntity caster, double power) {
        double casterAffinity = clampAffinity(casterAffinityOf(caster));
        return modifiedDamage(caster, power) * (1 + (casterAffinity / 100));
    }

    private double casterAffinityOf(LivingEntity caster) {
        if (caster instanceof Player player) {
            MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
            return profile == null ? 0.0 : profile.getAffinity(getElement());
        }
        return Utils.getEntityAffinity(getElement(), caster);
    }

    private double targetResistanceOf(LivingEntity target) {
        if (target == null) return 0.0;
        if (target instanceof Player player) {
            MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
            return profile == null ? 0.0 : profile.getResistance(getElement());
        }
        return Utils.getEntityResistance(getElement(), target);
    }

    private double modifiedDamage(LivingEntity caster, double power) {
        if (caster instanceof Player player) {
            return getModifiedStat(player, "damage", power);
        }
        return power;
    }

    private double applyLightAmplification(LivingEntity caster, LivingEntity target, double damage) {
        if (getElement() != Element.LIGHT) return damage;
        return damage * LightAmplification.forTarget(caster, target);
    }

    /**
     * Called when this spell hits a barrier
     */
    public abstract void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster);

    /**
     * Called when this spell is fully countered
     */
    public abstract void onCountered(Location location);

    @Override
    public boolean canMobCast(Mob mob){
        return mob.getTarget() != null;
    }

}
