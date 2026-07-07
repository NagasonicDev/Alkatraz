package me.nagasonic.alkatraz.api.magic.instance;

import org.bukkit.NamespacedKey;

public final class Engraving {

    private final NamespacedKey engravingKey;
    private final NamespacedKey triggerKey;

    public Engraving(NamespacedKey engravingKey, NamespacedKey triggerKey) {
        this.engravingKey = engravingKey;
        this.triggerKey = triggerKey;
    }

    public NamespacedKey engravingKey() {
        return engravingKey;
    }

    public NamespacedKey triggerKey() {
        return triggerKey;
    }
}
