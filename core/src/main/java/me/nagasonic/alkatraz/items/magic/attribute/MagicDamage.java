package me.nagasonic.alkatraz.items.magic.attribute;

import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;

public final class MagicDamage {

    private MagicDamage() {
    }

    public static double of(MagicItemInstance instance) {
        if (instance == null) return 0.0;
        return MagicItemRegistries.ITEM_DEFINITIONS.get(instance.definitionKey())
                .map(def -> def.attributes().getOrDefault(MagicKeys.alkatraz("magic_damage"), 0.0))
                .orElse(0.0);
    }
}
