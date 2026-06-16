package me.nagasonic.alkatraz.items.magic.trigger;

import me.nagasonic.alkatraz.items.magic.condition.Condition;
import me.nagasonic.alkatraz.items.magic.effect.Effect;
import me.nagasonic.alkatraz.items.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.List;

/**
 * Connects a trigger type to conditional effects on a modifier or definition.
 */
public record TriggerBinding(
        NamespacedKey triggerType,
        List<Condition> conditions,
        List<Effect> effects,
        int priority
) implements Keyed {

    @Override
    public NamespacedKey getKey() {
        return triggerType;
    }
}
