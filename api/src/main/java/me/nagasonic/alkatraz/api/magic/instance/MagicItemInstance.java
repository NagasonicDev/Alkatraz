package me.nagasonic.alkatraz.api.magic.instance;

import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A concrete instance of a magic item, carrying a unique identity and mutable state.
 * Each instance tracks its own modifiers, engravings, progression data, and arbitrary
 * custom data. An instance is linked to an {@link me.nagasonic.alkatraz.api.magic.definition.MagicItemDefinition}
 * via {@link #definitionKey()}.
 */
public final class MagicItemInstance {

    private UUID instanceId;
    private NamespacedKey definitionKey;
    private List<NamespacedKey> modifiers;
    private List<Engraving> engravings;
    private Map<String, Object> progression;
    private Map<String, Object> customData;

    /**
     * Constructs a new magic item instance with full state.
     *
     * @param instanceId    unique identifier for this instance, or {@code null} to auto-generate
     * @param definitionKey key of the item definition this instance is based on
     * @param modifiers     list of modifier keys applied to this instance, or {@code null} for none
     * @param engravings    list of engravings applied to this instance, or {@code null} for none
     * @param progression   progression data map, or {@code null} for an empty map
     * @param customData    arbitrary custom data map, or {@code null} for an empty map
     */
    public MagicItemInstance(
            UUID instanceId,
            NamespacedKey definitionKey,
            List<NamespacedKey> modifiers,
            List<Engraving> engravings,
            Map<String, Object> progression,
            Map<String, Object> customData
    ) {
        this.instanceId = instanceId != null ? instanceId : UUID.randomUUID();
        this.definitionKey = definitionKey;
        this.modifiers = copyKeyList(modifiers);
        this.engravings = engravings == null ? List.of() : List.copyOf(engravings);
        this.progression = copyMap(progression);
        this.customData = copyMap(customData);
    }

    /**
     * Creates a default magic item instance with the given definition and no modifiers,
     * engravings, or data.
     *
     * @param definitionKey key of the item definition
     * @return a new instance with a random UUID and empty state
     */
    public static MagicItemInstance createDefault(NamespacedKey definitionKey) {
        return new MagicItemInstance(UUID.randomUUID(), definitionKey, List.of(), List.of(), Map.of(), Map.of());
    }

    /**
     * Returns the unique identifier for this instance.
     *
     * @return the instance UUID
     */
    public UUID instanceId() {
        return instanceId;
    }

    /**
     * Returns the key of the item definition this instance is based on.
     *
     * @return the definition key
     */
    public NamespacedKey definitionKey() {
        return definitionKey;
    }

    /**
     * Returns an unmodifiable list of modifier keys applied to this instance.
     *
     * @return the modifier keys
     */
    public List<NamespacedKey> modifiers() {
        return modifiers;
    }

    /**
     * Returns an unmodifiable list of engravings applied to this instance.
     *
     * @return the engravings
     */
    public List<Engraving> engravings() {
        return engravings;
    }

    /**
     * Returns an unmodifiable map of progression data.
     *
     * @return the progression map
     */
    public Map<String, Object> progression() {
        return progression;
    }

    /**
     * Returns an unmodifiable map of arbitrary custom data.
     *
     * @return the custom data map
     */
    public Map<String, Object> customData() {
        return customData;
    }

    /**
     * Replaces the modifier list with a new set of modifiers.
     *
     * @param modifiers the new modifier key list, or {@code null} to clear
     */
    public void setModifiers(List<NamespacedKey> modifiers) {
        this.modifiers = copyKeyList(modifiers);
    }

    /**
     * Appends a modifier to this instance if it is not already present.
     *
     * @param modifier the modifier key to add
     */
    public void addModifier(NamespacedKey modifier) {
        if (!modifiers.contains(modifier)) {
            modifiers = new java.util.ArrayList<>(modifiers);
            modifiers.add(modifier);
        }
    }

    /**
     * Replaces the engraving list with a new set of engravings.
     *
     * @param engravings the new engraving list, or {@code null} to clear
     */
    public void setEngravings(List<Engraving> engravings) {
        this.engravings = engravings == null ? List.of() : List.copyOf(engravings);
    }

    /**
     * Appends an engraving to this instance.
     *
     * @param engraving the engraving to add
     */
    public void addEngraving(Engraving engraving) {
        if (this.engravings.isEmpty()) {
            this.engravings = new ArrayList<>();
        } else {
            this.engravings = new ArrayList<>(this.engravings);
        }
        this.engravings.add(engraving);
    }

    /**
     * Stores a value in the progression map, overwriting any existing value for the same key.
     *
     * @param key   the progression key
     * @param value the value to store
     */
    public void putProgression(String key, Object value) {
        progression = new LinkedHashMap<>(progression);
        progression.put(key, value);
    }

    /**
     * Stores a value in the custom data map, overwriting any existing value for the same key.
     *
     * @param key   the custom data key
     * @param value the value to store
     */
    public void putCustomData(String key, Object value) {
        customData = new LinkedHashMap<>(customData);
        customData.put(key, value);
    }

    /**
     * Creates a shallow copy of this instance, sharing the same {@link #instanceId()}.
     *
     * @return a new {@link MagicItemInstance} with identical state
     */
    public MagicItemInstance copy() {
        return new MagicItemInstance(instanceId, definitionKey, modifiers, engravings, progression, customData);
    }

    private static List<NamespacedKey> copyKeyList(List<NamespacedKey> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
