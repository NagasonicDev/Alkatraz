package me.nagasonic.alkatraz.items.magic.trigger;

import me.nagasonic.alkatraz.Alkatraz;
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
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event Ã¢â€ â€™ context Ã¢â€ â€™ relevant items Ã¢â€ â€™ modifiers Ã¢â€ â€™ conditions Ã¢â€ â€™ effects.
 */
public final class TriggerPipeline {

    /**
     * Union of every trigger type declared by a registered item definition or engraving
     * definition. While non-empty, a dispatch whose trigger type is absent from this set
     * can be skipped entirely: no equipped item could possibly produce a binding for it.
     * An empty set means "index unavailable" and disables the shortcut, preserving the
     * behaviour of resolving bindings for every dispatch.
     */
    private static volatile Set<NamespacedKey> BOUND_TRIGGERS = Set.of();

    /**
     * Cached {@link NamespacedKey} instances for the constant trigger paths used by
     * listeners as hot-path guards. {@link NamespacedKey} is immutable with
     * value-based {@code equals}/{@code hashCode}, so sharing instances is safe.
     */
    private static final ConcurrentHashMap<String, NamespacedKey> BOUND_KEY_CACHE = new ConcurrentHashMap<>();

    private final EquipmentService equipmentService;

    public TriggerPipeline(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    /** Replaces the bound-trigger index. Passing {@code null} or an empty set disables the shortcut. */
    public static void setBoundTriggers(Set<NamespacedKey> types) {
        BOUND_TRIGGERS = (types == null || types.isEmpty()) ? Set.of() : Set.copyOf(types);
    }

    /** Memoised {@link MagicKeys#alkatraz(String)} lookup used by {@link #isBound(NamespacedKey)} callers. */
    public static NamespacedKey boundKey(String path) {
        NamespacedKey cached = BOUND_KEY_CACHE.get(path);
        if (cached != null) {
            return cached;
        }
        NamespacedKey key = MagicKeys.alkatraz(path);
        if (BOUND_KEY_CACHE.size() > 1024) {
            BOUND_KEY_CACHE.clear();
        }
        BOUND_KEY_CACHE.put(path, key);
        return key;
    }

    /**
     * Whether any loaded item or engraving definition declares the given trigger type, so a
     * dispatch for it could resolve at least one binding.
     *
     * <p>Fails open: when the index is empty or unavailable (its {@code Set.of()} sentinel)
     * this returns {@code true}, preserving the pre-existing behaviour of resolving bindings
     * for every dispatch. Callers therefore use this only to skip work that
     * {@link #dispatch(InternalTriggerEvent)} would have discarded anyway.
     */
    public static boolean isBound(NamespacedKey triggerKey) {
        Set<NamespacedKey> bound = BOUND_TRIGGERS;
        if (bound.isEmpty()) {
            return true;
        }
        return triggerKey != null && bound.contains(triggerKey);
    }

    /**
     * Rebuilds the bound-trigger index from the registries that back item and engraving
     * definitions. Any definition whose trigger declarations cannot be read is treated as
     * "may bind anything", which collapses the index to empty and disables the shortcut
     * rather than silently dropping a trigger.
     */
    public static void refreshBoundTriggers() {
        Set<NamespacedKey> types = new HashSet<>();
        for (ItemDefinition definition : MagicItemRegistries.ITEM_DEFINITIONS.values()) {
            if (definition.triggers() == null) {
                BOUND_TRIGGERS = Set.of();
                return;
            }
            for (TriggerBinding binding : definition.triggers()) {
                if (binding == null || binding.triggerType() == null) {
                    BOUND_TRIGGERS = Set.of();
                    return;
                }
                types.add(binding.triggerType());
            }
        }
        for (EngravingDefinition engraving : MagicItemRegistries.ENGRAVING_DEFINITIONS.values()) {
            for (String raw : declaredEngravingTriggers(engraving)) {
                Optional<NamespacedKey> parsed = MagicKeys.parse(raw);
                if (parsed.isEmpty()) {
                    BOUND_TRIGGERS = Set.of();
                    return;
                }
                types.add(parsed.get());
            }
        }
        types.add(MagicKeys.alkatraz("passive"));
        BOUND_TRIGGERS = Set.copyOf(types);
        Alkatraz.logInfo("Bound-trigger index built for " + types.size() + " trigger type(s).");
    }

    private static Collection<String> declaredEngravingTriggers(EngravingDefinition engraving) {
        Object raw = engraving.staticConfig().get("triggers");
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            return List.of(String.valueOf(raw));
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object entry : list) {
            out.add(String.valueOf(entry));
        }
        return out;
    }

    public void dispatch(InternalTriggerEvent event) {
        TriggerContext baseContext = event.context();
        if (baseContext.isCancelled() || baseContext.actor() == null) {
            return;
        }

        Set<NamespacedKey> bound = BOUND_TRIGGERS;
        if (!bound.isEmpty() && !bound.contains(event.triggerType())) {
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
