package me.nagasonic.alkatraz.api.magic.trigger;

import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Carries all contextual data for a single trigger invocation, including the acting entity,
 * target entity, source item, equipment slot, and arbitrary parameters.
 * <p>
 * Contexts are created when a trigger fires and passed to {@link me.nagasonic.alkatraz.api.magic.condition.Condition}
 * and {@link me.nagasonic.alkatraz.api.magic.effect.Effect} instances during evaluation.
 * A context can be cancelled to prevent further processing.
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

    /**
     * Creates a minimal context with only an actor and no other data.
     *
     * @param actor the acting entity
     * @return a new {@link TriggerContext} with no target, trigger type, source item, or parameters
     */
    public static TriggerContext empty(LivingEntity actor) {
        return new TriggerContext(actor, null, null, null, null, Map.of());
    }

    /**
     * Returns the entity that initiated this trigger (e.g. the player who killed, equipped, or cast).
     *
     * @return the acting entity, never {@code null}
     */
    public LivingEntity actor() {
        return actor;
    }

    /**
     * Returns the actor as a {@link Player} if they are a player, otherwise {@link Optional#empty()}.
     *
     * @return an {@link Optional} containing the player actor, or empty
     */
    public Optional<Player> playerActor() {
        return actor instanceof Player player ? Optional.of(player) : Optional.empty();
    }

    /**
     * Returns the target entity of this trigger (e.g. the entity killed or hit).
     *
     * @return the target entity, or {@code null} if not applicable
     */
    public LivingEntity target() {
        return target;
    }

    /**
     * Returns the {@link NamespacedKey} identifying the trigger type that fired.
     *
     * @return the trigger type key, or {@code null} if not set
     */
    public NamespacedKey triggerType() {
        return triggerType;
    }

    /**
     * Returns the magic item instance that originated this trigger.
     *
     * @return the source item, or {@code null} if not applicable
     */
    public MagicItemInstance sourceItem() {
        return sourceItem;
    }

    /**
     * Returns the equipment slot the source item occupies.
     *
     * @return the equipment slot, or {@code null} if not applicable
     */
    public EquipmentSlot equipmentSlot() {
        return equipmentSlot;
    }

    /**
     * Returns an unmodifiable view of all parameters attached to this context.
     *
     * @return an unmodifiable map of parameter key-value pairs
     */
    public Map<String, Object> parameters() {
        return Collections.unmodifiableMap(parameters);
    }

    /**
     * Returns a single parameter value by key.
     *
     * @param key the parameter key
     * @return the parameter value, or {@code null} if not present
     */
    public Object parameter(String key) {
        return parameters.get(key);
    }

    /**
     * Sets or replaces a parameter value in this context.
     *
     * @param key   the parameter key
     * @param value the parameter value
     */
    public void setParameter(String key, Object value) {
        parameters.put(key, value);
    }

    /**
     * Returns whether this trigger has been cancelled.
     *
     * @return {@code true} if cancelled, {@code false} otherwise
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets the cancelled state of this trigger.
     *
     * @param cancelled {@code true} to cancel, {@code false} to un-cancel
     */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * Returns a new {@link TriggerContext} identical to this one but with a different source item and slot.
     * All other fields (actor, target, trigger type, parameters, cancelled state) are preserved.
     *
     * @param item the new source item
     * @param slot the new equipment slot
     * @return a new context with the updated source
     */
    public TriggerContext withSource(MagicItemInstance item, EquipmentSlot slot) {
        return new TriggerContext(actor, target, triggerType, item, slot, parameters);
    }
}
