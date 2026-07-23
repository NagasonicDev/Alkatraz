package me.nagasonic.alkatraz.items.magic.attribute;

import me.nagasonic.alkatraz.api.magic.attribute.AttributeContribution;

import me.nagasonic.alkatraz.api.magic.attribute.AttributeSource;

import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentProfile;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentService;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.api.magic.instance.Engraving;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.items.magic.service.SetBonusService;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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
        if (entity == null) return contributions;
        if (!(entity instanceof Player player)) {
            for (NamespacedKey attr : MagicItemRegistries.ATTRIBUTE_TYPES.keySet()) {
                MagicProfile profile = ProfileManager.getProfile(entity.getUniqueId(), MagicProfile.class);
                if (profile != null) {
                    double value = getAttributeFromProfile(profile, attr);
                    contributions.add(new AttributeContribution(attr, value, AttributeContribution.AttributeOperation.ADD, 
                            AttributeContribution.AttributeSourceType.BASE, 0));
                }
            }
            return contributions;
        }

        // Try the full equipment profile (includes virtual/ring slots, main hand, off-hand, armor)
        EquipmentProfile profile = null;
        try {
            profile = equipmentService.profile(player);
        } catch (Exception e) {
            me.nagasonic.alkatraz.Alkatraz.getInstance().getServer().getLogger().warning(
                    "[Alkatraz] EquipmentService.profile() failed, falling back to direct slot scan: " + e.getMessage());
        }

        if (profile != null) {
            for (MagicItemInstance instance : profile.instances().values()) {
                processInstance(contributions, instance);
            }
            contributions.addAll(SetBonusService.getInstance().getSetBonuses(profile));
        } else {
            // Fallback: scan vanilla armor slots directly
            PlayerInventory inv = player.getInventory();
            int[] armorSlots = {36, 37, 38, 39, 40};
            for (int slot : armorSlots) {
                ItemStack item = inv.getItem(slot);
                if (item == null || item.getType().isAir()) continue;
                MagicItemStack.readInstance(item).ifPresent(instance ->
                        processInstance(contributions, instance));
            }
        }

        return contributions;
    }

    private double getAttributeFromProfile(MagicProfile profile, NamespacedKey attr) {
        String key = attr.getNamespace() + "_" + attr.getKey();
        if (profile.isDouble(key)) {
            return profile.getDouble(key);
        }
        if (profile.isInt(key)) {
            return profile.getInt(key);
        }
        return 0.0;
    }

    private void processInstance(List<AttributeContribution> contributions, MagicItemInstance instance) {
        MagicItemRegistries.ITEM_DEFINITIONS.get(instance.definitionKey()).ifPresent(definition ->
                appendAttributes(contributions, definition.attributes(), AttributeContribution.AttributeSourceType.DEFINITION));

        for (NamespacedKey modifierKey : instance.modifiers()) {
            MagicItemRegistries.ENGRAVING_DEFINITIONS.get(modifierKey).ifPresent(modifier ->
                    appendAttributes(contributions, modifier.attributes(), AttributeContribution.AttributeSourceType.MODIFIER));
        }

        for (Engraving engraving : instance.engravings()) {
            MagicItemRegistries.ENGRAVING_DEFINITIONS.get(engraving.engravingKey()).ifPresent(engDef ->
                    appendAttributes(contributions, engDef.attributes(), AttributeContribution.AttributeSourceType.MODIFIER));
        }
    }

    private static void appendAttributes(
            List<AttributeContribution> contributions,
            java.util.Map<NamespacedKey, Double> attributes,
            AttributeContribution.AttributeSourceType sourceType
    ) {
        for (var entry : attributes.entrySet()) {
            AttributeContribution.AttributeOperation operation = AttributeContribution.AttributeOperation.ADD;
            String key = entry.getKey().getNamespace() + "_" + entry.getKey().getKey();
            if (key.contains("_set_") || key.contains("_multiply_")) {
                operation = key.contains("_set_") ? 
                        AttributeContribution.AttributeOperation.SET : 
                        AttributeContribution.AttributeOperation.MULTIPLY;
            }
            contributions.add(new AttributeContribution(
                    entry.getKey(),
                    entry.getValue(),
                    operation,
                    sourceType,
                    0
            ));
        }
    }
}
