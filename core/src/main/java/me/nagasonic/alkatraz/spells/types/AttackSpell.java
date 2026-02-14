package me.nagasonic.alkatraz.spells.types;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public abstract class AttackSpell extends Spell {
    protected double basePower;


    public AttackSpell(String type) {
        super(type);
        setupOptions();
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
     * Gets power with player-specific modifiers applied
     */
    public double getPower(Player caster, LivingEntity target, double power) {
        // Apply spell option modifiers
        double modifiedPower = applyPlayerModifiers(caster, power);

        // Apply affinity/resistance
        MagicProfile casterProfile = ProfileManager.getProfile(caster, MagicProfile.class);
        double casterAffinity = casterProfile.getAffinity(getElement());

        double targetResistance;
        if (target instanceof Player t) {
            MagicProfile targetProfile = ProfileManager.getProfile(t, MagicProfile.class);
            targetResistance = targetProfile.getResistance(getElement());
        } else {
            targetResistance = Utils.getEntityResistance(getElement(), target);
        }

        return modifiedPower * (1 + ((casterAffinity - targetResistance) / 100));
    }

    /**
     * Overload for non-entity targets (barriers, constructs, etc)
     */
    public double getPower(Player caster, double power) {
        double modifiedPower = applyPlayerModifiers(caster, power);
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        return modifiedPower * (1 + (profile.getAffinity(getElement()) / 100));
    }

    /**
     * Applies player-specific spell modifiers from their spell options
     */
    private double applyPlayerModifiers(Player caster, double baseStat) {
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);

        if (profile.hasSpellModifier(this, "damage")) {
            double value = profile.getSpellModifiers(this, "damage").value();
            String typeStr = profile.getSpellModifierType(profile.getSpellModifiers(this, "damage"));

            if (typeStr != null) {
                StatModifierImpact.ModifierType type = StatModifierImpact.ModifierType.valueOf(typeStr);
                return switch (type) {
                    case ADD -> baseStat + value;
                    case MULTIPLY -> baseStat * value;
                    case SET -> value;
                };
            }
        }

        return baseStat;
    }

    /**
     * Called when this spell hits a barrier
     */
    public abstract void onHitBarrier(BarrierSpell barrier, Location location, Player caster);

    /**
     * Called when this spell is fully countered
     */
    public abstract void onCountered(Location location);

}
