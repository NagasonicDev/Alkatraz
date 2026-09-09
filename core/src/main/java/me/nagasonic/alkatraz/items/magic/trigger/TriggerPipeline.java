package me.nagasonic.alkatraz.items.magic.trigger;

import me.nagasonic.alkatraz.api.magic.trigger.InternalTriggerEvent;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerBinding;

import me.nagasonic.alkatraz.items.magic.condition.ConditionEvaluator;
import me.nagasonic.alkatraz.items.magic.condition.implementation.CooldownCondition;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.items.magic.effect.EffectExecutor;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentProfile;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentService;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.api.magic.instance.Engraving;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Event Ã¢â€ â€™ context Ã¢â€ â€™ relevant items Ã¢â€ â€™ modifiers Ã¢â€ â€™ conditions Ã¢â€ â€™ effects.
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
        me.nagasonic.alkatraz.Alkatraz.logInfo("[DBG barrier] dispatch: trigger=" + event.triggerType().getKey()
                + " actor=" + (baseContext.actor() instanceof Player ? ((Player) baseContext.actor()).getName() : baseContext.actor())
                + " bindings=" + bindings.size());
        bindings.sort(Comparator.comparingInt(ResolvedBinding::priority));

        for (ResolvedBinding binding : bindings) {
            TriggerContext scoped = baseContext.withSource(binding.instance(), binding.slot());
            if (!ConditionEvaluator.allMatch(binding.binding().conditions(), scoped)) {
                me.nagasonic.alkatraz.Alkatraz.logInfo("[DBG barrier] dispatch: binding for " + binding.instance().definitionKey()
                        + "@" + binding.slot().getKey().getKey() + " FAILED conditions, skipped");
                continue;
            }
            me.nagasonic.alkatraz.Alkatraz.logInfo("[DBG barrier] dispatch: running effects for " + binding.instance().definitionKey()
                    + "@" + binding.slot().getKey().getKey());
            EffectExecutor.executeAll(binding.binding().effects(), scoped);
            if (scoped.isCancelled()) {
                baseContext.setCancelled(true);
                return;
            }
            CooldownCondition.commitPending(binding.binding().conditions(), scoped);
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
            me.nagasonic.alkatraz.Alkatraz.logInfo("[DBG barrier] collectBindings: equipment profile has "
                    + profile.instances().size() + " magic instances for " + player.getName());
            profile.instances().forEach((slot, inst) ->
                    me.nagasonic.alkatraz.Alkatraz.logInfo("[DBG barrier] collectBindings:   slot=" + slot.getKey().getKey()
                            + " def=" + inst.definitionKey() + " engravings=" + inst.engravings()));
            for (var entry : profile.instances().entrySet()) {
                collectForInstance(entry.getValue(), entry.getKey(), triggerType, resolved);
            }
        }

        return deduplicate(resolved);
    }

    /**
     * Removes duplicate bindings for the same item (definition + slot). A source item
     * linked into the context (e.g. the weapon that fired a projectile) is usually also
     * present in the actor's equipment profile, so without deduplication its trigger
     * would fire twice.
     */
    private List<ResolvedBinding> deduplicate(List<ResolvedBinding> resolved) {
        Set<String> seen = new HashSet<>();
        List<ResolvedBinding> deduped = new ArrayList<>();
        for (ResolvedBinding binding : resolved) {
            String key = binding.instance().definitionKey().toString() + "@" + binding.slot().getKey().toString();
            if (seen.add(key)) {
                deduped.add(binding);
            }
        }
        return deduped;
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
            MagicItemRegistries.ENGRAVING_DEFINITIONS.get(modifierKey).ifPresent(modifier ->
                    collectFromEngraving(modifier, instance, slot, triggerType, resolved));
        }

        for (Engraving engraving : instance.engravings()) {
            if (engraving.triggerKey().equals(triggerType)) {
                MagicItemRegistries.ENGRAVING_DEFINITIONS.get(engraving.engravingKey()).ifPresent(engDef -> {
                    resolved.add(new ResolvedBinding(
                            new TriggerBinding(engraving.triggerKey(), engDef.conditions(), engDef.effects(), 0),
                            instance, slot));
                });
            }
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

    private void collectFromEngraving(
            EngravingDefinition engraving,
            MagicItemInstance instance,
            EquipmentSlot slot,
            org.bukkit.NamespacedKey triggerType,
            List<ResolvedBinding> resolved
    ) {
        // Legacy: old modifiers may have baked-in trigger bindings
        // New engravings are handled via instance.engravings() loop above
    }

    private record ResolvedBinding(TriggerBinding binding, MagicItemInstance instance, EquipmentSlot slot) {
        int priority() {
            return binding.priority();
        }
    }
}
