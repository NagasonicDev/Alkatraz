package me.nagasonic.alkatraz.api.magic.trigger;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.effect.Effect;
import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.List;

/**
 * An immutable binding that connects a {@link TriggerType} to a set of {@link Condition conditions}
 * and {@link Effect effects} with an execution priority.
 * <p>
 * When a trigger fires, bindings are evaluated in priority order. If all conditions in a binding
 * are met, its effects are executed.
 *
 * @param triggerType the {@link NamespacedKey} identifying the trigger type
 * @param conditions  the list of conditions that must be satisfied before effects run
 * @param effects     the list of effects to execute when the trigger fires and conditions are met
 * @param priority    execution priority; lower values are evaluated first
 */
public record TriggerBinding(
        NamespacedKey triggerType,
        List<Condition> conditions,
        List<Effect> effects,
        int priority
) implements Keyed {

    /**
     * {@inheritDoc}
     */
    @Override
    public NamespacedKey getKey() {
        return triggerType;
    }
}
