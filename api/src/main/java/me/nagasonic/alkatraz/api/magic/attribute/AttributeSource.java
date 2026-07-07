package me.nagasonic.alkatraz.api.magic.attribute;

import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

import java.util.Collection;

public interface AttributeSource {

    AttributeContribution.AttributeSourceType sourceType();

    Collection<AttributeContribution> collect(LivingEntity entity, TriggerContext context);
}
