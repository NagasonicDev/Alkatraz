package me.nagasonic.alkatraz.api.magic.attribute;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

import java.util.Collection;

/**
 * Strategy interface for providers that supply {@link AttributeContribution contributions}
 * for an entity. Implementations are registered with {@link AttributeService} and called
 * during snapshot resolution.
 */
public interface AttributeSource {

    /**
     * Returns the source type that categorises all contributions produced by this source.
     *
     * @return the {@link AttributeContribution.AttributeSourceType} of this source
     */
    AttributeContribution.AttributeSourceType sourceType();

    /**
     * Collects all attribute contributions this source provides for the given entity
     * within the specified trigger context.
     *
     * @param entity  the living entity to collect contributions for
     * @param context the trigger context influencing attribute collection
     * @return a collection of {@link AttributeContribution contributions}; may be empty
     */
    Collection<AttributeContribution> collect(LivingEntity entity, TriggerContext context);
}
