package me.nagasonic.alkatraz.spells.modifier;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.LivingEntity;

/**
 * A temporary effect applied when a spell is cast (e.g. Buff / Debuff).
 */
public abstract class AppliedModifier {

    private final String description;

    protected AppliedModifier(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public abstract void apply(LivingEntity entity, Spell source);

    public abstract void remove(LivingEntity entity, Spell source);
}
