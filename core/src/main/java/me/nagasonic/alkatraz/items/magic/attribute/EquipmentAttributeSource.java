package me.nagasonic.alkatraz.items.magic.attribute;

import me.nagasonic.alkatraz.items.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentProfile;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentService;
import me.nagasonic.alkatraz.items.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.modifier.ModifierDefinition;
import me.nagasonic.alkatraz.items.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerContext;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Aggregates attributes from equipped magic item definitions and modifiers.
 */
public final class EquipmentAttributeSource implements AttributeSource {

    private final EquipmentService equipmentService;

    public EquipmentAttributeSource(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @Override
    public AttributeContribution.AttributeSourceType sourceType() {
        return AttributeContribution.AttributeSourceType.EQUIPMENT;
    }

    @Override
    public Collection<AttributeContribution> collect(LivingEntity entity, TriggerContext context) {
        List<AttributeContribution> contributions = new ArrayList<>();
        if (!(entity instanceof Player player)) {
            return contributions;
        }

        EquipmentProfile profile = equipmentService.profile(player);
        for (MagicItemInstance instance : profile.instances().values()) {
            MagicItemRegistries.ITEM_DEFINITIONS.get(instance.definitionKey()).ifPresent(definition ->
                    appendAttributes(contributions, definition.attributes(), AttributeContribution.AttributeSourceType.DEFINITION));

            for (NamespacedKey modifierKey : instance.modifiers()) {
                MagicItemRegistries.MODIFIER_DEFINITIONS.get(modifierKey).ifPresent(modifier ->
                        appendAttributes(contributions, modifier.attributes(), AttributeContribution.AttributeSourceType.MODIFIER));
            }

            for (NamespacedKey socketKey : instance.sockets()) {
                MagicItemRegistries.MODIFIER_DEFINITIONS.get(socketKey).ifPresent(modifier ->
                        appendAttributes(contributions, modifier.attributes(), AttributeContribution.AttributeSourceType.MODIFIER));
            }
        }

        return contributions;
    }

    private static void appendAttributes(
            List<AttributeContribution> contributions,
            java.util.Map<NamespacedKey, Double> attributes,
            AttributeContribution.AttributeSourceType sourceType
    ) {
        for (var entry : attributes.entrySet()) {
            contributions.add(new AttributeContribution(
                    entry.getKey(),
                    entry.getValue(),
                    AttributeContribution.AttributeOperation.ADD,
                    sourceType,
                    0
            ));
        }
    }
}
