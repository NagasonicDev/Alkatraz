package me.nagasonic.alkatraz.spells.types;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.util.LightAmplification;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

public abstract class AttackSpell extends Spell {
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
     *   <li>the affinity/resistance spread against the target</li>
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
        double casterAffinity = casterAffinityOf(caster);
        double targetResistance = targetResistanceOf(target);
        double modifiedPower = modifiedDamage(caster, power);
        double scaled = modifiedPower * (1 + ((casterAffinity - targetResistance) / 100));
        return applyLightAmplification(caster, target, scaled);
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
        double casterAffinity = casterAffinityOf(caster);
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
