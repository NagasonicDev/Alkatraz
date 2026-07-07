package me.nagasonic.alkatraz.api.magic.instance;

import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MagicItemInstance {

    private UUID instanceId;
    private NamespacedKey definitionKey;
    private List<NamespacedKey> modifiers;
    private List<Engraving> engravings;
    private Map<String, Object> progression;
    private Map<String, Object> customData;

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

    public static MagicItemInstance createDefault(NamespacedKey definitionKey) {
        return new MagicItemInstance(UUID.randomUUID(), definitionKey, List.of(), List.of(), Map.of(), Map.of());
    }

    public UUID instanceId() {
        return instanceId;
    }

    public NamespacedKey definitionKey() {
        return definitionKey;
    }

    public List<NamespacedKey> modifiers() {
        return modifiers;
    }

    public List<Engraving> engravings() {
        return engravings;
    }

    public Map<String, Object> progression() {
        return progression;
    }

    public Map<String, Object> customData() {
        return customData;
    }

    public void setModifiers(List<NamespacedKey> modifiers) {
        this.modifiers = copyKeyList(modifiers);
    }

    public void addModifier(NamespacedKey modifier) {
        if (!modifiers.contains(modifier)) {
            modifiers = new java.util.ArrayList<>(modifiers);
            modifiers.add(modifier);
        }
    }

    public void setEngravings(List<Engraving> engravings) {
        this.engravings = engravings == null ? List.of() : List.copyOf(engravings);
    }

    public void addEngraving(Engraving engraving) {
        if (this.engravings.isEmpty()) {
            this.engravings = new ArrayList<>();
        } else {
            this.engravings = new ArrayList<>(this.engravings);
        }
        this.engravings.add(engraving);
    }

    public void putProgression(String key, Object value) {
        progression = new LinkedHashMap<>(progression);
        progression.put(key, value);
    }

    public void putCustomData(String key, Object value) {
        customData = new LinkedHashMap<>(customData);
        customData.put(key, value);
    }

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
