package me.nagasonic.alkatraz.api.magic.trigger;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.effect.Effect;
import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.List;

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
