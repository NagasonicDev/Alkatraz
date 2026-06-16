package me.nagasonic.alkatraz.items.magic.trigger;

import me.nagasonic.alkatraz.items.magic.condition.ConditionEvaluator;
import me.nagasonic.alkatraz.items.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.items.magic.effect.EffectExecutor;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentProfile;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentService;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.items.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.modifier.ModifierDefinition;
import me.nagasonic.alkatraz.items.magic.registry.MagicItemRegistries;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Event → context → relevant items → modifiers → conditions → effects.
 */
public final class TriggerPipeline {

    private final EquipmentService equipmentService;

    public TriggerPipeline(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    public void dispatch(InternalTriggerEvent event) {
        TriggerContext baseContext = event.context();
        if (baseContext.isCancelled() || baseContext.actor() == null) {
            return;
        }

        List<ResolvedBinding> bindings = collectBindings(baseContext, event.triggerType());
        bindings.sort(Comparator.comparingInt(ResolvedBinding::priority));

        for (ResolvedBinding binding : bindings) {
            TriggerContext scoped = baseContext.withSource(binding.instance(), binding.slot());
            if (!ConditionEvaluator.allMatch(binding.binding().conditions(), scoped)) {
                continue;
            }
            EffectExecutor.executeAll(binding.binding().effects(), scoped);
            if (scoped.isCancelled()) {
                baseContext.setCancelled(true);
                return;
            }
        }
    }

    private List<ResolvedBinding> collectBindings(TriggerContext context, org.bukkit.NamespacedKey triggerType) {
        List<ResolvedBinding> resolved = new ArrayList<>();
        LivingEntity actor = context.actor();

        if (context.sourceItem() != null) {
            collectForInstance(context.sourceItem(), context.equipmentSlot(), triggerType, resolved);
        }

        if (actor instanceof Player player) {
            EquipmentProfile profile = equipmentService.profile(player);
            for (var entry : profile.instances().entrySet()) {
                collectForInstance(entry.getValue(), entry.getKey(), triggerType, resolved);
            }
        }

        return resolved;
    }

    private void collectForInstance(
            MagicItemInstance instance,
            EquipmentSlot slot,
            org.bukkit.NamespacedKey triggerType,
            List<ResolvedBinding> resolved
    ) {
        MagicItemRegistries.ITEM_DEFINITIONS.get(instance.definitionKey()).ifPresent(definition ->
                collectFromDefinition(definition, instance, slot, triggerType, resolved));

        for (org.bukkit.NamespacedKey modifierKey : instance.modifiers()) {
            MagicItemRegistries.MODIFIER_DEFINITIONS.get(modifierKey).ifPresent(modifier ->
                    collectFromModifier(modifier, instance, slot, triggerType, resolved));
        }

        for (org.bukkit.NamespacedKey socketKey : instance.sockets()) {
            MagicItemRegistries.MODIFIER_DEFINITIONS.get(socketKey).ifPresent(modifier ->
                    collectFromModifier(modifier, instance, slot, triggerType, resolved));
        }
    }

    private void collectFromDefinition(
            ItemDefinition definition,
            MagicItemInstance instance,
            EquipmentSlot slot,
            org.bukkit.NamespacedKey triggerType,
            List<ResolvedBinding> resolved
    ) {
        for (TriggerBinding binding : definition.triggers()) {
            if (binding.triggerType().equals(triggerType)) {
                resolved.add(new ResolvedBinding(binding, instance, slot));
            }
        }
    }

    private void collectFromModifier(
            ModifierDefinition modifier,
            MagicItemInstance instance,
            EquipmentSlot slot,
            org.bukkit.NamespacedKey triggerType,
            List<ResolvedBinding> resolved
    ) {
        for (TriggerBinding binding : modifier.triggers()) {
            if (binding.triggerType().equals(triggerType)) {
                resolved.add(new ResolvedBinding(binding, instance, slot));
            }
        }
    }

    private record ResolvedBinding(TriggerBinding binding, MagicItemInstance instance, EquipmentSlot slot) {
        int priority() {
            return binding.priority();
        }
    }
}
