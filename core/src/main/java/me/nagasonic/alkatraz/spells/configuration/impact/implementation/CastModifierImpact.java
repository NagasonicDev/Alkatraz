package me.nagasonic.alkatraz.spells.configuration.impact.implementation;

import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpact;
import me.nagasonic.alkatraz.spells.modifier.AppliedModifier;
import org.bukkit.entity.Player;

/**
 * Declares a cast-time modifier on an option value. The modifier is not applied
 * when the player selects the option — the spell reads it at cast time instead.
 */
public class CastModifierImpact implements ValueImpact {

    private final AppliedModifier modifier;

    public CastModifierImpact(AppliedModifier modifier) {
        this.modifier = modifier;
    }

    public AppliedModifier getModifier() {
        return modifier;
    }

    @Override
    public void apply(Player player) {
        // Cast-time only; selection does not apply gameplay effects.
    }

    @Override
    public void unapply(Player player) {
        // Cast-time only.
    }

    @Override
    public String getDescription() {
        return modifier.getDescription();
    }
}
