package me.nagasonic.alkatraz.api.magic.instance;

import org.bukkit.NamespacedKey;

/**
 * Represents a concrete engraving applied to a {@link MagicItemInstance}.
 * An engraving links an {@link me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition}
 * (identified by {@code engravingKey}) with a specific trigger
 * (identified by {@code triggerKey}).
 */
public final class Engraving {

    private final NamespacedKey engravingKey;
    private final NamespacedKey triggerKey;

    /**
     * Constructs an engraving instance.
     *
     * @param engravingKey the key of the {@link me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition}
     * @param triggerKey   the key of the trigger that activates this engraving
     */
    public Engraving(NamespacedKey engravingKey, NamespacedKey triggerKey) {
        this.engravingKey = engravingKey;
        this.triggerKey = triggerKey;
    }

    /**
     * Returns the key identifying the engraving definition.
     *
     * @return the engraving's {@link NamespacedKey}
     */
    public NamespacedKey engravingKey() {
        return engravingKey;
    }

    /**
     * Returns the key identifying the trigger that activates this engraving.
     *
     * @return the trigger's {@link NamespacedKey}
     */
    public NamespacedKey triggerKey() {
        return triggerKey;
    }
}
