package me.nagasonic.alkatraz.items.magic.trigger;

import me.nagasonic.alkatraz.items.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.items.magic.instance.MagicItemInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mutable execution context passed through the trigger pipeline.
 */
public final class TriggerContext {

    private final LivingEntity actor;
    private final LivingEntity target;
    private final NamespacedKey triggerType;
    private final MagicItemInstance sourceItem;
    private final EquipmentSlot equipmentSlot;
    private final Map<String, Object> parameters;
    private boolean cancelled;

    public TriggerContext(
            LivingEntity actor,
            LivingEntity target,
            NamespacedKey triggerType,
            MagicItemInstance sourceItem,
            EquipmentSlot equipmentSlot,
            Map<String, Object> parameters
    ) {
        this.actor = actor;
        this.target = target;
        this.triggerType = triggerType;
        this.sourceItem = sourceItem;
        this.equipmentSlot = equipmentSlot;
        this.parameters = new HashMap<>(parameters != null ? parameters : Map.of());
    }

    public static TriggerContext empty(LivingEntity actor) {
        return new TriggerContext(actor, null, null, null, null, Map.of());
    }

    public LivingEntity actor() {
        return actor;
    }

    public Optional<Player> playerActor() {
        return actor instanceof Player player ? Optional.of(player) : Optional.empty();
    }

    public LivingEntity target() {
        return target;
    }

    public NamespacedKey triggerType() {
        return triggerType;
    }

    public MagicItemInstance sourceItem() {
        return sourceItem;
    }

    public EquipmentSlot equipmentSlot() {
        return equipmentSlot;
    }

    public Map<String, Object> parameters() {
        return Collections.unmodifiableMap(parameters);
    }

    public Object parameter(String key) {
        return parameters.get(key);
    }

    public void setParameter(String key, Object value) {
        parameters.put(key, value);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public TriggerContext withSource(MagicItemInstance item, EquipmentSlot slot) {
        return new TriggerContext(actor, target, triggerType, item, slot, parameters);
    }
}
