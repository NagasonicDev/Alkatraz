package me.nagasonic.alkatraz.api.ai;

import me.nagasonic.alkatraz.api.ai.attribute.AttributeProfile;
import me.nagasonic.alkatraz.api.ai.control.ControlProfile;
import me.nagasonic.alkatraz.api.ai.task.TaskBrain;

import java.util.Optional;

/**
 * The combined per-mob AI document for all general subsystems: an optional
 * {@link TaskBrain}, an optional {@link ControlProfile} and an optional
 * {@link AttributeProfile}. Goals are intentionally not part of this general
 * document (goal specifications are declared separately in the goal-brain API).
 * A {@code null} slot is normalized
 * to {@code Optional.empty()}.
 *
 * @param taskBrain   the memory/behavior driven brain, if any
 * @param control     movement/look/jump/navigation actuators, if any
 * @param attributes  attribute overrides, if any
 */
public record AiProfile(
        Optional<TaskBrain> taskBrain,
        Optional<ControlProfile> control,
        Optional<AttributeProfile> attributes) {

    public AiProfile {
        taskBrain = normalize(taskBrain);
        control = normalize(control);
        attributes = normalize(attributes);
    }

    private static <T> Optional<T> normalize(Optional<T> slot) {
        return slot == null ? Optional.empty() : slot;
    }
}
