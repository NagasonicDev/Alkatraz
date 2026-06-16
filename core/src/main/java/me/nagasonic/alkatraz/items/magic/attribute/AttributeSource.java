package me.nagasonic.alkatraz.items.magic.attribute;

import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;
import org.bukkit.entity.LivingEntity;

import java.util.Collection;

/**
 * Supplies attribute contributions for a living entity (equipment, buffs, skills, etc.).
 */
public interface AttributeSource {

    AttributeContribution.AttributeSourceType sourceType();

    Collection<AttributeContribution> collect(LivingEntity entity, TriggerContext context);
}
